package com.supakeyboard.neuralkey.data.repository

import android.util.Log
import com.supakeyboard.neuralkey.data.db.dao.ModelDao
import com.supakeyboard.neuralkey.data.db.dao.UserPromptDao
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import com.supakeyboard.neuralkey.data.network.OpenRouterApiClient
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
     * Preserves pinned states and computes pricing and free flags.
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

    /**
     * Refreshes the model catalog only if the local cache is older than [MODEL_CACHE_TTL_MS].
     * Returns the current model count on a cache hit, or the refreshed count on a miss.
     */
    suspend fun refreshModelsIfStale(): Result<Int> = withContext(Dispatchers.IO) {
        val latestTimestamp = modelDao.getModelAgeMs()
        val ageMs = System.currentTimeMillis() - latestTimestamp
        if (ageMs < MODEL_CACHE_TTL_MS && latestTimestamp > 0L) {
            Log.d(TAG, "Model cache is fresh (age=${ageMs}ms), skipping refresh")
            val count = modelDao.getModelCount()
            Result.success(count)
        } else {
            Log.d(TAG, "Model cache is stale (age=${ageMs}ms), refreshing from API")
            refreshModels()
        }
    }

    /**
     * Search models using query and optional free-only filter.
     */
    fun searchModels(query: String, freeOnly: Boolean = false): Flow<List<OpenRouterModelEntity>> {
        val trimmed = query.trim()
        return if (freeOnly) {
            if (trimmed.isEmpty()) modelDao.getFreeModels() else modelDao.searchFreeModels(trimmed)
        } else {
            if (trimmed.isEmpty()) modelDao.getAllModels() else modelDao.searchModels(trimmed)
        }
    }

    /**
     * Queries key balance, rate limits, and verification from OpenRouter.
     */
    suspend fun checkApiKeyUsage(): Result<KeyAuthResponse> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getApiKey()
            ?: return@withContext Result.failure(IllegalStateException("No OpenRouter API key found in secure storage"))
        apiClient.checkKeyAuth(apiKey)
    }

    /**
     * Streams an AI completion for the user's text draft with strict system prompting,
     * target app context awareness, provider latency routing, and fallback models.
     */
    fun streamAIAction(
        promptInstruction: String,
        draftText: String,
        targetPackageName: String? = null,
        customPromptOverride: String? = null
    ): Flow<String> = flow {
        try {
            val apiKey = secureKeyStorage.getApiKey()
                ?: throw IllegalStateException("OpenRouter API key is missing. Tap the Settings icon to configure your key.")

            require(draftText.isNotBlank()) { "No text available to process. Please type or select text first." }

            val activeModelId = preferences.activeModelId.first()
            val configuredFallbacks = preferences.fallbackModelIds.first()
            val globalDirective = preferences.customSystemPrompt.first().takeIf { it.isNotBlank() }

            // Combine primary model with fallbacks (ensuring unique order)
            // OpenRouter API limits 'models' array to 3 items max
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

            apiClient.streamChatCompletion(chatRequest, apiKey).collect { token ->
                emit(token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AI stream failed: missing configuration", e)
            throw e
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "AI stream failed: invalid input", e)
            throw e
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "AI stream failed: network unavailable", e)
            throw IllegalStateException("Network connection failed. Check your internet and try again.", e)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "AI stream failed: timeout", e)
            throw IllegalStateException("Request timed out. Please try again.", e)
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed: unexpected error", e)
            throw IllegalStateException("AI request failed. Please try again.", e)
        }
    }.flowOn(Dispatchers.IO)

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
    }
}
