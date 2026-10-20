package com.supakeyboard.neuralkey.ime.typing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class KeyMemorySnapshot(
    val words: Map<String, Int> = emptyMap(),
    val bigrams: Map<String, Map<String, Int>> = emptyMap(),
    val customCasing: Map<String, String> = emptyMap()
)

/**
 * Privacy-first, 100% on-device persistence for user-typed vocabulary (Key Memory)
 * and contextual next-word bigrams. Never synced to the cloud and never records
 * sensitive/password/URI/email fields.
 */
class KeyMemoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _learnedWordCount = MutableStateFlow(0)
    val learnedWordCount: StateFlow<Int> = _learnedWordCount.asStateFlow()

    private val _topLearnedWords = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val topLearnedWords: StateFlow<List<Pair<String, Int>>> = _topLearnedWords.asStateFlow()

    fun loadSnapshot(): KeyMemorySnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, null) ?: return KeyMemorySnapshot()
        return try {
            json.decodeFromString<KeyMemorySnapshot>(raw).also { updateFlows(it) }
        } catch (_: Exception) {
            KeyMemorySnapshot()
        }
    }

    fun saveSnapshot(snapshot: KeyMemorySnapshot) {
        try {
            val trimmedWords = snapshot.words.entries
                .sortedByDescending { it.value }
                .take(MAX_STORED_WORDS)
                .associate { it.key to it.value }
            val trimmedBigrams = snapshot.bigrams.entries
                .take(MAX_STORED_BIGRAM_KEYS)
                .associate { (k, v) ->
                    k to v.entries.sortedByDescending { it.value }.take(8).associate { it.key to it.value }
                }
            val trimmedCasing = snapshot.customCasing.filterKeys { it in trimmedWords }
            val bounded = KeyMemorySnapshot(trimmedWords, trimmedBigrams, trimmedCasing)
            prefs.edit().putString(KEY_SNAPSHOT_JSON, json.encodeToString(bounded)).apply()
            updateFlows(bounded)
        } catch (_: Exception) {
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _learnedWordCount.value = 0
        _topLearnedWords.value = emptyList()
    }

    private fun updateFlows(snapshot: KeyMemorySnapshot) {
        _learnedWordCount.value = snapshot.words.size
        _topLearnedWords.value = snapshot.words.entries
            .sortedByDescending { it.value }
            .take(25)
            .map { (word, count) -> (snapshot.customCasing[word] ?: word) to count }
    }

    companion object {
        private const val PREFS_NAME = "neuralkey_key_memory_store"
        private const val KEY_SNAPSHOT_JSON = "key_memory_snapshot_v1"
        private const val MAX_STORED_WORDS = 3000
        private const val MAX_STORED_BIGRAM_KEYS = 1500

        @Volatile
        private var instance: KeyMemoryStore? = null

        fun getInstance(context: Context): KeyMemoryStore {
            return instance ?: synchronized(this) {
                instance ?: KeyMemoryStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
