package com.supakeyboard.neuralkey.ime.clipboard

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ClipboardImageTest {

    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, ClipboardImageStore.IMAGE_DIR).deleteRecursively()
        File(app.filesDir, "neuralkey_clipboard_history.json").delete()
    }

    private fun png(color: Int = Color.RED, size: Int = 8): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun detectsImageFormatsByContentNotByName() {
        assertEquals("image/png", ClipboardImageStore.sniffMimeType(png()))
        assertEquals("image/jpeg", ClipboardImageStore.sniffMimeType(byteArrayOf(-1, -40, -1, -32, 0, 0)))
        assertEquals("image/gif", ClipboardImageStore.sniffMimeType("GIF89a....".toByteArray()))
        assertEquals(
            "image/webp",
            ClipboardImageStore.sniffMimeType("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1))
        )
        assertNull(ClipboardImageStore.sniffMimeType("<html>not an image</html>".toByteArray()))
        assertNull(ClipboardImageStore.sniffMimeType(ByteArray(0)))
    }

    @Test fun importsRealImagesAndRejectsEverythingElse() {
        val store = ClipboardImageStore(app)
        val imported = store.importBytes(png())
        assertNotNull(imported)
        assertEquals("image/png", imported!!.mimeType)
        assertTrue(imported.fileName.endsWith(".png"))
        assertTrue(store.exists(imported.fileName))

        assertNull(store.importBytes("hello world".toByteArray()))
        // Valid PNG signature but no decodable image must not be stored.
        assertNull(store.importBytes(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
    }

    @Test fun identicalImagesShareOneFileAndDifferentOnesDoNot() {
        val store = ClipboardImageStore(app)
        val a = store.importBytes(png(Color.RED))!!
        val b = store.importBytes(png(Color.RED))!!
        val c = store.importBytes(png(Color.BLUE))!!
        assertEquals(a.fileName, b.fileName)
        assertNotEquals(a.fileName, c.fileName)
    }

    @Test fun storedNamesCannotEscapeTheImageDirectory() {
        val store = ClipboardImageStore(app)
        val resolved = store.file("../../databases/secret.db")
        assertEquals("secret.db", resolved.name)
        assertEquals(File(app.filesDir, ClipboardImageStore.IMAGE_DIR).canonicalPath, resolved.parentFile!!.canonicalPath)
    }

    @Test fun thumbnailsAreDownsampledAndMissingFilesAreSafe() {
        val store = ClipboardImageStore(app)
        val imported = store.importBytes(png(size = 512))!!
        val thumb = store.thumbnail(imported.fileName, 64)
        assertNotNull(thumb)
        assertTrue(thumb!!.width <= 256)
        assertNull(store.thumbnail("missing.png", 64))
    }

    @Test fun historyKeepsImagesSeparateFromTextAndCleansUpFiles() {
        val manager = ClipboardHistoryManager(app)
        manager.addClip("hello")
        val image = manager.imageStore.importBytes(png())!!
        manager.addImage(image)
        manager.addImage(image) // duplicate refreshes instead of adding

        val items = manager.items.value
        assertEquals(2, items.size)
        assertEquals(1, items.count { it.isImage })
        assertEquals("hello", manager.latestClipText())

        val imageItem = items.first { it.isImage }
        manager.deleteClip(imageItem.id)
        assertFalse(manager.imageStore.exists(image.fileName))
        assertEquals(1, manager.items.value.size)
    }

    @Test fun oldHistoryWithoutImageFieldsStillLoads() {
        File(app.filesDir, "neuralkey_clipboard_history.json")
            .writeText("""[{"id":1,"text":"legacy clip","timestampMs":1000,"isPinned":true}]""")
        val manager = ClipboardHistoryManager(app)
        val item = manager.items.value.single()
        assertEquals("legacy clip", item.text)
        assertTrue(item.isPinned)
        assertFalse(item.isImage)
    }

    @Test fun imageEntriesWhoseFileVanishedAreDroppedOnLoad() {
        File(app.filesDir, "neuralkey_clipboard_history.json").writeText(
            """[{"id":2,"text":"","timestampMs":1,"imageFile":"gone.png","imageMime":"image/png"},""" +
                """{"id":3,"text":"kept","timestampMs":2}]"""
        )
        val manager = ClipboardHistoryManager(app)
        assertEquals(listOf("kept"), manager.items.value.map { it.text })
    }

    @Test fun clearUnpinnedRemovesUnpinnedImageFilesButKeepsPinned() {
        val manager = ClipboardHistoryManager(app)
        val keep = manager.imageStore.importBytes(png(Color.GREEN))!!
        val drop = manager.imageStore.importBytes(png(Color.BLUE))!!
        manager.addImage(keep)
        manager.addImage(drop)
        manager.togglePin(manager.items.value.first { it.imageFile == keep.fileName }.id)
        manager.clearUnpinned()
        assertTrue(manager.imageStore.exists(keep.fileName))
        assertFalse(manager.imageStore.exists(drop.fileName))
    }
}
