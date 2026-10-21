package com.supakeyboard.neuralkey.ime.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.ImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.supakeyboard.neuralkey.NeuralKeyApplication
import com.supakeyboard.neuralkey.ime.media.GifStickerItem
import com.supakeyboard.neuralkey.ime.media.GifStickerRepository
import com.supakeyboard.neuralkey.ime.media.MediaTabCategory
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.VoiceTransformMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme
import com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine
import com.supakeyboard.neuralkey.ime.voice.WavAudioRecorder
import com.supakeyboard.neuralkey.ui.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.sin
import kotlin.math.abs

private fun onThemeAccent(color: Color): Color {
    return if (color.luminance() > 0.42f) Color(0xFF090A0F) else Color.White
}

/**
 * 1. Smart Clipboard Time-Machine & AI Context Fusion Panel.
 * Allows pasting recent/pinned clips or fusing a copied message with the current draft for 1-tap AI replies.
 */
@Composable
fun ClipboardFusionPanel(
    uiState: KeyboardUiState,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit
) {
    val clips = uiState.clipboardItems

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.previewCardBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "📋 Clipboard Time-Machine & AI Fusion",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.keyTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap Paste, or tap 'AI Reply' to reply to a copied message using your draft",
                    fontSize = 10.sp,
                    color = theme.keySecondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (clips.any { !it.isPinned }) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.keyFunctionalBgColor)
                        .clickable { onIntent(KeyboardIntent.ClearUnpinnedClipboard) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Clear",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF87171),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
                onClick = { onIntent(KeyboardIntent.CloseClipboardFusion) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Clipboard",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.keyBgColor.copy(alpha = 0.55f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = theme.accentPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "Clipboard History is Empty",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.keyTextColor
                    )
                    Text(
                        text = "Copy any message, email, or snippet in any app to unlock 1-tap Paste, AI Context Reply, TL;DR, and Translate.",
                        fontSize = 11.sp,
                        color = theme.keySecondaryTextColor
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(clips, key = { it.id }) { clip ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.keyBgColor)
                            .border(
                                width = 1.dp,
                                color = if (clip.isPinned) theme.accentPrimary.copy(alpha = 0.7f) else theme.keyFunctionalBgColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = clip.text,
                                fontSize = 12.sp,
                                color = theme.keyTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onIntent(KeyboardIntent.PasteClipboardItem(clip.text)) }
                            )
                            IconButton(
                                onClick = { onIntent(KeyboardIntent.TogglePinClipboardItem(clip.id)) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pin Clip",
                                    tint = if (clip.isPinned) theme.accentPrimary else theme.keyFunctionalTextColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = { onIntent(KeyboardIntent.DeleteClipboardItem(clip.id)) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Clip",
                                    tint = theme.keyFunctionalTextColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Action chips row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MiniActionPill(
                                label = "📥 Paste",
                                bg = theme.accentPrimary,
                                fg = onThemeAccent(theme.accentPrimary),
                                onClick = { onIntent(KeyboardIntent.PasteClipboardItem(clip.text)) }
                            )
                            MiniActionPill(
                                label = "💬 AI Reply",
                                bg = theme.keyFunctionalBgColor,
                                fg = theme.accentPrimary,
                                onClick = { onIntent(KeyboardIntent.ReplyToClipboardItem(clip.text)) }
                            )
                            MiniActionPill(
                                label = "📝 TL;DR",
                                bg = theme.keyFunctionalBgColor,
                                fg = theme.keyTextColor,
                                onClick = { onIntent(KeyboardIntent.SummarizeClipboardItem(clip.text)) }
                            )
                            MiniActionPill(
                                label = "🌐 Translate",
                                bg = theme.keyFunctionalBgColor,
                                fg = theme.keyTextColor,
                                onClick = { onIntent(KeyboardIntent.TranslateClipboardItem(clip.text)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Multi-Model Arena Split-Compare Panel.
 * Streams 2 OpenRouter models side-by-side in parallel and lets the user pick the winning response.
 */
@Composable
fun ArenaComparePanel(
    arenaMode: KeyboardMode.ArenaCompareMode,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.previewCardBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "⚔️ Multi-Model Arena Split-Compare",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.keyTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Streaming 2 OpenRouter LLMs in parallel • Tap the winning output",
                    fontSize = 10.sp,
                    color = theme.keySecondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    onIntent(
                        KeyboardIntent.RunModelArena(
                            instruction = arenaMode.instruction,
                            modelAId = arenaMode.modelAId,
                            modelBId = arenaMode.modelBId
                        )
                    )
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Re-run Arena",
                    tint = theme.accentPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = { onIntent(KeyboardIntent.CloseModelArena) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Arena",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Split side-by-side comparison columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArenaModelColumn(
                badge = "A",
                modelName = arenaMode.modelAName,
                text = arenaMode.modelAText,
                isStreaming = arenaMode.modelAStreaming,
                latencyMs = arenaMode.modelALatencyMs,
                error = arenaMode.modelAError,
                accent = theme.accentPrimary,
                theme = theme,
                onSelectWinner = {
                    if (arenaMode.modelAText.isNotBlank()) {
                        onIntent(KeyboardIntent.CommitArenaWinner(arenaMode.modelAText, arenaMode.modelAId))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            ArenaModelColumn(
                badge = "B",
                modelName = arenaMode.modelBName,
                text = arenaMode.modelBText,
                isStreaming = arenaMode.modelBStreaming,
                latencyMs = arenaMode.modelBLatencyMs,
                error = arenaMode.modelBError,
                accent = theme.accentSecondary,
                theme = theme,
                onSelectWinner = {
                    if (arenaMode.modelBText.isNotBlank()) {
                        onIntent(KeyboardIntent.CommitArenaWinner(arenaMode.modelBText, arenaMode.modelBId))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ArenaModelColumn(
    badge: String,
    modelName: String,
    text: String,
    isStreaming: Boolean,
    latencyMs: Long,
    error: String?,
    accent: Color,
    theme: KeyboardTheme,
    onSelectWinner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyBgColor)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Model title & latency
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = onThemeAccent(accent)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = modelName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.keyTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (latencyMs > 0) {
                Text(
                    text = "${latencyMs}ms",
                    fontSize = 9.sp,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Streamed output or error
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                error != null -> {
                    Text(
                        text = "⚠️ $error",
                        fontSize = 11.sp,
                        color = Color(0xFFF87171)
                    )
                }
                text.isEmpty() && isStreaming -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Streaming...",
                            fontSize = 11.sp,
                            color = theme.keySecondaryTextColor
                        )
                    }
                }
                else -> {
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = theme.keyTextColor
                    )
                }
            }
        }

        Button(
            onClick = onSelectWinner,
            enabled = !isStreaming && error == null && text.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            Text(
                text = "✓ Use Model $badge",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = onThemeAccent(accent),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Seconds between live-transcript chunks while recording (cloud-chunked partial results). */
private const val LIVE_CHUNK_INTERVAL_MS = 3_000L

/** Minimum audio per live chunk: ~1.2 s of 16 kHz, 16-bit mono PCM. */
private const val LIVE_MIN_CHUNK_BYTES = 38_400

/** After this many consecutive live-chunk failures the live preview pauses (final pass still runs on Stop). */
private const val LIVE_MAX_CONSECUTIVE_FAILURES = 2

/**
 * Animated equalizer bars driven by the microphone RMS level.
 */
@Composable
private fun VoiceWaveform(
    level: Float,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "wavePhase"
    )
    Canvas(modifier = modifier) {
        val bars = 11
        val barWidth = size.width / (bars * 2f - 1f)
        val clamped = level.coerceIn(0f, 1f)
        for (i in 0 until bars) {
            val fraction = if (active) {
                (0.16f + 0.84f * clamped * (0.35f + 0.65f * abs(sin(phase + i * 0.75f)))).coerceIn(0.12f, 1f)
            } else {
                0.14f
            }
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.95f else 0.35f),
                topLeft = Offset(i * 2f * barWidth, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * 3. Scribe v2 / Voxtral / Multimodal Voice-to-Action AI Studio Panel.
 *
 * Enhancements in v1.3.0:
 * - Configured ONLY to OpenRouter models that natively support voice/audio:
 *   ElevenLabs Scribe v2 (`elevenlabs/scribe-v2`), Mistral Voxtral (`mistralai/voxtral-mini-transcribe` & `mistralai/voxtral-small-24b-2507`),
 *   Google Gemini Flash Audio (`google/gemini-2.5-flash`), OpenAI GPT Audio / Whisper (`openai/gpt-audio-mini`, `openai/whisper-large-v3-turbo`),
 *   and Free $0.00 OpenRouter Audio models (`nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`, `thinkingmachines/inkling:free`).
 * - Captures raw 16kHz 16-bit mono PCM WAV audio via [WavAudioRecorder] with hardware NoiseSuppressor + AutomaticGainControl.
 * - Eliminates repetitive word duplication on phones by strictly separating `committedTranscript` and `livePartialTranscript`
 *   and passing all transcripts through [VoiceAudioEngine.mergeRecognizedSegment] + [VoiceAudioEngine.cleanRepetitiveSpeech].
 * - Live transcript: while recording, 3-second audio chunks are transcribed with the selected voice model and streamed into
 *   the transcript card in real time (toggle in Settings → Typing → Voice-to-Action). The final full-audio pass on Stop is
 *   authoritative and replaces the live preview; if it fails the live text is kept.
 * - Smart Polish / Smart Grammar / Smart Rephrase transforms, with a Retry icon on the result card.
 */
@Composable
fun VoiceToActionPanel(
    voiceMode: KeyboardMode.VoiceActionMode,
    uiState: KeyboardUiState,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTransform by remember { mutableStateOf(voiceMode.selectedTransform) }

    // Strictly separate committed vs partial segments so onPartialResults NEVER duplicates onResults!
    var committedTranscript by remember {
        mutableStateOf(VoiceAudioEngine.cleanRepetitiveSpeech(uiState.currentDraft))
    }
    var livePartialTranscript by remember { mutableStateOf("") }
    // Transcript as it was before the current recording started (lets us re-run / replace a recording cleanly).
    var transcriptBeforeRecording by remember { mutableStateOf("") }
    val displayTranscript = remember(committedTranscript, livePartialTranscript) {
        if (livePartialTranscript.isNotBlank()) {
            VoiceAudioEngine.mergeRecognizedSegment(committedTranscript, livePartialTranscript)
        } else {
            committedTranscript
        }
    }

    // Measured panel height: compact postures (e.g. landscape) hide optional rows so the transcript keeps room.
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val roomyPanel = panelHeightPx == 0 || panelHeightPx >= with(LocalDensity.current) { 250.dp.roundToPx() }
    var isListening by remember { mutableStateOf(false) }
    var isTranscribingCloud by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    var lastRecordedWavBase64 by remember { mutableStateOf<String?>(null) }

    val liveTranscriptEnabled = uiState.liveTranscriptEnabled
    val voiceModels = uiState.availableVoiceModels
    val activeVoiceModel = remember(voiceModels, uiState.selectedVoiceModelId) {
        voiceModels.firstOrNull { it.id == uiState.selectedVoiceModelId } ?: voiceModels.first()
    }
    val latestVoiceModelId by rememberUpdatedState(activeVoiceModel.id)
    val latestLiveEnabled by rememberUpdatedState(liveTranscriptEnabled)

    var voiceStatus by remember(activeVoiceModel) {
        mutableStateOf("Ready • ${activeVoiceModel.name} (${activeVoiceModel.badge})")
    }
    var needsMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        )
    }

    val wavRecorder = remember {
        WavAudioRecorder(
            sampleRate = 16000,
            onAmplitudeChanged = { rms -> rmsLevel = rms }
        )
    }

    // Fallback on-device recognizer used only if hardware WAV cloud transcription is unavailable/offline
    val speechRecognizer = remember {
        runCatching {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else null
        }.getOrNull()
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                livePartialTranscript = ""
                voiceStatus = "Listening (On-Device Fallback)... speak naturally"
            }

            override fun onBeginningOfSpeech() {
                voiceStatus = "Capturing speech..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                rmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                voiceStatus = "Deduplicating & cleaning speech..."
            }

            override fun onError(error: Int) {
                isListening = false
                livePartialTranscript = ""
                voiceStatus = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        needsMicPermission = true
                        "Microphone permission needed. Tap 'Grant Mic' above."
                    }
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No speech detected. Tap the mic to try again."
                    else -> "Ready • ${activeVoiceModel.name}"
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                livePartialTranscript = ""
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                if (best.isNotEmpty()) {
                    committedTranscript = VoiceAudioEngine.mergeRecognizedSegment(committedTranscript, best)
                    voiceStatus = "Captured cleanly! Tap '${selectedTransform.emoji} ${selectedTransform.label}'."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!latestLiveEnabled) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    // Only update livePartialTranscript — NEVER overwrite committedTranscript!
                    livePartialTranscript = VoiceAudioEngine.cleanRepetitiveSpeech(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            wavRecorder.cancel()
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            }
        }
    }

    // Live transcript: while the HD WAV recorder is running, transcribe fresh 3-second audio chunks
    // with the selected voice model and stream them into the transcript card.
    LaunchedEffect(isListening, liveTranscriptEnabled) {
        if (!isListening || !liveTranscriptEnabled || !wavRecorder.isRecording) return@LaunchedEffect
        val repo = runCatching { NeuralKeyApplication.instance.openRouterRepository }.getOrNull()
            ?: return@LaunchedEffect
        livePartialTranscript = ""
        var cursor = 0
        var failures = 0
        while (isActive && wavRecorder.isRecording) {
            delay(LIVE_CHUNK_INTERVAL_MS)
            if (!isActive || !wavRecorder.isRecording) break
            val end = wavRecorder.capturedPcmSize()
            if (end - cursor < LIVE_MIN_CHUNK_BYTES) continue
            val chunkPcm = wavRecorder.copyPcmRange(cursor, end)
            if (chunkPcm.isEmpty()) continue
            val chunkWav = VoiceAudioEngine.encodePcmToWavBase64(chunkPcm, 16000)
            val result = repo.transcribeVoiceAudioToText(
                base64AudioWav = chunkWav,
                preferredVoiceModelId = latestVoiceModelId
            )
            if (!isActive || !wavRecorder.isRecording) break
            val text = result.getOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) {
                livePartialTranscript = VoiceAudioEngine.mergeRecognizedSegment(livePartialTranscript, text)
                cursor = end
                failures = 0
            } else {
                val message = result.exceptionOrNull()?.message.orEmpty()
                if (message.contains("No speech", ignoreCase = true)) {
                    // Silence in this window — skip it instead of re-sending it next time
                    cursor = end
                } else {
                    failures++
                    if (failures >= LIVE_MAX_CONSECUTIVE_FAILURES) {
                        voiceStatus = "Live preview paused • full transcript appears when you stop"
                        break
                    }
                }
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val basePulse by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.14f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )
    val micScale = if (isListening) (basePulse + rmsLevel * 0.12f).coerceAtMost(1.26f) else 1f
    val glowScale = 1f + (micScale - 1f) * 1.2f

    fun startFallbackSpeechRecognizer() {
        if (speechRecognizer == null) {
            voiceStatus = "Microphone unavailable on this device."
            return
        }
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, liveTranscriptEnabled)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching {
            livePartialTranscript = ""
            speechRecognizer.startListening(recognizerIntent)
            isListening = true
        }.onFailure {
            voiceStatus = "Could not start microphone: ${it.localizedMessage}"
        }
    }

    fun transcribeRecordedWav(wavBase64: String) {
        isTranscribingCloud = true
        voiceStatus = "Transcribing via ${activeVoiceModel.name}..."
        scope.launch {
            val repo = runCatching { NeuralKeyApplication.instance.openRouterRepository }.getOrNull()
            val result = repo?.transcribeVoiceAudioToText(
                base64AudioWav = wavBase64,
                preferredVoiceModelId = activeVoiceModel.id
            )
            isTranscribingCloud = false
            val transcript = result?.getOrNull()?.trim().orEmpty()
            if (transcript.isNotBlank()) {
                // The full-audio pass is authoritative: it replaces whatever the live preview showed.
                committedTranscript = VoiceAudioEngine.mergeRecognizedSegment(transcriptBeforeRecording, transcript)
                livePartialTranscript = ""
                voiceStatus = "Transcribed by ${activeVoiceModel.name}! Tap Apply below."
            } else {
                val err = result?.exceptionOrNull()?.localizedMessage ?: "Check OpenRouter API key"
                if (livePartialTranscript.isNotBlank()) {
                    committedTranscript = VoiceAudioEngine.mergeRecognizedSegment(
                        transcriptBeforeRecording,
                        livePartialTranscript
                    )
                    livePartialTranscript = ""
                    voiceStatus = "Kept live transcript • final pass failed: $err"
                } else {
                    voiceStatus = "${activeVoiceModel.name}: $err • Tap ⟳ to retry or Apply to stream audio directly"
                }
            }
        }
    }

    fun stopRecordingAndTranscribe(autoRunTransform: Boolean) {
        isListening = false
        rmsLevel = 0f
        if (wavRecorder.isRecording) {
            val wavBase64 = wavRecorder.stopAndEncodeWavBase64()
            if (!wavBase64.isNullOrBlank()) {
                lastRecordedWavBase64 = wavBase64
                if (autoRunTransform) {
                    onIntent(KeyboardIntent.ProcessVoiceAudioWav(wavBase64, selectedTransform))
                    return
                }
                transcribeRecordedWav(wavBase64)
                return
            }
            livePartialTranscript = ""
            voiceStatus = "Recording too short • tap the mic and speak again"
            return
        }
        runCatching { speechRecognizer?.stopListening() }
    }

    val accentBrush = Brush.linearGradient(listOf(theme.accentPrimary, theme.accentSecondary))
    val recordBrush = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
    val canApply = isListening || displayTranscript.isNotBlank() || !lastRecordedWavBase64.isNullOrBlank()
    val canRetranscribe = !lastRecordedWavBase64.isNullOrBlank() && !isListening && !isTranscribingCloud

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { panelHeightPx = it.height }
            .background(theme.previewCardBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Header Row (Title + Live Badge + Waveform + Clear + Close)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🎙️ Voice-to-Action Studio",
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.keyTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val badgeText: String
                    val badgeColor: Color
                    when {
                        isListening && liveTranscriptEnabled -> {
                            badgeText = "● LIVE"
                            badgeColor = Color(0xFFEF4444)
                        }
                        isListening -> {
                            badgeText = "● REC"
                            badgeColor = Color(0xFFEF4444)
                        }
                        isTranscribingCloud -> {
                            badgeText = "⚡ AI"
                            badgeColor = theme.accentSecondary
                        }
                        liveTranscriptEnabled -> {
                            badgeText = "LIVE ON"
                            badgeColor = Color(0xFF10B981)
                        }
                        else -> {
                            badgeText = "LIVE OFF"
                            badgeColor = theme.keySecondaryTextColor
                        }
                    }
                    Box(
                        modifier = Modifier
                            .testTag("voice_live_status_badge")
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    VoiceWaveform(
                        level = rmsLevel,
                        active = isListening,
                        color = if (isListening) Color(0xFFEF4444) else theme.accentPrimary,
                        modifier = Modifier
                            .width(40.dp)
                            .height(11.dp)
                    )
                }
                Text(
                    text = voiceStatus,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = if (needsMicPermission) Color(0xFFF59E0B) else theme.keySecondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (needsMicPermission) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                        .clickable {
                            val intent = Intent(context, SettingsActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra("REQUEST_MIC_PERMISSION", true)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Grant Mic",
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (displayTranscript.isNotBlank() || lastRecordedWavBase64 != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(theme.keyFunctionalBgColor)
                        .clickable {
                            committedTranscript = ""
                            livePartialTranscript = ""
                            transcriptBeforeRecording = ""
                            lastRecordedWavBase64 = null
                            voiceStatus = "Cleared • Ready (${activeVoiceModel.name})"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear transcript",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(
                onClick = { onIntent(KeyboardIntent.CloseVoiceAction) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Voice Studio",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 2. Voice-Only OpenRouter Model Selector Strip (Scribe v2, Voxtral, Gemini Audio, Whisper, Free Omni)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎧 Voice Model:",
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = theme.accentPrimary,
                maxLines = 1,
                softWrap = false
            )
            voiceModels.forEach { model ->
                val isSelected = model.id == activeVoiceModel.id
                val bg = if (isSelected) theme.accentPrimary.copy(alpha = 0.22f) else theme.keyBgColor
                val borderCol = if (isSelected) theme.accentPrimary else theme.keyFunctionalBgColor
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                        .clickable {
                            onIntent(KeyboardIntent.SelectVoiceModel(model.id))
                            voiceStatus = "Switched to ${model.name} (${model.badge})"
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = model.name,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) theme.accentPrimary else theme.keyTextColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (model.isFree) Color(0xFF10B981).copy(alpha = 0.22f)
                                else theme.keyFunctionalBgColor
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = model.badge,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (model.isFree) Color(0xFF34D399) else theme.keySecondaryTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 3. Transform Mode Selector Strip (Smart Polish / Grammar / Rephrase, Email, Bullets, English, Slovak, Raw)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            VoiceTransformMode.entries.forEach { mode ->
                val selected = selectedTransform == mode
                val chipFg by animateColorAsState(
                    targetValue = if (selected) onThemeAccent(theme.accentPrimary) else theme.keyTextColor,
                    label = "voiceChipFg"
                )
                Box(
                    modifier = Modifier
                        .testTag("voice_transform_${mode.name}")
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (selected) Modifier.background(accentBrush)
                            else Modifier.background(theme.keyBgColor)
                        )
                        .border(
                            1.dp,
                            if (selected) theme.accentPrimary else theme.keyFunctionalBgColor,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedTransform = mode }
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${mode.emoji} ${mode.label}",
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = chipFg,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 4. Center Mic + Live Transcript Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Pulsing HD WAV Microphone Button with glow ring
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .scale(glowScale)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.20f))
                    )
                }
                Box(
                    modifier = Modifier
                        .testTag("voice_mic_button")
                        .size(46.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            when {
                                isListening -> recordBrush
                                isTranscribingCloud -> Brush.linearGradient(
                                    listOf(theme.accentSecondary, theme.accentSecondary)
                                )
                                else -> accentBrush
                            }
                        )
                        .clickable(enabled = !isTranscribingCloud) {
                            if (isListening) {
                                stopRecordingAndTranscribe(autoRunTransform = false)
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                needsMicPermission = !hasPerm
                                if (!hasPerm) {
                                    voiceStatus = "Mic permission required. Tap 'Grant Mic' above."
                                } else {
                                    transcriptBeforeRecording = committedTranscript
                                    livePartialTranscript = ""
                                    // Start hardware 16kHz PCM WAV recording for Scribe v2 / OpenRouter Audio
                                    val startedWav = wavRecorder.start()
                                    if (startedWav) {
                                        isListening = true
                                        voiceStatus = if (liveTranscriptEnabled) {
                                            "Recording • live transcript on • tap Stop when done"
                                        } else {
                                            "Recording HD audio for ${activeVoiceModel.name}... Tap Stop when done"
                                        }
                                    } else {
                                        startFallbackSpeechRecognizer()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isTranscribingCloud) {
                        CircularProgressIndicator(
                            color = onThemeAccent(theme.accentSecondary),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Recording" else "Start Voice Input",
                            tint = if (isListening) Color.White else onThemeAccent(theme.accentPrimary),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Live Transcript Card (committed text + dimmed live tail)
            Column(
                modifier = Modifier
                    .testTag("voice_transcript_card")
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.keyBgColor)
                    .border(
                        1.dp,
                        if (isListening) Color(0xFFEF4444).copy(alpha = 0.65f) else theme.keyFunctionalBgColor,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val transcriptScroll = rememberScrollState()
                LaunchedEffect(displayTranscript) {
                    transcriptScroll.scrollTo(transcriptScroll.maxValue)
                }
                val liveTailColor = theme.keySecondaryTextColor
                val transcriptText = remember(displayTranscript, committedTranscript, livePartialTranscript, liveTailColor) {
                    buildAnnotatedString {
                        val tail = if (livePartialTranscript.isNotBlank() &&
                            displayTranscript.startsWith(committedTranscript)
                        ) {
                            displayTranscript.substring(committedTranscript.length)
                        } else {
                            ""
                        }
                        append(displayTranscript.substring(0, displayTranscript.length - tail.length))
                        if (tail.isNotEmpty()) {
                            withStyle(SpanStyle(color = liveTailColor, fontStyle = FontStyle.Italic)) {
                                append(tail)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(transcriptScroll)
                ) {
                    when {
                        displayTranscript.isNotBlank() -> Text(
                            text = transcriptText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = theme.keyTextColor
                        )
                        isListening -> Text(
                            text = if (liveTranscriptEnabled) {
                                "🔴 Listening... your words appear here live as you speak (stutters & repeats are filtered)."
                            } else {
                                "🔴 Recording HD audio... your transcript appears after you tap Stop."
                            },
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = theme.keySecondaryTextColor
                        )
                        isTranscribingCloud -> Text(
                            text = "⚡ Processing voice audio via ${activeVoiceModel.name}...",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = theme.keySecondaryTextColor
                        )
                        else -> Text(
                            text = "Tap the mic to dictate with ${activeVoiceModel.name}, or try a quick sample below.",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = theme.keySecondaryTextColor
                        )
                    }
                }

                // Quick sample voice brain-dumps for instant testing (only while the card is empty)
                if (roomyPanel && displayTranscript.isBlank() && !isListening && !isTranscribingCloud) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(
                            "🎙️ Sync Update" to "hey team we finished the v1.3 release build let's move our qa sync to 3pm tomorrow",
                            "🎙️ Client Follow-Up" to "hi alex just following up on the proposal from tuesday let me know if you have any questions",
                            "🇸🇰 Slovak Test" to "Good morning team, thank you for your help with the new release today."
                        ).forEach { (label, sample) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.keyFunctionalBgColor)
                                    .clickable {
                                        committedTranscript = VoiceAudioEngine.cleanRepetitiveSpeech(sample)
                                        transcriptBeforeRecording = ""
                                        livePartialTranscript = ""
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = theme.accentPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Bottom Action Bar: gradient Apply + re-transcribe (retry) icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .testTag("voice_apply_button")
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentBrush, alpha = if (canApply) 1f else 0.35f)
                    .clickable(enabled = canApply) {
                        if (isListening && wavRecorder.isRecording) {
                            stopRecordingAndTranscribe(autoRunTransform = true)
                        } else if (displayTranscript.isNotBlank()) {
                            onIntent(
                                KeyboardIntent.ProcessVoiceTranscript(
                                    transcript = VoiceAudioEngine.cleanRepetitiveSpeech(displayTranscript),
                                    transformMode = selectedTransform
                                )
                            )
                        } else if (!lastRecordedWavBase64.isNullOrBlank()) {
                            onIntent(
                                KeyboardIntent.ProcessVoiceAudioWav(
                                    base64Wav = lastRecordedWavBase64!!,
                                    transformMode = selectedTransform
                                )
                            )
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isListening) {
                        "⏹ Stop & Apply ${selectedTransform.emoji} ${selectedTransform.label}"
                    } else {
                        "${selectedTransform.emoji} Apply ${selectedTransform.label}"
                    },
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = onThemeAccent(theme.accentPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canRetranscribe) {
                Box(
                    modifier = Modifier
                        .testTag("voice_retranscribe_button")
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(theme.keyBgColor)
                        .border(1.dp, theme.accentPrimary.copy(alpha = 0.6f), CircleShape)
                        .clickable { lastRecordedWavBase64?.let { transcribeRecordedWav(it) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-transcribe recording",
                        tint = theme.accentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 4. Internet-Wide GIF & Sticker Search Studio Panel.
 * Searches across GIPHY Global GIFs, GIPHY Transparent Stickers, Wikimedia Commons Open-Source Media,
 * and Google Open-Source Noto Animated Emoji Stickers, and commits animated GIFs/Stickers directly
 * into any app via Android's `InputConnectionCompat.commitContent`.
 */
@Composable
fun GifStickerSearchPanel(
    gifMode: KeyboardMode.GifStickerMode,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GifStickerRepository.getInstance(context) }

    var selectedCategory by remember { mutableStateOf(gifMode.initialCategory) }
    var searchQuery by remember { mutableStateOf(gifMode.initialQuery) }
    var isTypingQuery by remember { mutableStateOf(false) }
    var mediaItems by remember { mutableStateOf<List<GifStickerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery, selectedCategory) {
        isLoading = true
        if (searchQuery.isNotBlank()) {
            delay(180) // debounce typing
        }
        val results = repo.searchMedia(query = searchQuery, category = selectedCategory)
        mediaItems = results
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.previewCardBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Header + Category Switcher Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🎬 GIF & Stickers",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = theme.keyTextColor,
                maxLines = 1,
                softWrap = false
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MediaTabCategory.entries.forEach { cat ->
                    val active = selectedCategory == cat
                    val bg = if (active) theme.accentPrimary else theme.keyBgColor
                    val fg = if (active) onThemeAccent(theme.accentPrimary) else theme.keyTextColor
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(
                                1.dp,
                                if (active) theme.accentPrimary else theme.keyFunctionalBgColor,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${cat.emoji} ${cat.label}",
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = fg,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            IconButton(
                onClick = { onIntent(KeyboardIntent.CloseGifStickerSearch) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close GIF & Sticker Studio",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // 2. Search Box + Reaction Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Interactive Search Pill (toggles inline mini-QWERTY search pad)
            Row(
                modifier = Modifier
                    .width(155.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(theme.keyBgColor)
                    .border(
                        width = 1.dp,
                        color = if (isTypingQuery) theme.accentPrimary else theme.keyFunctionalBgColor,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable { isTypingQuery = !isTypingQuery }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isTypingQuery) Icons.Default.Keyboard else Icons.Default.Search,
                    contentDescription = "Search GIFs",
                    tint = theme.accentPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (searchQuery.isEmpty()) "Search GIFs..." else searchQuery,
                    fontSize = 11.sp,
                    color = if (searchQuery.isEmpty()) theme.keySecondaryTextColor else theme.keyTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.keySecondaryTextColor,
                        modifier = Modifier.clickable { searchQuery = "" }
                    )
                }
            }

            // Quick Reaction Category Chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                GifStickerRepository.QUICK_REACTION_CHIPS.forEach { (label, queryVal) ->
                    val isChipSelected = searchQuery.equals(queryVal, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isChipSelected) theme.accentPrimary.copy(alpha = 0.22f)
                                else theme.keyFunctionalBgColor
                            )
                            .border(
                                1.dp,
                                if (isChipSelected) theme.accentPrimary else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                searchQuery = queryVal
                                isTypingQuery = false
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isChipSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isChipSelected) theme.accentPrimary else theme.keyTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 3. Animated GIF & Sticker Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading && mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = theme.accentPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Searching global GIF & Sticker catalog...",
                            fontSize = 11.sp,
                            color = theme.keySecondaryTextColor
                        )
                    }
                }
            } else if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No GIFs found for \"$searchQuery\". Try another keyword or tap Trending.",
                        fontSize = 11.sp,
                        color = theme.keySecondaryTextColor
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        AnimatedNetworkMediaCard(
                            item = item,
                            repo = repo,
                            theme = theme,
                            onClick = { onIntent(KeyboardIntent.CommitGifSticker(item)) }
                        )
                    }
                }
            }
        }

        // 4. Inline Compact Mini-QWERTY Search Bar (when user taps the search box inside the IME)
        if (isTypingQuery) {
            InlineMiniSearchKeyboard(
                theme = theme,
                onChar = { ch -> searchQuery += ch },
                onBackspace = {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                    }
                },
                onDone = { isTypingQuery = false }
            )
        }
    }
}

@Composable
private fun AnimatedNetworkMediaCard(
    item: GifStickerItem,
    repo: GifStickerRepository,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    var mediaBytes by remember(item.previewUrl) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(item.previewUrl) {
        mediaBytes = repo.fetchMediaBytes(item.previewUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.keyBgColor)
            .border(1.dp, theme.keyFunctionalBgColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val bytes = mediaBytes
        if (bytes == null) {
            CircularProgressIndicator(
                color = theme.accentPrimary,
                strokeWidth = 1.8.dp,
                modifier = Modifier.size(16.dp)
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = if (item.isSticker) {
                            ImageView.ScaleType.FIT_CENTER
                        } else {
                            ImageView.ScaleType.CENTER_CROP
                        }
                    }
                },
                update = { imageView ->
                    if (imageView.tag != item.previewUrl) {
                        imageView.tag = item.previewUrl
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                                val drawable = ImageDecoder.decodeDrawable(source)
                                imageView.setImageDrawable(drawable)
                                if (drawable is AnimatedImageDrawable) {
                                    drawable.start()
                                }
                            } else {
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageDrawable(BitmapDrawable(imageView.resources, bmp))
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (item.isSticker) 4.dp else 0.dp)
            )
        }

        // Source badge in top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = item.source.take(10),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun InlineMiniSearchKeyboard(
    theme: KeyboardTheme,
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit
) {
    val rows = remember {
        listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(theme.commandBarBg)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        rows.forEachIndexed { idx, rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowKeys.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(theme.keyBgColor)
                            .clickable { onChar(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.keyTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (idx == 2) {
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(theme.keyFunctionalBgColor)
                            .clickable(onClick = onBackspace),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⌫",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.keyTextColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(theme.accentPrimary)
                            .clickable { onChar(" ") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "␣",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = onThemeAccent(theme.accentPrimary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.6f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(theme.accentPrimary)
                            .clickable(onClick = onDone),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = onThemeAccent(theme.accentPrimary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniActionPill(
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false
        )
    }
}
