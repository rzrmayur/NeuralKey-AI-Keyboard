package com.supakeyboard.neuralkey.ime.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState

@Composable
fun KeyboardGridView(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    inputType: Int = EditorInfo.IME_NULL,
    theme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme = uiState.currentTheme
) {
    val isNumericField = inputType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_NUMBER
    val isPhoneField = inputType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_PHONE

    val activeLayout = when {
        isPhoneField -> KeyboardLayouts.phoneKeypad
        isNumericField && !uiState.isSymbols -> KeyboardLayouts.numericKeypad
        isNumericField && !uiState.isSymbolsPage2 -> KeyboardLayouts.symbolsRowsPage1
        isNumericField -> KeyboardLayouts.symbolsRowsPage2
        !uiState.isSymbols -> KeyboardLayouts.qwertyRows
        !uiState.isSymbolsPage2 -> KeyboardLayouts.symbolsRowsPage1
        else -> KeyboardLayouts.symbolsRowsPage2
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        activeLayout.forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                rowKeys.forEach { key ->
                    KeyView(
                        key = key,
                        isShifted = uiState.isShifted,
                        isCapsLocked = uiState.isCapsLocked,
                        hapticEnabled = uiState.hapticEnabled,
                        hapticIntensity = uiState.hapticIntensity,
                        activeModelName = uiState.activeModel?.name,
                        imeAction = uiState.imeAction,
                        theme = theme,
                        onIntent = onIntent
                    )
                }
            }
        }
    }
    }
}
