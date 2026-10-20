package com.supakeyboard.neuralkey.ime.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.VoiceTransformMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme
import com.supakeyboard.neuralkey.ui.settings.SettingsActivity
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
 * 3. Whisper / Multimodal Voice-to-Action Studio Panel.
 * Captures spoken thoughts via SpeechRecognizer (with graceful permission/fallback handling)
 * and transforms them into polished prose, structured emails, or action bullets via OpenRouter.
 */
@Composable
fun VoiceToActionPanel(
    voiceMode: KeyboardMode.VoiceActionMode,
    uiState: KeyboardUiState,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit
) {
    val context = LocalContext.current
    var selectedTransform by remember { mutableStateOf(voiceMode.selectedTransform) }
    var spokenTranscript by remember { mutableStateOf(uiState.currentDraft) }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember {
        mutableStateOf("Tap the microphone to dictate, then transform via OpenRouter AI")
    }
    var needsMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        )
    }

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
                voiceStatus = "Listening... speak naturally"
            }

            override fun onBeginningOfSpeech() {
                voiceStatus = "Capturing voice..."
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                voiceStatus = "Processing speech..."
            }

            override fun onError(error: Int) {
                isListening = false
                voiceStatus = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        needsMicPermission = true
                        "Microphone permission needed. Tap 'Grant Mic' or type notes below."
                    }
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No speech detected. Tap the mic to try again."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network error during speech recognition. Check internet connection."
                    else -> "Voice recognizer ready. Tap mic or use quick voice presets."
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                if (best.isNotEmpty()) {
                    spokenTranscript = if (spokenTranscript.isBlank()) best else "$spokenTranscript $best"
                    voiceStatus = "Captured! Tap '${selectedTransform.emoji} ${selectedTransform.label}' to transform."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    spokenTranscript = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val micScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.16f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.previewCardBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
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
                    text = "🎙️ Voice-to-Action AI Studio",
                    fontSize = 14.sp,
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
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
                onClick = { onIntent(KeyboardIntent.CloseVoiceAction) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Voice Studio",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Transform Mode Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipBg)
                        .border(
                            1.dp,
                            if (selected) theme.accentPrimary else theme.keyFunctionalBgColor,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedTransform = mode }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${mode.emoji} ${mode.label}",
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = chipFg,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Center Mic + Transcript Preview Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pulsing Microphone Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(if (isListening) micScale else 1f)
                    .clip(CircleShape)
                    .background(if (isListening) Color(0xFFEF4444) else theme.accentPrimary)
                    .clickable {
                        if (isListening) {
                            runCatching { speechRecognizer?.stopListening() }
                            isListening = false
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            needsMicPermission = !hasPerm
                            if (!hasPerm) {
                                voiceStatus = "Mic permission required. Tap 'Grant Mic' above or use quick voice notes."
                            } else if (speechRecognizer == null) {
                                voiceStatus = "On-device SpeechRecognizer unavailable. Use quick voice notes below."
                            } else {
                                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                }
                                runCatching {
                                    speechRecognizer.startListening(recognizerIntent)
                                    isListening = true
                                }.onFailure {
                                    voiceStatus = "Could not start microphone: ${it.localizedMessage}"
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop Recording" else "Start Voice Input",
                    tint = if (isListening) Color.White else onThemeAccent(theme.accentPrimary),
                    modifier = Modifier.size(26.dp)
                )
            }

            // Spoken Transcript / Brain-Dump Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.keyBgColor)
                    .border(1.dp, theme.keyFunctionalBgColor, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (spokenTranscript.isBlank()) {
                        "Speak messy thoughts (e.g. 'uh tell sarah we shipped v1.3 and ask if we can review metrics tomorrow at 2pm')..."
                    } else {
                        spokenTranscript
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (spokenTranscript.isBlank()) theme.keySecondaryTextColor else theme.keyTextColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Quick sample voice brain-dumps for instant testing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "🎙️ Sync Update" to "hey team um we finished the v1.3 release build let's move our qa sync to 3pm tomorrow",
                        "🎙️ Client Follow-Up" to "uh hi alex just following up on the proposal from tuesday let me know if you have any questions"
                    ).forEach { (label, sample) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.keyFunctionalBgColor)
                                .clickable { spokenTranscript = sample }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
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

        // Bottom Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (spokenTranscript.isNotBlank()) {
                        onIntent(KeyboardIntent.ProcessVoiceTranscript(spokenTranscript, selectedTransform))
                    }
                },
                enabled = spokenTranscript.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Text(
                    text = "${selectedTransform.emoji} Apply ${selectedTransform.label}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = onThemeAccent(theme.accentPrimary),
                    maxLines = 1,
                    softWrap = false
                )
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
