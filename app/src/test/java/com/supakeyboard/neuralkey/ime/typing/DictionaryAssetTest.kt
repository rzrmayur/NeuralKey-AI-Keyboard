package com.supakeyboard.neuralkey.ime.typing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DictionaryAssetTest {
    @Test fun mergedDictionaryCanBeLoadedUsingTheApplicationsAssetPath() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val words = GZIPInputStream(context.assets.open("english_words.dict")).bufferedReader().use { it.readLines() }
        assertTrue(words.size > 100000)
        val engine = SuggestionEngine(words)
        assertTrue("hello" in engine.suggestions("hel"))
        assertEquals("receive", engine.correction("recieve"))
        assertNotNull(context.assets.open("licenses/english-dictionary.txt").use { it.read() })
    }
}
