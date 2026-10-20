package com.supakeyboard.neuralkey.ime.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.keyboard.KeyboardGridView
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.theme.createDynamicKeyboardTheme


@Composable
fun KeyboardRootView(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    systemInsets: WindowInsets? = null
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
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyboardContentHeight = when {
        isLandscape -> 150.dp
        configuration.screenHeightDp < 650 -> 225.dp
        else -> 248.dp
    }
    val totalPanelHeight = keyboardContentHeight + 84.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(resolvedTheme.backgroundBrush)
            // Consume only remaining system insets. Never use imePadding inside an IME.
            // Caption bars include newer system keyboard-switch/hide controls.
            .windowInsetsPadding(
                (systemInsets ?: WindowInsets.navigationBars.union(WindowInsets.captionBar).union(WindowInsets.displayCutout))
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(bottom = 6.dp)
    ) {
        when (uiState.mode) {
            is KeyboardMode.TypingMode -> {
                // 1. Top Utility Command Bar (44dp)
                AICommandBar(
                    uiState = uiState,
                    theme = resolvedTheme,
                    onIntent = onIntent
                )

                SuggestionBar(uiState, resolvedTheme, onIntent)

                // 2. Physical QWERTY / Symbols Key Grid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keyboardContentHeight)
                ) {
                    KeyboardGridView(
                        uiState = uiState,
                        theme = resolvedTheme,
                        onIntent = onIntent,
                        inputType = uiState.inputType
                    )
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
        }
    }
}
