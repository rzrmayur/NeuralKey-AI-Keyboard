package com.supakeyboard.neuralkey.data.network

import android.util.Log
import com.supakeyboard.neuralkey.data.network.model.ChatCompletionChunkDto
import com.supakeyboard.neuralkey.data.network.model.ChatCompletionRequest
import com.supakeyboard.neuralkey.data.network.model.InputAudioPayloadDto
import com.supakeyboard.neuralkey.data.network.model.KeyAuthResponse
import com.supakeyboard.neuralkey.data.network.model.MultimodalChatCompletionRequest
import com.supakeyboard.neuralkey.data.network.model.MultimodalChatMessageDto
import com.supakeyboard.neuralkey.data.network.model.MultimodalContentPartDto
import com.supakeyboard.neuralkey.data.network.model.OpenRouterErrorResponseDto
import com.supakeyboard.neuralkey.data.network.model.OpenRouterModelDto
import com.supakeyboard.neuralkey.data.network.model.OpenRouterModelsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class OpenRouterApiException(
    val statusCode: Int,
    val userFriendlyMessage: String,
    val isRetriableWithFreeFallback: Boolean = statusCode == 402 || statusCode == 404 || statusCode == 429 || statusCode in 500..599
) : IllegalStateException(userFriendlyMessage)

class OpenRouterApiClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /**
     * Fetches all available models from OpenRouter catalog.
     */
    suspend fun getModels(): Result<List<OpenRouterModelDto>> = runCatching {
        withRetry {
            val httpResponse = httpClient.get(MODELS_ENDPOINT) {
                header(HEADER_HTTP_REFERER, REFERER_VALUE)
                header(HEADER_X_TITLE, TITLE_VALUE)
            }
            val status = httpResponse.status.value
            if (status !in 200..299) {
                val rawBody = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
                throw parseApiError(status, rawBody)
            }
            val response = httpResponse.body<OpenRouterModelsResponse>()
            response.data
        }
    }

    /**
     * Queries key metadata, balance, and rate limits.
     */
    suspend fun checkKeyAuth(apiKey: String): Result<KeyAuthResponse> = runCatching {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        withRetry {
            val httpResponse = httpClient.get(AUTH_KEY_ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HEADER_HTTP_REFERER, REFERER_VALUE)
                header(HEADER_X_TITLE, TITLE_VALUE)
            }
            val status = httpResponse.status.value
            if (status !in 200..299) {
                val rawBody = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
                throw parseApiError(status, rawBody)
            }
            httpResponse.body<KeyAuthResponse>()
        }
    }

    /**
     * Streams chat completion tokens via Server-Sent Events (SSE) with ultra-low latency.
     */
    fun streamChatCompletion(
        request: ChatCompletionRequest,
        apiKey: String
    ): Flow<String> = flow {
        require(apiKey.isNotBlank()) { "OpenRouter API Key is missing. Please set your API key in Settings." }

        val requestWithStream = request.copy(stream = true)

        httpClient.preparePost(CHAT_COMPLETIONS_ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header(HEADER_HTTP_REFERER, REFERER_VALUE)
            header(HEADER_X_TITLE, TITLE_VALUE)
            contentType(ContentType.Application.Json)
            setBody(requestWithStream)
        }.execute { response ->
            val status = response.status.value
            if (status !in 200..299) {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                throw parseApiError(status, errorBody)
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()

                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    continue
                }

                if (trimmed.startsWith("data: ")) {
                    val payload = trimmed.removePrefix("data: ").trim()
                    if (payload == "[DONE]") {
                        break
                    }

                    try {
                        val chunk = json.decodeFromString<ChatCompletionChunkDto>(payload)
                        if (chunk.error != null) {
                            val errCode = chunk.error.code ?: 500
                            throw parseApiError(errCode, chunk.error.message ?: "Provider stream error")
                        }
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (!content.isNullOrEmpty()) emit(content)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OpenRouterApiException) {
                        throw e
                    } catch (e: kotlinx.serialization.SerializationException) {
                        Log.d(TAG, "Skipping unrecognized SSE frame")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams multimodal audio completion via OpenRouter's `input_audio` specification
     * aligned with audio-capable models (e.g. google/gemini-2.5-flash, google/gemini-2.0-flash-001).
     */
    fun streamMultimodalAudioCompletion(
        base64AudioWav: String,
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
        models: List<String> = DEFAULT_AUDIO_CAPABLE_MODELS
    ): Flow<String> = flow {
        require(apiKey.isNotBlank()) { "OpenRouter API Key is missing. Please set your API key in Settings." }
        require(base64AudioWav.isNotBlank()) { "Audio recording payload is empty." }

        val audioModels = models.ifEmpty { DEFAULT_AUDIO_CAPABLE_MODELS }.take(3)
        val request = MultimodalChatCompletionRequest(
            models = audioModels,
            messages = listOf(
                MultimodalChatMessageDto(
                    role = "system",
                    content = listOf(MultimodalContentPartDto(type = "text", text = systemInstruction))
                ),
                MultimodalChatMessageDto(
                    role = "user",
                    content = listOf(
                        MultimodalContentPartDto(type = "text", text = userPrompt),
                        MultimodalContentPartDto(
                            type = "input_audio",
                            inputAudio = InputAudioPayloadDto(data = base64AudioWav, format = "wav")
                        )
                    )
                )
            ),
            stream = true
        )

        httpClient.preparePost(CHAT_COMPLETIONS_ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header(HEADER_HTTP_REFERER, REFERER_VALUE)
            header(HEADER_X_TITLE, TITLE_VALUE)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.execute { response ->
            val status = response.status.value
            if (status !in 200..299) {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                throw parseApiError(status, errorBody)
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (trimmed.startsWith("data: ")) {
                    val payload = trimmed.removePrefix("data: ").trim()
                    if (payload == "[DONE]") break
                    try {
                        val chunk = json.decodeFromString<ChatCompletionChunkDto>(payload)
                        if (chunk.error != null) {
                            val errCode = chunk.error.code ?: 500
                            throw parseApiError(errCode, chunk.error.message ?: "Audio model error")
                        }
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (!content.isNullOrEmpty()) emit(content)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OpenRouterApiException) {
                        throw e
                    } catch (e: kotlinx.serialization.SerializationException) {
                        Log.d(TAG, "Skipping unrecognized multimodal SSE frame")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    internal fun parseApiError(statusCode: Int, rawBody: String): OpenRouterApiException {
        val parsedDetail = runCatching {
            json.decodeFromString<OpenRouterErrorResponseDto>(rawBody).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: rawBody.takeIf { !it.trim().startsWith("{") }?.take(140)

        val friendlyMessage = when (statusCode) {
            400 -> "Invalid request payload${parsedDetail?.let { ": $it" } ?: "."}"
            401, 403 -> "OpenRouter API key rejected (HTTP $statusCode). Verify your key in NeuralKey Settings."
            402 -> "Selected model requires paid credits (HTTP 402). Switch to a Free ($0) model or top up OpenRouter."
            404 -> "Selected model is unavailable on OpenRouter (HTTP 404). Switching to fallback model."
            408 -> "Model provider timed out (HTTP 408). Please try again."
            429 -> "Rate limit reached on provider (HTTP 429). Retrying with fallback model."
            in 500..599 -> "Model provider is temporarily overloaded (HTTP $statusCode). Trying fallback model."
            else -> parsedDetail ?: "OpenRouter error (HTTP $statusCode). Please try again."
        }
        return OpenRouterApiException(statusCode = statusCode, userFriendlyMessage = friendlyMessage)
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 500,
        maxDelayMs: Long = 8000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: OpenRouterApiException) {
                if (e.statusCode == 401 || e.statusCode == 403 || e.statusCode == 402) {
                    throw e
                }
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }
        throw lastException!!
    }

    companion object {
        private const val TAG = "OpenRouterApiClient"
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        const val MODELS_ENDPOINT = "$BASE_URL/models"
        const val AUTH_KEY_ENDPOINT = "$BASE_URL/auth/key"
        const val CHAT_COMPLETIONS_ENDPOINT = "$BASE_URL/chat/completions"

        val DEFAULT_AUDIO_CAPABLE_MODELS = listOf(
            "google/gemini-2.5-flash",
            "google/gemini-2.0-flash-001",
            "google/gemini-2.0-flash-lite-001"
        )

        private const val HEADER_HTTP_REFERER = "HTTP-Referer"
        private const val HEADER_X_TITLE = "X-Title"
        private const val REFERER_VALUE = "https://github.com/rzrmayur/NeuralKey-AI-Keyboard"
        private const val TITLE_VALUE = "NeuralKey AI"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                expectSuccess = false
                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(60, TimeUnit.SECONDS)
                        writeTimeout(30, TimeUnit.SECONDS)
                        retryOnConnectionFailure(true)
                    }
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true
                        coerceInputValues = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }
        }
    }
}
