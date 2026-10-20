package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme

/** Bounded controls leave room for long model names, narrow windows and large fonts. */
@Composable
fun AICommandBar(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    theme: KeyboardTheme = uiState.currentTheme
) {
    Row(
        modifier.fillMaxWidth().height(44.dp).background(theme.commandBarBg).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(onClick = { onIntent(KeyboardIntent.OpenAIMenu) }, enabled = !uiState.isSensitiveField) {
            Icon(Icons.Default.AutoAwesome, "Open AI Studio", tint = theme.accentPrimary)
        }
        Text(
            text = if (uiState.isSensitiveField) "Private field" else uiState.activeModel?.name ?: "Choose AI model",
            color = theme.keyTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !uiState.isSensitiveField) { onIntent(KeyboardIntent.OpenModelPicker) }
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )
        IconButton(onClick = { onIntent(KeyboardIntent.HideKeyboard) }) {
            Icon(Icons.Default.KeyboardHide, "Hide keyboard", tint = theme.keyFunctionalTextColor)
        }
    }
}

@Composable
fun SuggestionBar(uiState: KeyboardUiState, theme: KeyboardTheme, onIntent: (KeyboardIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(theme.commandBarBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.suggestionsEnabled && !uiState.isSensitiveField) {
            uiState.suggestions.forEach { word ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onIntent(KeyboardIntent.SelectSuggestion(word)) }
                        .padding(horizontal = 8.dp), contentAlignment = Alignment.Center
                ) {
                    Text(word, color = theme.keyTextColor, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
