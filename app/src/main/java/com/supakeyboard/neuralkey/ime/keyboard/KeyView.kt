package com.supakeyboard.neuralkey.ime.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.haptics.HapticManager
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun RowScope.KeyView(
    key: KeyDefinition,
    isShifted: Boolean,
    isCapsLocked: Boolean,
    hapticEnabled: Boolean,
    hapticIntensity: Int,
    activeModelName: String?,
    imeAction: Int,
    theme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit
) {
    if (key.type == KeyType.SPACER) {
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier
                .weight(key.weight)
                .fillMaxHeight()
        )
        return
    }

    val view = LocalView.current
    val hapticManager = remember { HapticManager.getInstance(view.context) }
    val scope = rememberCoroutineScope()

    var isPressed by remember { mutableStateOf(false) }
    var backspaceJob by remember { mutableStateOf<Job?>(null) }

    // Spacebar cursor trackpad drag accumulator
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    val dragStepThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "keyScale"
    )

    val displayText = key.getDisplayText(isShifted, isCapsLocked)

    // Dynamic background colors from active theme
    val keyBgColor = when {
        key.type == KeyType.SHIFT && (isShifted || isCapsLocked) -> theme.accentPrimary
        key.type == KeyType.ENTER -> when (imeAction) {
            EditorInfo.IME_ACTION_SEARCH -> Color(0xFF0284C7)
            EditorInfo.IME_ACTION_SEND -> Color(0xFF10B981)
            EditorInfo.IME_ACTION_GO -> theme.accentPrimary
            else -> theme.keyFunctionalBgColor
        }
        isPressed -> theme.keyPressedBgColor
        key.isFunctional -> theme.keyFunctionalBgColor
        else -> theme.keyBgColor
    }

    val contentColor = when {
        key.type == KeyType.SHIFT && (isShifted || isCapsLocked) -> Color.White
        key.type == KeyType.ENTER -> Color.White
        key.isFunctional -> theme.keyFunctionalTextColor
        else -> theme.keyTextColor
    }

    val latestIntent by rememberUpdatedState(onIntent)
    val latestHaptics by rememberUpdatedState(hapticEnabled)
    val latestIntensity by rememberUpdatedState(hapticIntensity)
    val latestDisplay by rememberUpdatedState(displayText)
    val activate: () -> Unit = {
        when (key.type) {
            KeyType.CHARACTER -> latestIntent(KeyboardIntent.KeyPress(latestDisplay))
            KeyType.SPACE -> latestIntent(KeyboardIntent.KeyPress(" "))
            KeyType.SHIFT -> latestIntent(KeyboardIntent.ToggleShift)
            KeyType.SYMBOLS_TOGGLE -> latestIntent(KeyboardIntent.ToggleSymbols)
            KeyType.SYMBOLS_PAGE_TOGGLE -> latestIntent(KeyboardIntent.SwitchSymbolsPage)
            KeyType.ENTER -> latestIntent(KeyboardIntent.KeyCodePress(android.view.KeyEvent.KEYCODE_ENTER))
            KeyType.SWITCH_IME -> latestIntent(KeyboardIntent.SwitchToNextIme)
            KeyType.COMMA -> latestIntent(KeyboardIntent.KeyPress(","))
            KeyType.PERIOD -> latestIntent(KeyboardIntent.KeyPress("."))
            KeyType.BACKSPACE -> latestIntent(KeyboardIntent.DeleteBackwards)
            KeyType.SPACER -> Unit
        }
    }
    val latestActivate by rememberUpdatedState(activate)

    Box(
        modifier = Modifier
            .weight(key.weight)
            .fillMaxHeight()
            .padding(horizontal = 2.5.dp, vertical = 3.5.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(keyBgColor)
            .semantics {
                contentDescription = when (key.type) {
                    KeyType.BACKSPACE -> "Backspace"
                    KeyType.SWITCH_IME -> "Switch keyboard"
                    KeyType.SPACE -> "Space"
                    KeyType.ENTER -> "Enter"
                    else -> displayText
                }
                onClick { latestActivate(); true }
            }
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    isPressed = true
                    var released = false
                    var wasDragged = false
                    accumulatedDragX = 0f
                    try {
                        hapticManager.performKeyTap(view, latestHaptics, latestIntensity)
                        if (key.type == KeyType.BACKSPACE) {
                            latestIntent(KeyboardIntent.DeleteBackwards)
                            backspaceJob = scope.launch {
                                delay(400)
                                var count = 0
                                while (isActive) {
                                    val wordMode = count++ >= 12
                                    latestIntent(KeyboardIntent.AcceleratedDelete(wordMode))
                                    hapticManager.performCursorTick(view, latestHaptics)
                                    delay(if (wordMode) 45 else 75)
                                }
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // A system gesture or another handler may cancel this key.
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                released = true
                                change.consume()
                                break
                            }
                            if (key.type == KeyType.SPACE) {
                                accumulatedDragX += change.positionChange().x
                                if (abs(accumulatedDragX) >= dragStepThresholdPx) {
                                    wasDragged = true
                                    val steps = (accumulatedDragX / dragStepThresholdPx).toInt()
                                    accumulatedDragX -= steps * dragStepThresholdPx
                                    latestIntent(KeyboardIntent.MoveCursor(steps))
                                    hapticManager.performCursorTick(view, latestHaptics)
                                }
                                change.consume()
                            } else if (change.position.x !in 0f..size.width.toFloat() ||
                                change.position.y !in 0f..size.height.toFloat()) break
                        }
                        if (released && !wasDragged && key.type != KeyType.BACKSPACE) latestActivate()
                    } finally {
                        // Also runs on ACTION_CANCEL, navigation, mode changes and disposal.
                        isPressed = false
                        backspaceJob?.cancel()
                        backspaceJob = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Secondary hint (e.g. number above top row)
            if (key.secondaryText != null) {
                Text(
                    text = key.secondaryText,
                    fontSize = 9.sp,
                    color = theme.keySecondaryTextColor,
                    lineHeight = 10.sp
                )
            }

            if (key.type == KeyType.SPACE) {
                val label = activeModelName?.let { "⚡ $it" } ?: "NeuralKey"
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = theme.accentPrimary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = displayText,
                    fontSize = if (key.isFunctional) 14.sp else 19.sp,
                    fontWeight = if (key.isFunctional) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
