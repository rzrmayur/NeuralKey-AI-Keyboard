package com.supakeyboard.neuralkey.ime.typing

import org.junit.Assert.*
import org.junit.Test

class SuggestionEngineTest {
    private val engine = SuggestionEngine(listOf("hello", "hellos", "help", "helpful", "helmet", "meeting", "meetings", "world"))
    @Test fun completesWordsAndRetainsOriginal() {
        val result = engine.suggestions("hel")
        assertEquals("hel", result.first())
        assertEquals(3, result.size)
        assertTrue(result.drop(1).all { it.startsWith("hel") })
    }
    @Test fun suggestsKnownTypos() {
        assertTrue("receive" in engine.suggestions("recieve"))
        assertEquals("receive", engine.correction("recieve"))
    }
    @Test fun ambiguousOrKnownWordsAreNotAutoReplaced() {
        assertNull(engine.correction("hello"))
        assertNull(engine.correction("hellp")) // hello / help: user chooses
        assertNull(engine.correction("Mayur"))
        assertNull(engine.correction("Kotlin"))
        assertNull(engine.correction("TEH"))
        assertNull(engine.correction("tEh"))
    }
    @Test fun supportsCaseInSuggestions() {
        assertTrue("Hello" in engine.suggestions("Hel"))
        assertTrue("HELLO" in engine.suggestions("HEL"))
        assertEquals("Receive", engine.correction("Recieve"))
    }
    @Test fun nonWordsAreNeverCorrectedOrSuggested() {
        for (value in listOf("", "a@b", "42", "my_var", "Slovák", "a".repeat(33))) {
            assertNull(engine.correction(value))
            assertTrue(engine.suggestions(value).isEmpty())
        }
    }
}
