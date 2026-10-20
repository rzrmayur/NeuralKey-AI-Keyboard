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
