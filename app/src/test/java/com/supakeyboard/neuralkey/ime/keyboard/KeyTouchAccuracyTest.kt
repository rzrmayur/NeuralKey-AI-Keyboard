package com.supakeyboard.neuralkey.ime.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Typing accuracy: taps must never be dropped in the gaps between keys, a drifting finger must not
 * turn a tap into a random glide word, and a short slide must commit the key under the finger.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port")
class KeyTouchAccuracyTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setKeyboard(
        intents: MutableList<KeyboardIntent>,
        glideEnabled: Boolean = true,
        shifted: Boolean = false
    ) {
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(240.dp)) {
                    KeyboardGridView(
                        uiState = KeyboardUiState(glideTypingEnabled = glideEnabled, isShifted = shifted),
                        onIntent = intents::add
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(description: String): Rect =
        compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    private fun keyPresses(intents: List<KeyboardIntent>) =
        intents.filterIsInstance<KeyboardIntent.KeyPress>().map { it.char }

    @Test
    fun tapInVisualGapBetweenKeysStillTypesTheKey() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents)
        val a = bounds("a")
        // 1px inside the tile edge = inside the old dead padding between 'a' and 's'.
        val gap = Offset(a.right - 1f, a.center.y)
        compose.onRoot().performTouchInput { down(gap); up() }
        compose.waitForIdle()
        assertEquals(listOf("a"), keyPresses(intents))
    }

    @Test
    fun tapInVerticalGapStillTypesTheKey() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents)
        val a = bounds("a")
        compose.onRoot().performTouchInput { down(Offset(a.center.x, a.bottom - 1f)); up() }
        compose.waitForIdle()
        assertEquals(listOf("a"), keyPresses(intents))
    }

    @Test
    fun smallFingerRollIntoNeighbourStaysATapOnTheOriginalKey() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents)
        val a = bounds("a")
        val slopPx = with(compose.density) { KEY_TAP_SLOP_DP.dp.toPx() }
        val roll = slopPx * 0.6f
        val start = Offset(a.right - roll * 0.3f, a.center.y)
        // Rolls 60% of the tap slop, crossing the 'a'/'s' boundary, then lifts.
        compose.onRoot().performTouchInput { down(start); moveTo(Offset(start.x + roll, start.y)); up() }
        compose.waitForIdle()
        assertEquals(listOf("a"), keyPresses(intents))
        assertTrue(intents.none { it is KeyboardIntent.GlideTypingPath })
    }

    @Test
    fun shortSlideOntoNeighbourCommitsTheKeyUnderTheFingerNotAGlideWord() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents)
        val a = bounds("a")
        val s = bounds("s")
        compose.onRoot().performTouchInput { down(a.center); moveTo(s.center); up() }
        compose.waitForIdle()
        assertEquals(listOf("s"), keyPresses(intents))
        assertTrue(intents.none { it is KeyboardIntent.GlideTypingPath })
    }

    @Test
    fun swipeAcrossThreeKeysIsStillDecodedAsGlideWord() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents)
        val a = bounds("a")
        val s = bounds("s")
        val d = bounds("d")
        compose.onRoot().performTouchInput {
            down(a.center)
            moveTo(s.center)
            moveTo(d.center)
            up()
        }
        compose.waitForIdle()
        val glide = intents.filterIsInstance<KeyboardIntent.GlideTypingPath>().single()
        assertEquals("asd", glide.rawKeySequence)
        assertTrue(keyPresses(intents).isEmpty())
    }

    @Test
    fun swipeWithGlideDisabledCommitsOnlyTheFinalKey() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents, glideEnabled = false)
        val a = bounds("a")
        val s = bounds("s")
        val d = bounds("d")
        compose.onRoot().performTouchInput {
            down(a.center)
            moveTo(s.center)
            moveTo(d.center)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("d"), keyPresses(intents))
        assertTrue(intents.none { it is KeyboardIntent.GlideTypingPath })
    }

    @Test
    fun slideOntoNeighbourWhileShiftedCommitsUppercase() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents, shifted = true)
        val a = bounds("A")
        val s = bounds("S")
        compose.onRoot().performTouchInput { down(a.center); moveTo(s.center); up() }
        compose.waitForIdle()
        assertEquals(listOf("S"), keyPresses(intents))
    }

    @Test
    fun plainTapOnKeyCenterTypesExactlyOnce() {
        val intents = mutableListOf<KeyboardIntent>()
        setKeyboard(intents)
        val q = bounds("q")
        compose.onRoot().performTouchInput { down(q.center); up() }
        compose.waitForIdle()
        assertEquals(listOf("q"), keyPresses(intents))
    }
}
