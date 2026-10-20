package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.layout.WindowInsets
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

    @Test fun bottomKeysStayAboveSystemControlsAndPanelsKeepHeight() {
        val state = mutableStateOf(KeyboardUiState())
        compose.setContent {
            MaterialTheme {
                KeyboardRootView(state.value, {}, Modifier.testTag("root"), WindowInsets(bottom = 48.dp))
            }
        }
        val root = compose.onNodeWithTag("root").getUnclippedBoundsInRoot()
        val switch = compose.onNodeWithContentDescription("Switch keyboard").getUnclippedBoundsInRoot()
        val space = compose.onNodeWithContentDescription("Space").getUnclippedBoundsInRoot()
        assertTrue(root.bottom - switch.bottom >= 48.dp)
        assertTrue(root.bottom - space.bottom >= 48.dp)
        for (mode in listOf(KeyboardMode.AIMenuMode("hello"), KeyboardMode.ModelPickerMode(),
            KeyboardMode.AIStreamingMode("hello", "world", false))) {
            compose.runOnIdle { state.value = state.value.copy(mode = mode) }
            assertEquals(root.height.value, compose.onNodeWithTag("root").getUnclippedBoundsInRoot().height.value, 0.1f)
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-port")
    fun narrowWindowKeepsSwitchAndHideControlsAccessible() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme { KeyboardRootView(KeyboardUiState(), intents::add, systemInsets = WindowInsets(bottom = 24.dp)) }
        }
        compose.onNodeWithContentDescription("Switch keyboard").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Hide keyboard").assertIsDisplayed().performClick()
        assertTrue(KeyboardIntent.SwitchToNextIme in intents)
        assertTrue(KeyboardIntent.HideKeyboard in intents)
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land")
    fun landscapeRespectsSideNavigationInsets() {
        compose.setContent {
            MaterialTheme {
                KeyboardRootView(KeyboardUiState(), {}, Modifier.testTag("root"),
                    WindowInsets(left = 48.dp, right = 24.dp, bottom = 0.dp))
            }
        }
        val root = compose.onNodeWithTag("root").getUnclippedBoundsInRoot()
        val switch = compose.onNodeWithContentDescription("Switch keyboard").getUnclippedBoundsInRoot()
        val enter = compose.onNodeWithContentDescription("Enter").getUnclippedBoundsInRoot()
        assertTrue(switch.left - root.left >= 48.dp)
        assertTrue(root.right - enter.right >= 24.dp)
        assertTrue(root.height < 260.dp)
    }

    @Test fun cancelledCharacterTouchDoesNotCommit() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme { KeyboardRootView(KeyboardUiState(), intents::add, systemInsets = WindowInsets(0, 0, 0, 0)) }
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
            MaterialTheme { KeyboardRootView(KeyboardUiState(), intents::add, systemInsets = WindowInsets(0, 0, 0, 0)) }
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
}
