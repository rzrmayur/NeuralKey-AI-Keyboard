package com.supakeyboard.neuralkey.ime.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.keyboard.KeyboardGridView
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.OneHandedMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme
import com.supakeyboard.neuralkey.ime.theme.createDynamicKeyboardTheme

/**
 * Adaptive posture classification for standard phones, Flip cover screens, and Unfolded Foldables.
 */
enum class DeviceKeyboardPosture {
    STANDARD_PORTRAIT,
    LANDSCAPE_COMPACT,
    FLIP_COVER_COMPACT,
    FOLDABLE_UNFOLDED
}

fun resolveDevicePosture(screenWidthDp: Int, screenHeightDp: Int, orientation: Int): DeviceKeyboardPosture {
    return when {
        screenWidthDp >= 600 && screenHeightDp >= 480 -> DeviceKeyboardPosture.FOLDABLE_UNFOLDED
        orientation == Configuration.ORIENTATION_LANDSCAPE -> DeviceKeyboardPosture.LANDSCAPE_COMPACT
        screenHeightDp <= 440 -> DeviceKeyboardPosture.FLIP_COVER_COMPACT
        else -> DeviceKeyboardPosture.STANDARD_PORTRAIT
    }
}

@Composable
fun KeyboardRootView(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    // Resolve dynamic theme at runtime when SYSTEM_DYNAMIC is selected
    val resolvedTheme = remember(uiState.currentTheme.id, isSystemDark) {
        if (uiState.currentTheme.id == "SYSTEM_DYNAMIC") {
            createDynamicKeyboardTheme(context, isSystemDark)
        } else {
            uiState.currentTheme
        }
    }

    val configuration = LocalConfiguration.current
    val posture = resolveDevicePosture(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        orientation = configuration.orientation
    )
    val keyboardContentHeight = when (posture) {
        DeviceKeyboardPosture.LANDSCAPE_COMPACT -> 150.dp
        DeviceKeyboardPosture.FLIP_COVER_COMPACT -> 178.dp
        DeviceKeyboardPosture.FOLDABLE_UNFOLDED -> 252.dp
        DeviceKeyboardPosture.STANDARD_PORTRAIT ->
            if (configuration.screenHeightDp < 650) 225.dp else 248.dp
    }
    val totalPanelHeight = keyboardContentHeight + 48.dp
    val isSplitFoldableActive = posture == DeviceKeyboardPosture.FOLDABLE_UNFOLDED && uiState.splitKeyboardOnFold
    val activeOneHanded = if (posture == DeviceKeyboardPosture.STANDARD_PORTRAIT) uiState.oneHandedMode else OneHandedMode.OFF

    val view = LocalView.current
    SideEffect {
        // Continue the keyboard's surface through the system-control area.
        (view.parent?.parent as? ImeInsetsHost)?.setBackgroundColor(resolvedTheme.surfaceColor.toArgb())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(resolvedTheme.backgroundBrush)
            // Native ImeInsetsHost owns system spacing; do not apply it twice in Compose.
            .padding(bottom = 6.dp)
    ) {
        when (uiState.mode) {
            is KeyboardMode.TypingMode -> {
                // One toolbar: suggestions replace its center label while typing.
                AICommandBar(
                    uiState = uiState,
                    theme = resolvedTheme,
                    onIntent = onIntent,
                    isFoldablePosture = posture == DeviceKeyboardPosture.FOLDABLE_UNFOLDED
                )

                // 2. Physical QWERTY / Symbols Key Grid (supports One-Handed & Foldable Split)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keyboardContentHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeOneHanded == OneHandedMode.RIGHT) {
                        OneHandedDockSidebar(
                            currentMode = OneHandedMode.RIGHT,
                            theme = resolvedTheme,
                            onIntent = onIntent,
                            modifier = Modifier
                                .weight(0.18f)
                                .fillMaxHeight()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(if (activeOneHanded == OneHandedMode.OFF) 1f else 0.82f)
                            .fillMaxHeight()
                    ) {
                        KeyboardGridView(
                            uiState = uiState,
                            theme = resolvedTheme,
                            onIntent = onIntent,
                            inputType = uiState.inputType,
                            isSplitLayout = isSplitFoldableActive
                        )
                    }

                    if (activeOneHanded == OneHandedMode.LEFT) {
                        OneHandedDockSidebar(
                            currentMode = OneHandedMode.LEFT,
                            theme = resolvedTheme,
                            onIntent = onIntent,
                            modifier = Modifier
                                .weight(0.18f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
            is KeyboardMode.AIMenuMode -> {
                // Full Action Studio Panel (matches totalPanelHeight so height is rock-solid!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalPanelHeight)
                ) {
                    AIPromptSelectionPanel(
                        uiState = uiState,
                        theme = resolvedTheme,
                        onIntent = onIntent
                    )
                }
            }
            is KeyboardMode.AIStreamingMode -> {
                // Full AI Streaming Preview Panel (matches totalPanelHeight so height is rock-solid!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalPanelHeight)
                ) {
                    AIStreamingPreviewCard(
                        streamingMode = uiState.mode as KeyboardMode.AIStreamingMode,
                        theme = resolvedTheme,
                        onIntent = onIntent
                    )
                }
            }
            is KeyboardMode.ModelPickerMode -> {
                // Full Model Switcher Panel (matches totalPanelHeight so height is rock-solid!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalPanelHeight)
                ) {
                    InlineModelQuickSwitcher(
                        uiState = uiState,
                        theme = resolvedTheme,
                        onIntent = onIntent
                    )
                }
            }
            is KeyboardMode.ClipboardFusionMode -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalPanelHeight)
                ) {
                    ClipboardFusionPanel(
                        uiState = uiState,
                        theme = resolvedTheme,
                        onIntent = onIntent
                    )
                }
            }
            is KeyboardMode.ArenaCompareMode -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalPanelHeight)
                ) {
                    ArenaComparePanel(
                        arenaMode = uiState.mode as KeyboardMode.ArenaCompareMode,
                        theme = resolvedTheme,
                        onIntent = onIntent
                    )
                }
            }
            is KeyboardMode.VoiceActionMode -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalPanelHeight)
                ) {
                    VoiceToActionPanel(
                        voiceMode = uiState.mode as KeyboardMode.VoiceActionMode,
                        uiState = uiState,
                        theme = resolvedTheme,
                        onIntent = onIntent
                    )
                }
            }
        }
    }
}

@Composable
private fun OneHandedDockSidebar(
    currentMode: OneHandedMode,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val oppositeMode = if (currentMode == OneHandedMode.LEFT) OneHandedMode.RIGHT else OneHandedMode.LEFT
    val switchIcon = if (currentMode == OneHandedMode.LEFT) {
        Icons.AutoMirrored.Filled.KeyboardArrowRight
    } else {
        Icons.AutoMirrored.Filled.KeyboardArrowLeft
    }

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.commandBarBg.copy(alpha = 0.85f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.keyFunctionalBgColor)
                .semantics { contentDescription = "Exit one-handed mode" }
                .clickable { onIntent(KeyboardIntent.SetOneHandedMode(OneHandedMode.OFF)) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = null,
                tint = theme.accentPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = if (currentMode == OneHandedMode.LEFT) "LEFT" else "RIGHT",
            color = theme.keySecondaryTextColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.keyFunctionalBgColor)
                .semantics { contentDescription = "Switch one-handed side" }
                .clickable { onIntent(KeyboardIntent.SetOneHandedMode(oppositeMode)) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = switchIcon,
                contentDescription = null,
                tint = theme.keyTextColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
