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
    var lastShiftTapTime by remember { mutableStateOf(0L) }

    // Spacebar cursor trackpad drag accumulator
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    val dragStepThresholdPx = 32f

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

    Box(
        modifier = Modifier
            .weight(key.weight)
            .fillMaxHeight()
            .padding(horizontal = 2.5.dp, vertical = 3.5.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(keyBgColor)
            .pointerInput(key, isShifted, isCapsLocked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true

                    // Trigger immediate key-down haptic
                    hapticManager.performKeyTap(view, hapticEnabled, hapticIntensity)

                    when (key.type) {
                        KeyType.BACKSPACE -> {
                            // Immediate first delete
                            onIntent(KeyboardIntent.DeleteBackwards)

                            // Launch accelerated backspace ticker
                            backspaceJob?.cancel()
                            backspaceJob = scope.launch {
                                delay(400) // Initial hold delay
                                var count = 0
                                while (isActive) {
                                    val isWordMode = count >= 12
                                    onIntent(KeyboardIntent.AcceleratedDelete(isWordMode = isWordMode))
                                    hapticManager.performCursorTick(view, hapticEnabled)
                                    val repeatDelay = if (isWordMode) 45L else 75L
                                    delay(repeatDelay)
                                    count++
                                }
                            }
                        }
                        KeyType.SPACE -> {
                            accumulatedDragX = 0f
                        }
                        else -> {
                            // Non-backspace keys
                        }
                    }

                    // Loop while pointer is held down
                    var wasDragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) {
                            // Pointer released
                            break
                        }

                        if (key.type == KeyType.SPACE) {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val dx = change.positionChange().x
                                accumulatedDragX += dx
                                if (abs(accumulatedDragX) >= dragStepThresholdPx) {
                                    wasDragged = true
                                    val steps = (accumulatedDragX / dragStepThresholdPx).toInt()
                                    accumulatedDragX -= steps * dragStepThresholdPx
                                    onIntent(KeyboardIntent.MoveCursor(steps))
                                    hapticManager.performCursorTick(view, hapticEnabled)
                                }
                            }
                        }
                    }

                    // UP / RELEASE
                    isPressed = false
                    backspaceJob?.cancel()
                    backspaceJob = null

                    when (key.type) {
                        KeyType.CHARACTER -> {
                            onIntent(KeyboardIntent.KeyPress(displayText))
                        }
                        KeyType.SPACE -> {
                            if (!wasDragged) {
                                onIntent(KeyboardIntent.KeyPress(" "))
                            }
                        }
                        KeyType.SHIFT -> {
                            val now = System.currentTimeMillis()
                            if (now - lastShiftTapTime < 300) {
                                // Double-tap detected: Caps Lock
                                onIntent(KeyboardIntent.ToggleShift)
                            } else {
                                onIntent(KeyboardIntent.ToggleShift)
                            }
                            lastShiftTapTime = now
                        }
                        KeyType.SYMBOLS_TOGGLE -> {
                            onIntent(KeyboardIntent.ToggleSymbols)
                        }
                        KeyType.SYMBOLS_PAGE_TOGGLE -> {
                            onIntent(KeyboardIntent.SwitchSymbolsPage)
                        }
                        KeyType.ENTER -> {
                            onIntent(KeyboardIntent.KeyCodePress(android.view.KeyEvent.KEYCODE_ENTER))
                        }
                        KeyType.SWITCH_IME -> {
                            onIntent(KeyboardIntent.SwitchToNextIme)
                        }
                        KeyType.COMMA -> {
                            onIntent(KeyboardIntent.KeyPress(","))
                        }
                        KeyType.PERIOD -> {
                            onIntent(KeyboardIntent.KeyPress("."))
                        }
                        KeyType.BACKSPACE -> {
                            // Already handled on DOWN
                        }
                        KeyType.SPACER -> {
                            // Spacers are non-interactive
                        }
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
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = displayText,
                    fontSize = if (key.isFunctional) 14.sp else 19.sp,
                    fontWeight = if (key.isFunctional) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor
                )
            }
        }
    }
}
