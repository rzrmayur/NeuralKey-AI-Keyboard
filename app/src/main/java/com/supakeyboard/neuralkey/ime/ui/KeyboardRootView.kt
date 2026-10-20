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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import android.util.Log
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

private const val DIAG_TAG = "NeuralKeyDiag"

@Composable
fun KeyboardRootView(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    // DIAGNOSTIC: navigation bar inset as seen by the IME window (expected > 0 on gesture/3-button nav)
    val density = LocalDensity.current
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(navBarBottomPx) {
        Log.d(DIAG_TAG, "IME navBar.bottom=${navBarBottomPx}px (${navBarBottomPx / density.density}dp)")
    }

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
    val totalPanelHeight = keyboardContentHeight + 44.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .onSizeChanged { Log.d(DIAG_TAG, "IME root size: ${it.width}x${it.height}px") }
            .background(resolvedTheme.backgroundBrush)
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
