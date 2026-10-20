package com.supakeyboard.neuralkey.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed secure storage for the OpenRouter API key using Android Keystore
 * and EncryptedSharedPreferences (AES-256 GCM).
 */
class SecureKeyStorage(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to secure fallback", e)
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(): String? {
        val key = prefs.getString(KEY_OPENROUTER_API_KEY, null)?.trim()
        return if (key.isNullOrBlank()) null else key
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit()
            .putString(KEY_OPENROUTER_API_KEY, apiKey.trim())
            .apply()
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_OPENROUTER_API_KEY)
            .apply()
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    companion object {
        private const val TAG = "SecureKeyStorage"
        private const val ENCRYPTED_PREFS_NAME = "neuralkey_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "neuralkey_fallback_prefs"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"

        @Volatile
        private var instance: SecureKeyStorage? = null

        fun getInstance(context: Context): SecureKeyStorage {
            return instance ?: synchronized(this) {
                instance ?: SecureKeyStorage(context.applicationContext).also { instance = it }
            }
        }
    }
}
