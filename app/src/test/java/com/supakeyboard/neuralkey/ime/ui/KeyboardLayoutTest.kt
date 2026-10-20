package com.supakeyboard.neuralkey.ime.ui

import android.text.InputType
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
class KeyboardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolbarHasNoEmptySuggestionRowAndPanelsKeepHeight() {
        val state = mutableStateOf(KeyboardUiState())
        compose.setContent {
            MaterialTheme {
                KeyboardRootView(state.value, {}, Modifier.testTag("root"))
            }
        }
        val root = compose.onNodeWithTag("root").getUnclippedBoundsInRoot()
        val firstKey = compose.onNodeWithContentDescription("q").getUnclippedBoundsInRoot()
        // One 48dp toolbar, then the first key (including its normal key gutter).
        assertTrue(firstKey.top - root.top < 58.dp)
        for (mode in listOf(KeyboardMode.AIMenuMode("hello"), KeyboardMode.ModelPickerMode(),
            KeyboardMode.AIStreamingMode("hello", "world", false))) {
            compose.runOnIdle { state.value = state.value.copy(mode = mode) }
            assertEquals((root.bottom - root.top).value, compose.onNodeWithTag("root").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }, 0.1f)
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-port")
    fun narrowWindowKeepsSwitchAndHideControlsAccessible() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme { KeyboardRootView(KeyboardUiState(), intents::add) }
        }
        compose.onNodeWithContentDescription("Switch keyboard").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Hide keyboard").assertIsDisplayed().performClick()
        assertTrue(KeyboardIntent.SwitchToNextIme in intents)
        assertTrue(KeyboardIntent.HideKeyboard in intents)
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land")
    fun landscapeKeepsControlsVisibleAndCompact() {
        compose.setContent {
            MaterialTheme {
                KeyboardRootView(KeyboardUiState(), {}, Modifier.testTag("root"))
            }
        }
        val root = compose.onNodeWithTag("root").getUnclippedBoundsInRoot()
        val switch = compose.onNodeWithContentDescription("Switch keyboard").getUnclippedBoundsInRoot()
        val enter = compose.onNodeWithContentDescription("Enter").getUnclippedBoundsInRoot()
        assertTrue(switch.left >= root.left)
        assertTrue(enter.right <= root.right)
        assertTrue(root.bottom - root.top < 220.dp)
    }

    @Test fun cancelledCharacterTouchDoesNotCommit() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme { KeyboardRootView(KeyboardUiState(), intents::add) }
        }
        compose.onNodeWithContentDescription("q").performTouchInput {
            down(center)
            cancel()
        }
        compose.waitForIdle()
        assertTrue(intents.none { it is KeyboardIntent.KeyPress })
    }

    @Test fun cancelledBackspaceStopsRepeating() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme { KeyboardRootView(KeyboardUiState(), intents::add) }
        }
        compose.onNodeWithContentDescription("Backspace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Backspace").performTouchInput { cancel() }
        compose.waitForIdle()
        val afterCancel = intents.size
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(afterCancel, intents.size)
        assertTrue(KeyboardIntent.DeleteBackwards in intents)
    }

    @Test fun suggestionsShareToolbarWithoutMovingKeysAndRemainSelectable() {
        val state = mutableStateOf(KeyboardUiState(inputType = InputType.TYPE_CLASS_TEXT))
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent { MaterialTheme { KeyboardRootView(state.value, intents::add, Modifier.testTag("root")) } }
        val original = compose.onNodeWithContentDescription("q").getUnclippedBoundsInRoot()
        compose.runOnIdle { state.value = state.value.copy(suggestions = listOf("hell", "hello", "help")) }
        compose.onNodeWithText("hello").assertIsDisplayed().performClick()
        assertTrue(KeyboardIntent.SelectSuggestion("hello") in intents)
        assertEquals(original, compose.onNodeWithContentDescription("q").getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("Open AI Studio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide keyboard").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(suggestionsEnabled = false) }
        compose.onNodeWithText("hello").assertDoesNotExist()
        assertEquals(original, compose.onNodeWithContentDescription("q").getUnclippedBoundsInRoot())
    }

    @Test fun urlFieldDoesNotDisplayStaleWordSuggestions() {
        compose.setContent {
            MaterialTheme {
                KeyboardRootView(KeyboardUiState(
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                    suggestions = listOf("hello")
                ), {})
            }
        }
        compose.onNodeWithText("hello").assertDoesNotExist()
        compose.onNodeWithText("Choose AI model").assertIsDisplayed()
    }
}
