package com.supakeyboard.neuralkey

import android.app.Application
import com.supakeyboard.neuralkey.data.db.NeuralKeyDatabase
import com.supakeyboard.neuralkey.data.network.OpenRouterApiClient
import com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences
import com.supakeyboard.neuralkey.data.repository.OpenRouterRepository
import com.supakeyboard.neuralkey.data.security.SecureKeyStorage
import com.supakeyboard.neuralkey.ime.typing.KeyMemoryStore
import com.supakeyboard.neuralkey.ime.typing.SuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NeuralKeyApplication : Application() {

    @Volatile
    var suggestionEngine: SuggestionEngine? = null
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { NeuralKeyDatabase.getInstance(this, applicationScope) }
    val keyboardPreferences by lazy { KeyboardPreferences.getInstance(this) }
    val secureKeyStorage by lazy { SecureKeyStorage.getInstance(this) }
    val keyMemoryStore by lazy { KeyMemoryStore.getInstance(this) }
    val openRouterApiClient by lazy { OpenRouterApiClient() }

    val openRouterRepository by lazy {
        OpenRouterRepository.getInstance(
            apiClient = openRouterApiClient,
            modelDao = database.modelDao(),
            userPromptDao = database.userPromptDao(),
            preferences = keyboardPreferences,
            secureKeyStorage = secureKeyStorage
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        applicationScope.launch(Dispatchers.IO) {
            try {
                val words = java.util.zip.GZIPInputStream(assets.open("english_words.dict")).bufferedReader().use { it.readLines() }
                val engine = SuggestionEngine(words)
                engine.restoreMemory(keyMemoryStore.loadSnapshot())
                engine.onMemoryUpdated = { snapshot ->
                    keyMemoryStore.saveSnapshot(snapshot)
                }
                suggestionEngine = engine
            } catch (e: java.io.IOException) {
                android.util.Log.e("NeuralKey", "Offline dictionary unavailable", e)
            }
        }

        // Trigger background refresh of model catalog if local DB is empty
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (secureKeyStorage.hasApiKey() && database.modelDao().getModelCount() == 0) {
                    openRouterRepository.refreshModels()
                }
            } catch (e: Exception) {
                android.util.Log.w("NeuralKey", "Deferred background model catalog check", e)
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        OpenRouterRepository.resetInstance()
        NeuralKeyDatabase.resetInstance()
        super.onTerminate()
    }

    fun clearKeyMemory() {
        keyMemoryStore.clearAll()
        suggestionEngine?.clearMemory()
    }

    fun teachCustomWord(word: String) {
        val trimmed = word.trim()
        if (SuggestionEngine.isWord(trimmed)) {
            suggestionEngine?.recordWord(trimmed)
        }
    }

    companion object {
        lateinit var instance: NeuralKeyApplication
            private set
    }
}
