package com.supakeyboard.neuralkey.ime.typing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

/** Exercises the engine against the real bundled frequency model, exactly as the keyboard ships it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PredictionQualityTest {

    private fun freshEngine(): SuggestionEngine = SuggestionEngine(words, model)

    @Test fun commonPrefixesRankByRealWordFrequency() {
        val engine = freshEngine()
        val hel = engine.suggestions("hel")
        assertEquals("hel", hel.first())
        assertTrue(hel.drop(1).all { it in setOf("help", "hello", "helpful", "held", "helps", "helping", "health") })
        assertTrue(engine.suggestions("pleas").drop(1).any { it == "please" })
        assertTrue(engine.suggestions("beaut").drop(1).any { it == "beautiful" })
    }

    @Test fun rarePrefixCompletionsAreNoLongerAlphabeticalNoise() {
        val engine = freshEngine()
        val result = engine.suggestions("ab")
        assertFalse("aah" in result)
        assertTrue(result.drop(1).all { it in setOf("about", "above", "able", "absolutely", "ability", "abuse", "abstract", "abroad") })
    }

    @Test fun fixesCommonMistypesWithoutExplicitTable() {
        val engine = freshEngine()
        assertTrue("because" in engine.suggestions("becuse"))
        assertTrue("tomorrow" in engine.suggestions("tomorow"))
        assertTrue("definitely" in engine.suggestions("definatly"))
        assertTrue("hello" in engine.suggestions("hrllo"))
    }

    @Test fun distanceTwoTyposAreOfferedButNotForcedOnTheUser() {
        val engine = freshEngine()
        assertTrue("restaurant" in engine.suggestions("restaraunt"))
        assertNull(engine.correction("restaraunt"))
    }

    @Test fun realWordsAndNamesAreNeverAutoReplaced() {
        val engine = freshEngine()
        for (word in listOf("hello", "there", "their", "Mayur", "Kotlin", "NeuralKey", "weather", "whether", "form", "from")) {
            assertNull("$word must stay as typed", engine.correction(word))
        }
    }

    @Test fun contractionsAreSuggestedAndAutoCorrected() {
        val engine = freshEngine()
        assertEquals("don't", engine.correction("dont"))
        assertEquals("I'm", engine.correction("im"))
        assertEquals("can't", engine.correction("cant"))
        assertEquals("I", engine.correction("i"))
        assertTrue("it's" in engine.suggestions("it'"))
    }

    @Test fun nextWordPredictionUsesWebScaleBigrams() {
        val engine = freshEngine()
        assertEquals("you", engine.nextWordSuggestions("thank").first())
        assertTrue("to" in engine.nextWordSuggestions("going"))
        assertTrue("for" in engine.nextWordSuggestions("looking"))
    }

    @Test fun nextWordPredictionCoversMostEverydayWords() {
        val engine = freshEngine()
        for (word in listOf("the", "I", "you", "are", "is", "what", "when", "because", "have", "will", "we", "they")) {
            assertEquals("$word should yield 3 next-word candidates", 3, engine.nextWordSuggestions(word).size)
        }
    }

    @Test fun contextBoostsTheLikelyCompletion() {
        val engine = freshEngine()
        assertEquals("you", engine.suggestions("yo", "thank")[1])
    }

    @Test fun properNounsAreCasedCorrectly() {
        val engine = freshEngine()
        assertTrue("London" in engine.suggestions("lond"))
        assertTrue("Monday" in engine.suggestions("mond"))
    }

    @Test fun offensiveWordsAreNeverSuggested() {
        val engine = freshEngine()
        val banned = setOf("fuck", "shit", "bitch", "asshole")
        for (prefix in listOf("fu", "sh", "bit", "ass")) {
            assertTrue(engine.suggestions(prefix).drop(1).none { it.lowercase() in banned })
        }
    }

    @Test fun learnedWordsOutrankFrequencyAndCanBeUnlearned() {
        val engine = freshEngine()
        repeat(3) { engine.recordWord("Supakey") }
        assertTrue("Supakey" in engine.suggestions("supa"))
        repeat(3) { engine.unlearnWord("Supakey") }
        assertFalse("Supakey" in engine.suggestions("supa"))
    }

    @Test fun typosAreNotMemorisedAsVocabularyUnlessForced() {
        val engine = freshEngine()
        repeat(3) { engine.recordWord("wether") }
        assertEquals(0, engine.learnedWordCount())
        repeat(2) { engine.recordWord("wether", force = true) }
        assertEquals(1, engine.learnedWordCount())
        assertNull(engine.correction("wether"))
    }

    @Test fun sentenceStartSuggestionsRespectTheNextWordSetting() {
        val engine = freshEngine()
        assertEquals(3, engine.sentenceStartSuggestions().size)
        engine.nextWordPredictionEnabled = false
        assertTrue(engine.sentenceStartSuggestions().isEmpty())
    }

    @Test fun glideSwipeRanksFrequentWordFirst() {
        val engine = freshEngine()
        assertEquals("Hello", engine.decodeGlidePath("Helo").first())
        assertTrue("the" in engine.decodeGlidePath("trehgfde"))
    }

    @Test fun suggestionLatencyStaysInteractive() {
        val engine = freshEngine()
        engine.suggestions("beaut", "a")
        val start = System.nanoTime()
        repeat(20) { engine.suggestions("restaraunt", "the") }
        val perCallMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue("suggestions took ${perCallMs}ms per call", perCallMs < 150.0)
    }

    private lateinit var words: List<String>
    private lateinit var model: LanguageModel

    /** Robolectric's application context only exists inside a test instance, so load here (cached). */
    @Before fun loadAssets() {
        val cached = cache
        if (cached != null) {
            words = cached.first
            model = cached.second
            return
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        words = GZIPInputStream(context.assets.open("english_words.dict")).bufferedReader().use { it.readLines() }
        model = LanguageModel.load { context.assets.open(it) }
        cache = words to model
    }

    companion object {
        @Volatile private var cache: Pair<List<String>, LanguageModel>? = null
    }
}
