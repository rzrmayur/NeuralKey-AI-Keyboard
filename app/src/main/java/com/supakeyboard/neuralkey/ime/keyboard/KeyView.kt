package com.supakeyboard.neuralkey.ime.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.haptics.HapticManager
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Finger-roll tolerance (dp). A touch that stays within this distance of where it started, or that
 * lands within this margin around a key, is always treated as a tap on the original key.
 */
internal const val KEY_TAP_SLOP_DP = 8f

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
    onIntent: (KeyboardIntent) -> Unit,
    onCharacterKeyBoundsChanged: ((Char, Rect) -> Unit)? = null,
    onGlideUpdate: ((Char, Offset) -> Unit)? = null,
    onGlideFinish: (() -> Unit)? = null,
    onGlideCancel: (() -> Unit)? = null
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
    var keyRootPosition by remember { mutableStateOf(Offset.Zero) }

    // Spacebar cursor trackpad drag accumulator
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    val dragStepThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        // Critically damped and stiff: instant press-in, crisp release with no wobble.
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 2500f),
        label = "keyScale"
    )

    val displayText = key.getDisplayText(isShifted, isCapsLocked)
    val isAlphabeticKey = key.type == KeyType.CHARACTER &&
        key.primaryText.length == 1 &&
        key.primaryText[0].isLetter()

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
    val latestGlideUpdate by rememberUpdatedState(onGlideUpdate)
    val latestGlideFinish by rememberUpdatedState(onGlideFinish)
    val latestGlideCancel by rememberUpdatedState(onGlideCancel)

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

    // The outer box owns the FULL key tile (no padding, no scale) so neighbouring keys tile the grid
    // with zero dead zones and the hit area never shrinks while pressed. Visuals live in the inner box.
    Box(
        modifier = Modifier
            .weight(key.weight)
            .fillMaxHeight()
            .onGloballyPositioned { coords ->
                keyRootPosition = coords.positionInRoot()
                if (isAlphabeticKey) {
                    onCharacterKeyBoundsChanged?.invoke(
                        key.primaryText[0].lowercaseChar(),
                        coords.boundsInRoot()
                    )
                }
            }
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
                val tapSlop = KEY_TAP_SLOP_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val downPosition = down.position
                    isPressed = true
                    var released = false
                    var wasDragged = false
                    var tracking = false
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
                            val position = change.position
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
                            } else if (isAlphabeticKey && latestGlideUpdate != null) {
                                // Letters: once the finger really leaves the key (beyond the tap slop) the grid
                                // tracks the keys it crosses. On release it decides between a glide word and
                                // committing the key under the finger (Gboard-style slide-to-correct).
                                val outsideKey = position.x !in 0f..size.width.toFloat() ||
                                    position.y !in 0f..size.height.toFloat()
                                val travelled = (position - downPosition).getDistance()
                                if (tracking || (outsideKey && travelled > tapSlop)) {
                                    tracking = true
                                    isPressed = false
                                    latestGlideUpdate?.invoke(
                                        key.primaryText[0].lowercaseChar(),
                                        keyRootPosition + position
                                    )
                                    change.consume()
                                }
                            } else {
                                // Other keys: forgive small drifts, cancel only when clearly slid away.
                                val withinTolerance = position.x in -tapSlop..(size.width + tapSlop) &&
                                    position.y in -tapSlop..(size.height + tapSlop)
                                if (!withinTolerance) break
                            }
                        }
                        if (released) {
                            if (tracking) {
                                latestGlideFinish?.invoke()
                            } else if (!wasDragged && key.type != KeyType.BACKSPACE) {
                                latestActivate()
                            }
                        }
                    } finally {
                        // Also runs on ACTION_CANCEL, navigation, mode changes and disposal.
                        isPressed = false
                        backspaceJob?.cancel()
                        backspaceJob = null
                        if (tracking && !released) {
                            latestGlideCancel?.invoke()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.5.dp, vertical = 3.5.dp)
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(keyBgColor),
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
}
