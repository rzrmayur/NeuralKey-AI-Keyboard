package com.supakeyboard.neuralkey.data.network

import android.util.Log
import com.supakeyboard.neuralkey.data.network.model.ChatCompletionChunkDto
import com.supakeyboard.neuralkey.data.network.model.ChatCompletionRequest
import com.supakeyboard.neuralkey.data.network.model.KeyAuthResponse
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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

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
            val response = httpClient.get(MODELS_ENDPOINT) {
                header(HEADER_HTTP_REFERER, REFERER_VALUE)
                header(HEADER_X_TITLE, TITLE_VALUE)
            }.body<OpenRouterModelsResponse>()
            response.data
        }
    }

    /**
     * Queries key metadata, balance, and rate limits.
     */
    suspend fun checkKeyAuth(apiKey: String): Result<KeyAuthResponse> = runCatching {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        withRetry {
            httpClient.get(AUTH_KEY_ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HEADER_HTTP_REFERER, REFERER_VALUE)
                header(HEADER_X_TITLE, TITLE_VALUE)
            }.body<KeyAuthResponse>()
        }
    }

    /**
     * Streams chat completion tokens via Server-Sent Events (SSE) with ultra-low latency.
     * Emits tokens as they arrive from the inference provider.
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
                val errorBody = response.body<String>()
                throw IllegalStateException("OpenRouter API returned error $status: $errorBody")
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()

                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    // Ignore empty lines and SSE ping comments
                    continue
                }

                if (trimmed.startsWith("data: ")) {
                    val payload = trimmed.removePrefix("data: ").trim()
                    if (payload == "[DONE]") {
                        break
                    }

                    try {
                        val chunk = json.decodeFromString<ChatCompletionChunkDto>(payload)
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (!content.isNullOrEmpty()) {
                            emit(content)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Skipping non-chunk SSE frame: $payload")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Retries a suspend block with exponential backoff.
     * Does not retry on SSE streams — only for discrete HTTP calls.
     */
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

        private const val HEADER_HTTP_REFERER = "HTTP-Referer"
        private const val HEADER_X_TITLE = "X-Title"
        private const val REFERER_VALUE = "https://github.com/SupaKeyboard/NeuralKey"
        private const val TITLE_VALUE = "NeuralKey AI"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                expectSuccess = true
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
