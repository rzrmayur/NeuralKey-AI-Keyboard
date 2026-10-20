package com.supakeyboard.neuralkey.data.repository

import android.util.Log
import com.supakeyboard.neuralkey.data.db.dao.ModelDao
import com.supakeyboard.neuralkey.data.db.dao.UserPromptDao
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import com.supakeyboard.neuralkey.data.network.OpenRouterApiClient
import com.supakeyboard.neuralkey.data.network.OpenRouterApiException
import com.supakeyboard.neuralkey.data.network.SystemPromptBuilder
import com.supakeyboard.neuralkey.data.network.model.ChatCompletionRequest
import com.supakeyboard.neuralkey.data.network.model.ChatMessageDto
import com.supakeyboard.neuralkey.data.network.model.KeyAuthResponse
import com.supakeyboard.neuralkey.data.network.model.ProviderRoutingDto
import com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences
import com.supakeyboard.neuralkey.data.security.SecureKeyStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class OpenRouterRepository(
    private val apiClient: OpenRouterApiClient,
    private val modelDao: ModelDao,
    private val userPromptDao: UserPromptDao,
    private val preferences: KeyboardPreferences,
    private val secureKeyStorage: SecureKeyStorage
) {

    val allModels: Flow<List<OpenRouterModelEntity>> = modelDao.getAllModels()
    val freeModels: Flow<List<OpenRouterModelEntity>> = modelDao.getFreeModels()
    val pinnedModels: Flow<List<OpenRouterModelEntity>> = modelDao.getPinnedModels()
    val userPrompts: Flow<List<UserPromptEntity>> = userPromptDao.getAllPrompts()

    /**
     * Emits the currently active model entity according to user preferences.
     */
    val activeModel: Flow<OpenRouterModelEntity?> = preferences.activeModelId.flatMapLatest { modelId ->
        modelDao.getModelById(modelId)
    }

    /**
     * Refreshes the local model catalog from OpenRouter's live API and stores it in Room.
     */
    suspend fun refreshModels(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val dtoList = apiClient.getModels().getOrThrow()
            val pinnedIds = preferences.pinnedModelIds.first()

            val entities = dtoList.map { dto ->
                OpenRouterModelEntity.fromDto(
                    dto = dto,
                    isPinned = pinnedIds.contains(dto.id)
                )
            }

            modelDao.replaceModelsPreservingPins(entities)
            Log.d(TAG, "Successfully refreshed and cached ${entities.size} OpenRouter models")
            entities.size
        }
    }

    suspend fun refreshModelsIfStale(): Result<Int> = withContext(Dispatchers.IO) {
        val latestTimestamp = modelDao.getModelAgeMs()
        val ageMs = System.currentTimeMillis() - latestTimestamp
        if (ageMs < MODEL_CACHE_TTL_MS && latestTimestamp > 0L) {
            val count = modelDao.getModelCount()
            Result.success(count)
        } else {
            refreshModels()
        }
    }

    fun searchModels(query: String, freeOnly: Boolean = false): Flow<List<OpenRouterModelEntity>> {
        val trimmed = query.trim()
        return if (freeOnly) {
            if (trimmed.isEmpty()) modelDao.getFreeModels() else modelDao.searchFreeModels(trimmed)
        } else {
            if (trimmed.isEmpty()) modelDao.getAllModels() else modelDao.searchModels(trimmed)
        }
    }

    suspend fun checkApiKeyUsage(): Result<KeyAuthResponse> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getApiKey()
            ?: return@withContext Result.failure(
                IllegalStateException("No OpenRouter API key found. Paste your free key in NeuralKey Settings → Setup.")
            )
        apiClient.checkKeyAuth(apiKey)
    }

    /**
     * Streams an AI completion for the user's text draft with strict system prompting,
     * target app persona awareness, provider latency routing, and automatic free-tier failover.
     */
    fun streamAIAction(
        promptInstruction: String,
        draftText: String,
        targetPackageName: String? = null,
        customPromptOverride: String? = null
    ): Flow<String> = flow {
        var emittedAnyToken = false
        try {
            val apiKey = secureKeyStorage.getApiKey()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("OpenRouter API key is missing. Open NeuralKey Settings → Setup to paste your free key.")

            require(draftText.isNotBlank()) { "No text available to process. Please type or paste text first." }

            val activeModelId = preferences.activeModelId.first()
            val configuredFallbacks = preferences.fallbackModelIds.first()
            val globalDirective = preferences.customSystemPrompt.first().takeIf { it.isNotBlank() }

            val modelHierarchy = (listOf(activeModelId) + configuredFallbacks).distinct().take(3)

            val systemPrompt = SystemPromptBuilder.buildSystemPrompt(
                actionInstruction = promptInstruction,
                targetPackageName = targetPackageName,
                customPromptOverride = customPromptOverride ?: globalDirective
            )

            val chatRequest = ChatCompletionRequest(
                models = modelHierarchy,
                messages = listOf(
                    ChatMessageDto(role = "system", content = systemPrompt),
                    ChatMessageDto(role = "user", content = draftText.trim())
                ),
                stream = true,
                provider = ProviderRoutingDto(sort = "latency", allowFallbacks = true),
                temperature = 0.5
            )

            try {
                apiClient.streamChatCompletion(chatRequest, apiKey).collect { token ->
                    emittedAnyToken = true
                    emit(token)
                }
            } catch (apiEx: OpenRouterApiException) {
                if (!emittedAnyToken && apiEx.isRetriableWithFreeFallback) {
                    Log.w(TAG, "Primary model failed (${apiEx.statusCode}); retrying with guaranteed free fallback chain")
                    val emergencyFallbackRequest = chatRequest.copy(
                        models = EMERGENCY_FREE_FALLBACK_MODELS
                    )
                    apiClient.streamChatCompletion(emergencyFallbackRequest, apiKey).collect { token ->
                        emittedAnyToken = true
                        emit(token)
                    }
                } else {
                    throw apiEx
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenRouterApiException) {
            Log.e(TAG, "AI stream failed with OpenRouterApiException (${e.statusCode})", e)
            throw IllegalStateException(e.userFriendlyMessage, e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AI stream failed: configuration error", e)
            throw e
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "AI stream failed: invalid input", e)
            throw e
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "AI stream failed: DNS/offline", e)
            throw IllegalStateException("No internet connection. Offline Key Memory, Glide Typing & /calc macros remain active.", e)
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "AI stream failed: network unavailable", e)
            throw IllegalStateException("Network connection failed. Check your internet and try again.", e)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "AI stream failed: timeout", e)
            throw IllegalStateException("AI provider timed out. Please try again or switch to a faster model.", e)
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed: unexpected error", e)
            throw IllegalStateException(e.message ?: "AI request failed. Please try again.", e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams a specific model directly for the Multi-Model Arena Split-Compare.
     */
    fun streamModelDirect(
        modelId: String,
        promptInstruction: String,
        draftText: String,
        targetPackageName: String? = null
    ): Flow<String> = flow {
        try {
            val apiKey = secureKeyStorage.getApiKey()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("OpenRouter API key is missing. Configure your key in Settings.")
            require(draftText.isNotBlank()) { "Please enter text before launching Model Arena." }

            val globalDirective = preferences.customSystemPrompt.first().takeIf { it.isNotBlank() }
            val systemPrompt = SystemPromptBuilder.buildSystemPrompt(
                actionInstruction = promptInstruction,
                targetPackageName = targetPackageName,
                customPromptOverride = globalDirective
            )

            val request = ChatCompletionRequest(
                models = (listOf(modelId) + EMERGENCY_FREE_FALLBACK_MODELS).distinct().take(3),
                messages = listOf(
                    ChatMessageDto(role = "system", content = systemPrompt),
                    ChatMessageDto(role = "user", content = draftText.trim())
                ),
                stream = true,
                provider = ProviderRoutingDto(sort = "latency", allowFallbacks = true),
                temperature = 0.55
            )
            apiClient.streamChatCompletion(request, apiKey).collect { token ->
                emit(token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenRouterApiException) {
            throw IllegalStateException(e.userFriendlyMessage, e)
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException("Offline: Check internet connection.", e)
        } catch (e: Exception) {
            throw IllegalStateException(e.message ?: "Model stream failed.", e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Transcribes recorded 16kHz WAV audio into clean, non-repetitive text using OpenRouter's
     * voice/STT models (`elevenlabs/scribe-v2`, `mistralai/voxtral-mini-transcribe`, `openai/whisper-large-v3-turbo`,
     * or multimodal Audio LLMs like `google/gemini-2.5-flash`, `mistralai/voxtral-small-24b-2507`, and `:free` audio models).
     */
    suspend fun transcribeVoiceAudioToText(
        base64AudioWav: String,
        preferredVoiceModelId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = secureKeyStorage.getApiKey()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("OpenRouter API key is missing. Paste your free key in Settings → Setup.")
            val chosenModelId = preferredVoiceModelId ?: preferences.selectedVoiceModelId.first()

            val rawTranscript = if (com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine.isDedicatedTranscriptionModel(chosenModelId)) {
                val sttResult = apiClient.transcribeAudio(base64AudioWav, chosenModelId, apiKey)
                if (sttResult.isSuccess) {
                    sttResult.getOrThrow()
                } else {
                    // Fallback to multimodal audio completion across audio-capable models (including free audio models)
                    collectMultimodalTranscription(base64AudioWav, apiKey, OpenRouterApiClient.DEFAULT_AUDIO_CAPABLE_MODELS)
                }
            } else {
                val candidateModels = (listOf(chosenModelId) + OpenRouterApiClient.DEFAULT_AUDIO_CAPABLE_MODELS).distinct()
                try {
                    collectMultimodalTranscription(base64AudioWav, apiKey, candidateModels.take(3))
                } catch (e: OpenRouterApiException) {
                    if (e.isRetriableWithFreeFallback) {
                        collectMultimodalTranscription(base64AudioWav, apiKey, OpenRouterApiClient.FREE_AUDIO_FALLBACK_MODELS)
                    } else {
                        throw e
                    }
                }
            }

            val cleaned = com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine.cleanRepetitiveSpeech(rawTranscript)
            if (cleaned.isEmpty()) {
                throw IllegalStateException("No speech detected in recording. Please speak clearly and try again.")
            }
            cleaned
        }
    }

    private suspend fun collectMultimodalTranscription(
        base64AudioWav: String,
        apiKey: String,
        models: List<String>
    ): String {
        val sb = StringBuilder()
        apiClient.streamMultimodalAudioCompletion(
            base64AudioWav = base64AudioWav,
            systemInstruction = "You are a precision speech-to-text transcription engine. Transcribe the user's spoken audio accurately without repeating words or adding commentary. Output ONLY the transcribed words.",
            userPrompt = "Transcribe this audio recording verbatim and cleanly.",
            apiKey = apiKey,
            models = models
        ).collect { token ->
            sb.append(token)
        }
        return sb.toString().trim()
    }

    /**
     * Streams multimodal voice audio directly to OpenRouter audio-capable models
     * (`elevenlabs/scribe-v2`, `mistralai/voxtral-mini-transcribe`, `mistralai/voxtral-small-24b-2507`,
     * `google/gemini-2.5-flash`, `openai/gpt-audio-mini`, `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`, etc.).
     */
    fun streamVoiceAudioAction(
        base64AudioWav: String,
        voiceInstruction: String,
        targetPackageName: String? = null,
        preferredVoiceModelId: String? = null
    ): Flow<String> = flow {
        try {
            val apiKey = secureKeyStorage.getApiKey()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("OpenRouter API key is missing. Open NeuralKey Settings → Setup to paste your free key.")

            val chosenModelId = preferredVoiceModelId ?: preferences.selectedVoiceModelId.first()

            if (com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine.isDedicatedTranscriptionModel(chosenModelId) ||
                voiceInstruction.isBlank()
            ) {
                val transcript = transcribeVoiceAudioToText(base64AudioWav, chosenModelId).getOrThrow()
                if (voiceInstruction.isBlank()) {
                    emit(transcript)
                } else {
                    streamAIAction(
                        promptInstruction = voiceInstruction,
                        draftText = transcript,
                        targetPackageName = targetPackageName
                    ).collect { token ->
                        emit(token)
                    }
                }
                return@flow
            }

            val audioModels = buildList {
                if (isAudioCapableModel(chosenModelId)) add(chosenModelId)
                addAll(OpenRouterApiClient.DEFAULT_AUDIO_CAPABLE_MODELS)
            }.distinct().take(3)

            val systemPrompt = SystemPromptBuilder.buildSystemPrompt(
                actionInstruction = voiceInstruction,
                targetPackageName = targetPackageName
            )

            var emittedAny = false
            try {
                apiClient.streamMultimodalAudioCompletion(
                    base64AudioWav = base64AudioWav,
                    systemInstruction = systemPrompt,
                    userPrompt = "$voiceInstruction Do NOT repeat words or stutter.",
                    apiKey = apiKey,
                    models = audioModels
                ).collect { token ->
                    emittedAny = true
                    emit(token)
                }
            } catch (e: OpenRouterApiException) {
                if (!emittedAny && e.isRetriableWithFreeFallback) {
                    apiClient.streamMultimodalAudioCompletion(
                        base64AudioWav = base64AudioWav,
                        systemInstruction = systemPrompt,
                        userPrompt = "$voiceInstruction Do NOT repeat words or stutter.",
                        apiKey = apiKey,
                        models = OpenRouterApiClient.FREE_AUDIO_FALLBACK_MODELS
                    ).collect { token ->
                        emit(token)
                    }
                } else {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenRouterApiException) {
            throw IllegalStateException(e.userFriendlyMessage, e)
        } catch (e: Exception) {
            throw IllegalStateException(e.message ?: "Voice AI processing failed.", e)
        }
    }.flowOn(Dispatchers.IO)

    fun isAudioCapableModel(modelId: String): Boolean {
        return com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine.isVoiceCapableModelId(modelId)
    }

    suspend fun setActiveModel(modelId: String) {
        preferences.setActiveModelId(modelId)
    }

    suspend fun togglePinModel(modelId: String) {
        val current = preferences.pinnedModelIds.first()
        val willPin = !current.contains(modelId)
        preferences.togglePinModel(modelId)
        modelDao.updatePinned(modelId, willPin)
    }

    suspend fun insertUserPrompt(
        title: String,
        instruction: String,
        iconKey: String = "auto_fix_high",
        category: String = "Productivity"
    ) {
        val count = userPromptDao.getPromptCount()
        val prompt = UserPromptEntity(
            title = title,
            promptInstruction = instruction,
            iconKey = iconKey,
            category = category,
            orderIndex = count,
            isBuiltIn = false
        )
        userPromptDao.insertPrompt(prompt)
    }

    suspend fun deleteUserPrompt(id: Long) {
        userPromptDao.deletePromptById(id)
    }

    companion object {
        private const val TAG = "OpenRouterRepository"
        private const val MODEL_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

        val EMERGENCY_FREE_FALLBACK_MODELS = listOf(
            "google/gemini-2.5-flash",
            "mistralai/mistral-7b-instruct:free",
            "meta-llama/llama-3.3-70b-instruct"
        )

        @Volatile
        private var instance: OpenRouterRepository? = null

        fun getInstance(
            apiClient: OpenRouterApiClient,
            modelDao: ModelDao,
            userPromptDao: UserPromptDao,
            preferences: KeyboardPreferences,
            secureKeyStorage: SecureKeyStorage
        ): OpenRouterRepository {
            return instance ?: synchronized(this) {
                instance ?: OpenRouterRepository(
                    apiClient,
                    modelDao,
                    userPromptDao,
                    preferences,
                    secureKeyStorage
                ).also { instance = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
