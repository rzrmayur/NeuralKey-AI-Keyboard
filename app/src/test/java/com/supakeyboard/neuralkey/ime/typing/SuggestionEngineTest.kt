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

    @Test fun keyMemoryLearnsCustomWordsAndBoostsRank() {
        val localEngine = SuggestionEngine(listOf("hello", "help", "helmet", "helicopter"))
        // Teach "helmet" several times so it outranks "help"
        repeat(4) { localEngine.recordWord("helmet") }
        val ranked = localEngine.suggestions("hel")
        assertEquals("hel", ranked[0])
        assertEquals("helmet", ranked[1])

        // Teach a brand-new custom word with custom casing not in SCOWL
        localEngine.recordWord("NeuralKey")
        val custom = localEngine.suggestions("neu")
        assertTrue("NeuralKey" in custom)
    }

    @Test fun contextualBigramsPredictNextWordAndBoostPrefix() {
        val localEngine = SuggestionEngine(listOf("you", "your", "young", "forward", "forever", "forest"))
        // Built-in bigram: "thank" -> "you"
        val afterThank = localEngine.nextWordSuggestions("thank")
        assertTrue("you" in afterThank)

        // Learned bigram: "project" -> "forever"
        repeat(3) { localEngine.recordWord("forever", "project") }
        val nextAfterProject = localEngine.nextWordSuggestions("project")
        assertEquals("forever", nextAfterProject.first())

        // Contextual prefix boost when typing "for" after "project"
        val contextualPrefix = localEngine.suggestions("for", "project")
        assertEquals("forever", contextualPrefix[1])
    }

    @Test fun qwertySpatialNeighborDetectionPrioritizesAdjacentKeys() {
        assertTrue(SuggestionEngine.isQwertyNeighborSubstitution("hellp", "hello")) // p is next to o
        assertFalse(SuggestionEngine.isQwertyNeighborSubstitution("hellz", "hello")) // z is far from o
    }
}
