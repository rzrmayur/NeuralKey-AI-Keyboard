package com.supakeyboard.neuralkey.ime.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Immutable
@Serializable
data class ClipboardItem(
    val id: Long,
    val text: String,
    val timestampMs: Long,
    val isPinned: Boolean = false,
    /** File name inside [ClipboardImageStore]; non-null means this entry is an image / screenshot. */
    val imageFile: String? = null,
    val imageMime: String? = null
) {
    val isImage: Boolean get() = imageFile != null
}

/**
 * On-Device Smart Clipboard Time-Machine & AI Context Fusion Manager.
 * Automatically captures non-sensitive clipboard items, supports pinning,
 * and builds AI Context Fusion prompts (e.g. replying to a copied message using current draft notes).
 */
class ClipboardHistoryManager(context: Context) {

    private val appContext = context.applicationContext
    val imageStore: ClipboardImageStore by lazy { ClipboardImageStore(appContext) }
    private val storageFile = File(context.applicationContext.filesDir, "neuralkey_clipboard_history.json")
    private val clipboardManager = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _items = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val items: StateFlow<List<ClipboardItem>> = _items.asStateFlow()

    init {
        loadFromDisk()
        syncFromSystemClipboard()
    }

    fun syncFromSystemClipboard() {
        val cm = clipboardManager ?: return
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return
        val description = clip.description
        if (description != null && isSensitiveClip(description.extras)) {
            return
        }
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && text.length <= MAX_CLIP_LENGTH) {
                addClip(text)
                return
            }
            // A screenshot / image copy (e.g. the "Copy" chip on the screenshot preview) has no text.
            val uri = clip.getItemAt(0)?.uri
            if (uri != null && description != null && looksLikeImage(description, uri)) {
                addImageFromUri(uri)
            }
        }
    }

    private fun looksLikeImage(description: ClipDescription, uri: Uri): Boolean {
        if (description.filterMimeTypes("image/*")?.isNotEmpty() == true) return true
        val type = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        return type?.startsWith("image/") == true
    }

    /**
     * Stores an image (screenshot or copied picture) in the history. Returns false when the content
     * is not a supported image or is too large. The newest copy of an identical image is moved to the top.
     */
    fun addImageFromUri(uri: Uri): Boolean {
        val imported = imageStore.importFromUri(uri) ?: return false
        addImage(imported)
        return true
    }

    @Synchronized
    fun addImage(imported: ImportedImage) {
        val current = _items.value
        val existing = current.firstOrNull { it.imageFile == imported.fileName }
        val now = System.currentTimeMillis()
        val updated = if (existing != null) {
            listOf(existing.copy(timestampMs = now)) + current.filterNot { it.id == existing.id }
        } else {
            val item = ClipboardItem(
                id = now xor imported.fileName.hashCode().toLong(),
                text = "",
                timestampMs = now,
                imageFile = imported.fileName,
                imageMime = imported.mimeType
            )
            listOf(item) + current
        }
        commit(updated)
    }

    /** Saves [updated] and deletes image files that fell out of the history. */
    private fun commit(updated: List<ClipboardItem>) {
        val before = _items.value
        val sorted = sortAndTrim(updated)
        _items.value = sorted
        saveToDisk(sorted)
        val kept = sorted.mapNotNull { it.imageFile }.toSet()
        before.mapNotNull { it.imageFile }.filterNot { it in kept }.forEach { imageStore.delete(it) }
    }

    private fun isSensitiveClip(extras: PersistableBundle?): Boolean {
        if (extras == null) return false
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        return extras.getBoolean(key, false)
    }

    @Synchronized
    fun addClip(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CLIP_LENGTH) return

        val current = _items.value
        val existing = current.firstOrNull { !it.isImage && it.text == trimmed }
        val now = System.currentTimeMillis()

        val updated = if (existing != null) {
            val refreshed = existing.copy(timestampMs = now)
            listOf(refreshed) + current.filterNot { it.id == existing.id }
        } else {
            val newItem = ClipboardItem(
                id = now xor trimmed.hashCode().toLong(),
                text = trimmed,
                timestampMs = now,
                isPinned = false
            )
            listOf(newItem) + current
        }

        commit(updated)
    }

    @Synchronized
    fun togglePin(id: Long) {
        commit(_items.value.map { item ->
            if (item.id == id) item.copy(isPinned = !item.isPinned) else item
        })
    }

    @Synchronized
    fun deleteClip(id: Long) {
        commit(_items.value.filterNot { it.id == id })
    }

    @Synchronized
    fun clearUnpinned() {
        commit(_items.value.filter { it.isPinned })
    }

    fun latestClipText(): String? = _items.value.firstOrNull { !it.isImage }?.text

    fun findItem(id: Long): ClipboardItem? = _items.value.firstOrNull { it.id == id }

    private fun sortAndTrim(list: List<ClipboardItem>): List<ClipboardItem> {
        val pinned = list.filter { it.isPinned }.sortedByDescending { it.timestampMs }
        val unpinned = list.filterNot { it.isPinned }.sortedByDescending { it.timestampMs }
        return (pinned + unpinned).take(MAX_HISTORY_ITEMS)
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        runCatching {
            val loaded = json.decodeFromString<List<ClipboardItem>>(storageFile.readText())
                // Drop image entries whose file was removed (e.g. app data cleared partially).
                .filter { it.imageFile == null || imageStore.exists(it.imageFile) }
            _items.value = sortAndTrim(loaded)
        }
    }

    private fun saveToDisk(list: List<ClipboardItem>) {
        runCatching {
            storageFile.writeText(json.encodeToString(list))
        }
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 25
        private const val MAX_CLIP_LENGTH = 4000

        @Volatile
        private var instance: ClipboardHistoryManager? = null

        fun getInstance(context: Context): ClipboardHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: ClipboardHistoryManager(context.applicationContext).also { instance = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }

        /**
         * Builds a fused AI prompt that replies to [copiedClip] using the user's [currentDraft] intent.
         */
        fun buildContextFusionReplyPayload(copiedClip: String, currentDraft: String): Pair<String, String> {
            val instruction = if (currentDraft.isBlank()) {
                "Write a clear, natural, and helpful reply to the following message that the user copied to their clipboard. Output ONLY the reply text."
            } else {
                "The user copied an incoming message to their clipboard and typed brief reply notes in their text field. " +
                    "Write a complete, polished reply to the copied message that incorporates the user's reply notes. Output ONLY the final reply."
            }
            val combinedInput = if (currentDraft.isBlank()) {
                "COPIED MESSAGE TO REPLY TO:\n$copiedClip"
            } else {
                "COPIED MESSAGE TO REPLY TO:\n$copiedClip\n\nMY REPLY NOTES / INTENT:\n$currentDraft"
            }
            return instruction to combinedInput
        }
    }
}
