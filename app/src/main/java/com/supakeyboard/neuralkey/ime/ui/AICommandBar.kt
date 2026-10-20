package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.OneHandedMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme
import com.supakeyboard.neuralkey.ime.typing.InputFieldPolicy

/** Bounded controls leave room for long model names, narrow windows and large fonts. */
@Composable
fun AICommandBar(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    theme: KeyboardTheme = uiState.currentTheme,
    isFoldablePosture: Boolean = false
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    Row(
        modifier.fillMaxWidth().height(48.dp).background(theme.commandBarBg).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { onIntent(KeyboardIntent.OpenAIMenu) },
            enabled = !uiState.isSensitiveField,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, "Open AI Studio", tint = theme.accentPrimary)
        }

        if (screenWidthDp >= 340 && !uiState.isSensitiveField) {
            IconButton(
                onClick = { onIntent(KeyboardIntent.OpenClipboardFusion) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Smart Clipboard",
                    tint = if (uiState.clipboardItems.isNotEmpty()) theme.accentPrimary else theme.keyFunctionalTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { onIntent(KeyboardIntent.OpenVoiceAction) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice to Action",
                    tint = theme.keyFunctionalTextColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        val showSuggestions = uiState.suggestionsEnabled && !uiState.isSensitiveField &&
            InputFieldPolicy.allowsSuggestions(uiState.inputType) && uiState.suggestions.isNotEmpty()
        if (showSuggestions) {
            Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                uiState.suggestions.take(3).forEachIndexed { idx, word ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable { onIntent(KeyboardIntent.SelectSuggestion(word)) }
                            .padding(horizontal = 3.dp), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = word,
                            color = if (idx == 1) theme.accentPrimary else theme.keyTextColor,
                            fontSize = 15.sp,
                            fontWeight = if (idx == 1) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else Text(
            text = if (uiState.isSensitiveField) "Private field" else uiState.activeModel?.name ?: "Choose AI model",
            color = theme.keyTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !uiState.isSensitiveField) { onIntent(KeyboardIntent.OpenModelPicker) }
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )

        if (isFoldablePosture) {
            IconButton(
                onClick = { onIntent(KeyboardIntent.ToggleSplitLayout) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewColumn,
                    contentDescription = "Toggle split keyboard",
                    tint = if (uiState.splitKeyboardOnFold) theme.accentPrimary else theme.keyFunctionalTextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (screenWidthDp >= 340) {
            IconButton(
                onClick = { onIntent(KeyboardIntent.CycleOneHandedMode) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhonelinkSetup,
                    contentDescription = "One-handed mode",
                    tint = if (uiState.oneHandedMode != OneHandedMode.OFF) theme.accentPrimary else theme.keyFunctionalTextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = { onIntent(KeyboardIntent.HideKeyboard) }) {
            Icon(Icons.Default.KeyboardHide, "Hide keyboard", tint = theme.keyFunctionalTextColor)
        }
    }
}
