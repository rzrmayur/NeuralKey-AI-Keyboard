package com.supakeyboard.neuralkey.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.supakeyboard.neuralkey.ime.mvi.OneHandedMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neuralkey_keyboard_preferences")

class KeyboardPreferences(private val context: Context) {

    private val dataStore = context.dataStore

    val activeModelId: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_MODEL_ID] ?: DEFAULT_ACTIVE_MODEL
    }

    val pinnedModelIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[KEY_PINNED_MODEL_IDS] ?: DEFAULT_PINNED_MODELS
    }

    val fallbackModelIds: Flow<List<String>> = dataStore.data.map { preferences ->
        val serialized = preferences[KEY_FALLBACK_MODELS] ?: ""
        if (serialized.isBlank()) {
            DEFAULT_FALLBACK_MODELS
        } else {
            serialized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    val hapticFeedbackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_HAPTIC_ENABLED] ?: true
    }

    val hapticIntensity: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_HAPTIC_INTENSITY] ?: 70
    }

    val soundFeedbackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_SOUND_ENABLED] ?: false
    }

    val freeModelsOnly: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_FREE_MODELS_ONLY] ?: false
    }

    val suggestionsEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_SUGGESTIONS] ?: true }
    val autoCorrectionEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_CORRECTION] ?: true }
    val keyMemoryEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MEMORY_ENABLED] ?: true }
    val nextWordPredictionEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_NEXT_WORD_PREDICTION] ?: true }
    val splitKeyboardOnFold: Flow<Boolean> = dataStore.data.map { it[KEY_SPLIT_ON_FOLD] ?: true }

    val oneHandedMode: Flow<OneHandedMode> = dataStore.data.map { preferences ->
        val raw = preferences[KEY_ONE_HANDED_MODE] ?: OneHandedMode.OFF.name
        runCatching { OneHandedMode.valueOf(raw) }.getOrDefault(OneHandedMode.OFF)
    }

    suspend fun setSuggestionsEnabled(enabled: Boolean) { dataStore.edit { it[KEY_SUGGESTIONS] = enabled } }
    suspend fun setAutoCorrectionEnabled(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_CORRECTION] = enabled } }
    suspend fun setKeyMemoryEnabled(enabled: Boolean) { dataStore.edit { it[KEY_MEMORY_ENABLED] = enabled } }
    suspend fun setNextWordPredictionEnabled(enabled: Boolean) { dataStore.edit { it[KEY_NEXT_WORD_PREDICTION] = enabled } }
    suspend fun setSplitKeyboardOnFold(enabled: Boolean) { dataStore.edit { it[KEY_SPLIT_ON_FOLD] = enabled } }
    suspend fun setOneHandedMode(mode: OneHandedMode) { dataStore.edit { it[KEY_ONE_HANDED_MODE] = mode.name } }

    val selectedTheme: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_THEME] ?: "SYSTEM_DYNAMIC"
    }

    val customSystemPrompt: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_SYSTEM_PROMPT] ?: ""
    }

    suspend fun setActiveModelId(modelId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_MODEL_ID] = modelId
        }
    }

    suspend fun togglePinModel(modelId: String) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_PINNED_MODEL_IDS] ?: DEFAULT_PINNED_MODELS
            val updated = if (current.contains(modelId)) {
                current - modelId
            } else {
                current + modelId
            }
            preferences[KEY_PINNED_MODEL_IDS] = updated
        }
    }

    suspend fun setPinnedModels(modelIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_PINNED_MODEL_IDS] = modelIds
        }
    }

    suspend fun setFallbackModels(models: List<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_FALLBACK_MODELS] = models.joinToString(",")
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setHapticIntensity(intensity: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_INTENSITY] = intensity.coerceIn(1, 100)
        }
    }

    suspend fun setSoundFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setFreeModelsOnly(freeOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_FREE_MODELS_ONLY] = freeOnly
        }
    }

    suspend fun setSelectedTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME] = theme
        }
    }

    suspend fun setCustomSystemPrompt(prompt: String) {
        dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_SYSTEM_PROMPT] = prompt
        }
    }

    companion object {
        private val KEY_SUGGESTIONS = booleanPreferencesKey("suggestions_enabled")
        private val KEY_AUTO_CORRECTION = booleanPreferencesKey("auto_correction_enabled")
        private val KEY_MEMORY_ENABLED = booleanPreferencesKey("key_memory_enabled")
        private val KEY_NEXT_WORD_PREDICTION = booleanPreferencesKey("next_word_prediction_enabled")
        private val KEY_SPLIT_ON_FOLD = booleanPreferencesKey("split_keyboard_on_fold")
        private val KEY_ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")

        const val DEFAULT_ACTIVE_MODEL = "google/gemini-2.5-flash"
        val DEFAULT_PINNED_MODELS = setOf(
            "google/gemini-2.5-flash",
            "meta-llama/llama-3.3-70b-instruct",
            "deepseek/deepseek-chat",
            "openai/gpt-4o-mini"
        )
        val DEFAULT_FALLBACK_MODELS = listOf(
            "google/gemini-2.5-flash",
            "meta-llama/llama-3.3-70b-instruct",
            "mistralai/mistral-7b-instruct:free"
        )

        private val KEY_ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        private val KEY_PINNED_MODEL_IDS = stringSetPreferencesKey("pinned_model_ids")
        private val KEY_FALLBACK_MODELS = stringPreferencesKey("fallback_models")
        private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val KEY_HAPTIC_INTENSITY = intPreferencesKey("haptic_intensity")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_FREE_MODELS_ONLY = booleanPreferencesKey("free_models_only")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_CUSTOM_SYSTEM_PROMPT = stringPreferencesKey("custom_system_prompt")

        @Volatile
        private var instance: KeyboardPreferences? = null

        fun getInstance(context: Context): KeyboardPreferences {
            return instance ?: synchronized(this) {
                instance ?: KeyboardPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
