package com.supakeyboard.neuralkey.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** An image that has been copied into NeuralKey's private storage. */
data class ImportedImage(val fileName: String, val mimeType: String)

enum class ImagePasteResult {
    /** The host app accepted the image directly (like Gboard's image paste). */
    INSERTED,

    /** The field cannot take images, so the image was placed on the system clipboard instead. */
    COPIED_TO_CLIPBOARD,
    FAILED
}

/**
 * Private, on-device storage for clipboard images and screenshots.
 *
 * Images never leave the device: they are copied into the app's own `filesDir`, validated as real
 * bitmaps, size-capped, and only shared with another app when the user taps Paste.
 */
class ClipboardImageStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, IMAGE_DIR).apply { mkdirs() }

    /** Resolves a stored image name; any path components are stripped so tampered data cannot escape [dir]. */
    fun file(fileName: String): File = File(dir, File(fileName).name)

    fun exists(fileName: String): Boolean = file(fileName).isFile

    fun delete(fileName: String) {
        runCatching { file(fileName).delete() }
    }

    /**
     * Copies the image behind [uri] into private storage. Returns null when the content is not a
     * supported image (PNG, JPEG, GIF or WebP), is empty, unreadable or larger than [MAX_IMAGE_BYTES].
     * Identical images are de-duplicated by content hash.
     */
    fun importFromUri(uri: Uri): ImportedImage? {
        val bytes = try {
            appContext.contentResolver.openInputStream(uri)?.use { readBounded(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        return importBytes(bytes)
    }

    fun importBytes(bytes: ByteArray): ImportedImage? {
        val mime = sniffMimeType(bytes) ?: return null
        // A corrupt or hostile image on the clipboard must never be able to crash the keyboard.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        } catch (_: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val name = sha256Hex(bytes).take(HASH_CHARS) + "." + extensionFor(mime)
        val target = file(name)
        if (!target.isFile || target.length() != bytes.size.toLong()) {
            try {
                target.writeBytes(bytes)
            } catch (_: Exception) {
                return null
            }
        }
        return ImportedImage(name, mime)
    }

    /** Decodes a down-sampled preview so the keyboard never holds full-resolution screenshots in memory. */
    fun thumbnail(fileName: String, maxPx: Int): Bitmap? {
        val source = file(fileName)
        if (!source.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun shareUri(fileName: String): Uri? {
        val source = file(fileName)
        if (!source.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", source)
        }.getOrNull()
    }

    /**
     * Inserts the image straight into the focused field via `InputConnectionCompat.commitContent`
     * (the same mechanism Gboard uses). When the field cannot take images, the image is placed on the
     * system clipboard so the user can paste it with the host app's own Paste action.
     * Must be called on the main thread.
     */
    fun commitToEditor(
        fileName: String,
        mimeType: String,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?
    ): ImagePasteResult {
        if (inputConnection == null || editorInfo == null) return ImagePasteResult.FAILED
        val uri = shareUri(fileName) ?: return ImagePasteResult.FAILED

        if (acceptsMimeType(editorInfo, mimeType)) {
            val committed = runCatching {
                val description = ClipDescription("image", arrayOf(mimeType))
                InputConnectionCompat.commitContent(
                    inputConnection,
                    editorInfo,
                    InputContentInfoCompat(uri, description, null),
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                    null
                )
            }.getOrDefault(false)
            if (committed) return ImagePasteResult.INSERTED
        }

        val copied = runCatching {
            val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            manager?.setPrimaryClip(ClipData.newUri(appContext.contentResolver, "image", uri)) != null
        }.getOrDefault(false)
        return if (copied) ImagePasteResult.COPIED_TO_CLIPBOARD else ImagePasteResult.FAILED
    }

    companion object {
        const val IMAGE_DIR = "clipboard_images"
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        private const val HASH_CHARS = 24

        /** True when the focused field declares that it can receive image content. */
        fun acceptsImages(editorInfo: EditorInfo?): Boolean {
            if (editorInfo == null) return false
            return runCatching {
                EditorInfoCompat.getContentMimeTypes(editorInfo).any { it.startsWith("image/") || it == "*/*" }
            }.getOrDefault(false)
        }

        fun acceptsMimeType(editorInfo: EditorInfo, mimeType: String): Boolean =
            runCatching {
                EditorInfoCompat.getContentMimeTypes(editorInfo).any { declared ->
                    declared == "*/*" || declared == "image/*" || declared.equals(mimeType, ignoreCase = true)
                }
            }.getOrDefault(false)

        /** Identifies the image format from its magic bytes; null means "not an image we support". */
        fun sniffMimeType(bytes: ByteArray): String? {
            fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
            return when {
                at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
                at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
                at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38 -> "image/gif"
                at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                    at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "image/webp"
                else -> null
            }
        }

        fun extensionFor(mimeType: String): String = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "png"
        }

        private fun readBounded(input: InputStream): ByteArray? {
            val out = ByteArrayOutputStream(64 * 1024)
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMAGE_BYTES) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray().takeIf { it.isNotEmpty() }
        }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
