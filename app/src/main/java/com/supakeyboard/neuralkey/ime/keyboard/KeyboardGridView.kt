package com.supakeyboard.neuralkey.ime.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState

@Composable
fun KeyboardGridView(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    inputType: Int = EditorInfo.IME_NULL,
    theme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme = uiState.currentTheme,
    isSplitLayout: Boolean = false
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
                val displayKeys = if (isSplitLayout && !isPhoneField && !isNumericField) {
                    buildSplitRow(rowKeys)
                } else {
                    rowKeys
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    displayKeys.forEach { key ->
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

/**
 * Inserts an ergonomic central thumb gap for unfolded foldable inner screens and tablets.
 * On the bottom row, splits the spacebar into left and right thumb spacebars separated by the gap.
 */
private fun buildSplitRow(rowKeys: List<KeyDefinition>): List<KeyDefinition> {
    val spaceIndex = rowKeys.indexOfFirst { it.type == KeyType.SPACE }
    if (spaceIndex != -1) {
        val spaceKey = rowKeys[spaceIndex]
        val halfSpace = spaceKey.copy(weight = (spaceKey.weight / 2f).coerceAtLeast(1.8f))
        return buildList {
            addAll(rowKeys.subList(0, spaceIndex))
            add(halfSpace)
            add(KeyDefinition(primaryText = "", type = KeyType.SPACER, weight = 1.4f))
            add(halfSpace)
            addAll(rowKeys.subList(spaceIndex + 1, rowKeys.size))
        }
    }
    val nonSpacers = rowKeys.filter { it.type != KeyType.SPACER }
    val splitIndex = nonSpacers.size / 2
    return buildList {
        addAll(nonSpacers.subList(0, splitIndex))
        add(KeyDefinition(primaryText = "", type = KeyType.SPACER, weight = 1.4f))
        addAll(nonSpacers.subList(splitIndex, nonSpacers.size))
    }
}
