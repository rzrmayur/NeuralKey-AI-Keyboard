package com.supakeyboard.neuralkey

import android.app.Application
import com.supakeyboard.neuralkey.data.db.NeuralKeyDatabase
import com.supakeyboard.neuralkey.data.network.OpenRouterApiClient
import com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences
import com.supakeyboard.neuralkey.data.repository.OpenRouterRepository
import com.supakeyboard.neuralkey.data.security.SecureKeyStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NeuralKeyApplication : Application() {

    @Volatile
    var suggestionEngine: com.supakeyboard.neuralkey.ime.typing.SuggestionEngine? = null
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { NeuralKeyDatabase.getInstance(this, applicationScope) }
    val keyboardPreferences by lazy { KeyboardPreferences.getInstance(this) }
    val secureKeyStorage by lazy { SecureKeyStorage.getInstance(this) }
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
                val words = java.util.zip.GZIPInputStream(assets.open("english_words.txt.gz")).bufferedReader().use { it.readLines() }
                suggestionEngine = com.supakeyboard.neuralkey.ime.typing.SuggestionEngine(words)
            } catch (e: java.io.IOException) {
                android.util.Log.e("NeuralKey", "Offline dictionary unavailable", e)
            }
        }

        // Trigger background refresh of model catalog if local DB is empty
        applicationScope.launch(Dispatchers.IO) {
            val count = database.modelDao().getModelCount()
            if (count == 0 && secureKeyStorage.hasApiKey()) {
                openRouterRepository.refreshModels()
            }
        }
    }

    companion object {
        lateinit var instance: NeuralKeyApplication
            private set
    }
}
