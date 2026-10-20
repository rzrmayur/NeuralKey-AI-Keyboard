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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Locale

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
    val displayTranscript = remember(committedTranscript, livePartialTranscript) {
        if (livePartialTranscript.isNotBlank()) {
            VoiceAudioEngine.mergeRecognizedSegment(committedTranscript, livePartialTranscript)
        } else {
            committedTranscript
        }
    }

    var isListening by remember { mutableStateOf(false) }
    var isTranscribingCloud by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    var lastRecordedWavBase64 by remember { mutableStateOf<String?>(null) }

    val voiceModels = uiState.availableVoiceModels
    val activeVoiceModel = remember(voiceModels, uiState.selectedVoiceModelId) {
        voiceModels.firstOrNull { it.id == uiState.selectedVoiceModelId } ?: voiceModels.first()
    }

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

    fun startFallbackSpeechRecognizer() {
        if (speechRecognizer == null) {
            voiceStatus = "Microphone unavailable on this device."
            return
        }
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
                        committedTranscript = VoiceAudioEngine.mergeRecognizedSegment(committedTranscript, transcript)
                        voiceStatus = "Transcribed by ${activeVoiceModel.name}! Tap Apply below."
                    } else {
                        val err = result?.exceptionOrNull()?.localizedMessage ?: "Check OpenRouter API key"
                        voiceStatus = "${activeVoiceModel.name}: $err • Tap Apply to stream audio directly"
                    }
                }
                return
            }
        }
        runCatching { speechRecognizer?.stopListening() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.previewCardBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Text(
                    text = "🎙️ Voice-to-Action Studio (Scribe v2 & Audio AI)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.keyTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = voiceStatus,
                    fontSize = 10.sp,
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Grant Mic",
                        fontSize = 10.sp,
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
                        .clip(RoundedCornerShape(7.dp))
                        .background(theme.keyFunctionalBgColor)
                        .clickable {
                            committedTranscript = ""
                            livePartialTranscript = ""
                            lastRecordedWavBase64 = null
                            voiceStatus = "Cleared • Ready (${activeVoiceModel.name})"
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Clear",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF87171),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(
                onClick = { onIntent(KeyboardIntent.CloseVoiceAction) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Voice Studio",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(17.dp)
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable {
                            onIntent(KeyboardIntent.SelectVoiceModel(model.id))
                            voiceStatus = "Switched to ${model.name} (${model.badge})"
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = model.name,
                        fontSize = 10.sp,
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
                            fontWeight = FontWeight.Bold,
                            color = if (model.isFree) Color(0xFF34D399) else theme.keySecondaryTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 3. Transform Mode Selector Strip (including 🇸🇰 Voice → Slovak)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            VoiceTransformMode.entries.forEach { mode ->
                val selected = selectedTransform == mode
                val chipBg by animateColorAsState(
                    targetValue = if (selected) theme.accentPrimary else theme.keyBgColor,
                    label = "voiceChipBg"
                )
                val chipFg = if (selected) onThemeAccent(theme.accentPrimary) else theme.keyTextColor
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(chipBg)
                        .border(
                            1.dp,
                            if (selected) theme.accentPrimary else theme.keyFunctionalBgColor,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable { selectedTransform = mode }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${mode.emoji} ${mode.label}",
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = chipFg,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 4. Center Mic + Clean Transcript Preview Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Pulsing HD WAV Microphone Button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            isListening -> Color(0xFFEF4444)
                            isTranscribingCloud -> theme.accentSecondary
                            else -> theme.accentPrimary
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
                                // Start hardware 16kHz PCM WAV recording for Scribe v2 / OpenRouter Audio
                                val startedWav = wavRecorder.start()
                                if (startedWav) {
                                    isListening = true
                                    voiceStatus = "Recording 16kHz HD WAV for ${activeVoiceModel.name}... Tap Stop when done"
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
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Recording" else "Start Voice Input",
                        tint = if (isListening) Color.White else onThemeAccent(theme.accentPrimary),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Deduplicated Spoken Transcript / Brain-Dump Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.keyBgColor)
                    .border(1.dp, theme.keyFunctionalBgColor, RoundedCornerShape(10.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        isListening && displayTranscript.isBlank() ->
                            "🔴 Listening in 16kHz HD Audio... speak naturally (stutters & repeated words are automatically filtered)."
                        isTranscribingCloud && displayTranscript.isBlank() ->
                            "⚡ Processing voice audio via ${activeVoiceModel.name}..."
                        displayTranscript.isBlank() ->
                            "Tap Mic to dictate with ${activeVoiceModel.name}, or pick a quick voice test below..."
                        else -> displayTranscript
                    },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = if (displayTranscript.isBlank()) theme.keySecondaryTextColor else theme.keyTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Quick sample voice brain-dumps for instant testing
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
                                    livePartialTranscript = ""
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
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

        // 5. Bottom Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
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
                },
                enabled = isListening || displayTranscript.isNotBlank() || !lastRecordedWavBase64.isNullOrBlank(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            ) {
                Text(
                    text = if (isListening) {
                        "⏹ Stop & Apply ${selectedTransform.emoji} ${selectedTransform.label}"
                    } else {
                        "${selectedTransform.emoji} Apply ${selectedTransform.label} (${activeVoiceModel.name.substringAfter(": ")})"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = onThemeAccent(theme.accentPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
