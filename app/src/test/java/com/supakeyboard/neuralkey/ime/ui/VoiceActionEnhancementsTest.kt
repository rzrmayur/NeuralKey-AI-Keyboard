package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.VoiceTransformMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardThemes
import com.supakeyboard.neuralkey.ime.voice.WavAudioRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the Voice-to-Action enhancements: Smart Grammar / Smart Rephrase transforms,
 * the Retry icon on the AI result card, and the live-transcript recorder helpers + default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port")
class VoiceActionEnhancementsTest {

    @get:Rule
    val compose = createComposeRule()

    private val theme = KeyboardThemes.CYBERPUNK_NEON

    @Test
    fun smartPolishGrammarAndRephraseAreDistinctVoiceTransforms() {
        val modes = VoiceTransformMode.entries
        assertEquals(VoiceTransformMode.SMART_POLISH, modes[0])
        assertEquals(VoiceTransformMode.SMART_GRAMMAR, modes[1])
        assertEquals(VoiceTransformMode.SMART_REPHRASE, modes[2])
        assertEquals("Smart Grammar", VoiceTransformMode.SMART_GRAMMAR.label)
        assertEquals("Smart Rephrase", VoiceTransformMode.SMART_REPHRASE.label)

        val instructions = listOf(
            VoiceTransformMode.SMART_POLISH,
            VoiceTransformMode.SMART_GRAMMAR,
            VoiceTransformMode.SMART_REPHRASE
        ).map { it.instruction }
        assertTrue(instructions.all { it.isNotBlank() })
        assertEquals(3, instructions.toSet().size)
        assertTrue(VoiceTransformMode.SMART_GRAMMAR.instruction.contains("grammar", ignoreCase = true))
        assertTrue(VoiceTransformMode.SMART_REPHRASE.instruction.contains("rephrase", ignoreCase = true))
    }

    @Test
    fun voicePanelOffersSmartGrammarAndRephraseAndAppliesSelectedTransform() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(290.dp)) {
                    VoiceToActionPanel(
                        voiceMode = KeyboardMode.VoiceActionMode(),
                        uiState = KeyboardUiState(currentDraft = "i has went to the store yesterday"),
                        theme = theme,
                        onIntent = intents::add
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("voice_transform_SMART_POLISH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("voice_transform_SMART_REPHRASE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("voice_transform_SMART_GRAMMAR").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("✅ Apply Smart Grammar").assertIsDisplayed()
        compose.onNodeWithTag("voice_apply_button").performClick()

        val processed = intents.filterIsInstance<KeyboardIntent.ProcessVoiceTranscript>().single()
        assertEquals(VoiceTransformMode.SMART_GRAMMAR, processed.transformMode)
        assertTrue(processed.transcript.contains("store yesterday"))
    }

    @Test
    fun voicePanelRendersLiveTranscriptCardWhenLiveTranscriptIsEnabled() {
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(290.dp)) {
                    VoiceToActionPanel(
                        voiceMode = KeyboardMode.VoiceActionMode(),
                        uiState = KeyboardUiState(liveTranscriptEnabled = true),
                        theme = theme,
                        onIntent = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("voice_transcript_card").assertIsDisplayed()
        compose.onNodeWithTag("voice_mic_button").assertIsDisplayed()
        compose.onNodeWithTag("voice_live_status_badge").assertIsDisplayed()
        compose.onNodeWithText("Tap the mic to dictate", substring = true).assertIsDisplayed()
    }

    @Test
    fun voicePanelRendersWhenLiveTranscriptIsDisabledAndDefaultIsEnabled() {
        assertTrue(KeyboardUiState().liveTranscriptEnabled)
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(290.dp)) {
                    VoiceToActionPanel(
                        voiceMode = KeyboardMode.VoiceActionMode(),
                        uiState = KeyboardUiState(liveTranscriptEnabled = false),
                        theme = theme,
                        onIntent = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("voice_transcript_card").assertIsDisplayed()
        compose.onNodeWithTag("voice_mic_button").assertIsDisplayed()
    }

    @Test
    fun retryIconAppearsOnCompletedResultAndDispatchesRetry() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(290.dp)) {
                    AIStreamingPreviewCard(
                        streamingMode = KeyboardMode.AIStreamingMode(
                            originalText = "hello",
                            streamedText = "Hello there.",
                            isStreaming = false,
                            modelUsed = "Test Model",
                            canRetry = true
                        ),
                        theme = theme,
                        onIntent = intents::add
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("ai_retry_button").assertIsDisplayed().performClick()
        assertTrue(KeyboardIntent.RetryAIStreaming in intents)
        compose.onNodeWithTag("ai_retry_error_button").assertDoesNotExist()
    }

    @Test
    fun retryButtonRecoversFromErrorState() {
        val intents = mutableListOf<KeyboardIntent>()
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(290.dp)) {
                    AIStreamingPreviewCard(
                        streamingMode = KeyboardMode.AIStreamingMode(
                            originalText = "hello",
                            streamedText = "",
                            isStreaming = false,
                            modelUsed = "Test Model",
                            error = "Provider timed out",
                            canRetry = true
                        ),
                        theme = theme,
                        onIntent = intents::add
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("ai_retry_error_button").assertIsDisplayed().performClick()
        assertTrue(KeyboardIntent.RetryAIStreaming in intents)
        compose.onNodeWithTag("ai_retry_button").assertDoesNotExist()
    }

    @Test
    fun retryIsHiddenWhileStreamingOrWhenNotRetryable() {
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(360.dp).height(290.dp)) {
                    AIStreamingPreviewCard(
                        streamingMode = KeyboardMode.AIStreamingMode(
                            originalText = "hello",
                            streamedText = "partial",
                            isStreaming = true,
                            modelUsed = "Test Model",
                            canRetry = true
                        ),
                        theme = theme,
                        onIntent = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("ai_retry_button").assertDoesNotExist()
        compose.onNodeWithTag("ai_retry_error_button").assertDoesNotExist()
    }

    @Test
    fun recorderLiveChunkHelpersAreSafeWhenIdle() {
        val recorder = WavAudioRecorder()
        assertEquals(0, recorder.capturedPcmSize())
        assertEquals(0, recorder.copyPcmRange(0, 1_000).size)
        assertFalse(recorder.isRecording)
    }
}

