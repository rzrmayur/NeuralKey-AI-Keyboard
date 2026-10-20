package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme

/**
 * Compact, fast 44dp top utility bar for NeuralKey.
 * Houses the AI Studio trigger button, 1-tap quick action pills, and active model switcher.
 */
@Composable
fun AICommandBar(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    theme: KeyboardTheme = uiState.currentTheme
) {
    val activeModelName = uiState.activeModel?.name ?: "Gemini 2.5 Flash"

    // Glowing animation for AI sparkle
    val infiniteTransition = rememberInfiniteTransition(label = "sparklePulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(theme.commandBarBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. AI Studio Trigger Button with dynamic theme glow
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                theme.accentPrimary.copy(alpha = glowAlpha),
                                theme.accentSecondary.copy(alpha = glowAlpha)
                            )
                        )
                    )
                    .clickable { onIntent(KeyboardIntent.OpenAIMenu) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Studio",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "AI Studio",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 2. Contextual quick suggestions pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 6.dp)
            ) {
                val suggestions = if (uiState.currentDraft.isBlank()) {
                    listOf("Hello!", "Sounds good", "Thanks!")
                } else {
                    listOf("✨ Polish", "💀 Roast", "⚡ TL;DR")
                }

                suggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.keyBgColor)
                            .border(1.dp, theme.keyFunctionalBgColor, RoundedCornerShape(12.dp))
                            .clickable {
                                when {
                                    suggestion.startsWith("✨") -> {
                                        val p = uiState.userPrompts.find {
                                            it.title.contains("Grammar", ignoreCase = true) ||
                                                    it.title.contains("Polish", ignoreCase = true)
                                        }
                                        if (p != null) onIntent(KeyboardIntent.TriggerAIAction(p)) else onIntent(KeyboardIntent.OpenAIMenu)
                                    }
                                    suggestion.startsWith("💀") -> {
                                        val p = uiState.userPrompts.find { it.title.contains("Roast", ignoreCase = true) }
                                        if (p != null) onIntent(KeyboardIntent.TriggerAIAction(p)) else onIntent(KeyboardIntent.OpenAIMenu)
                                    }
                                    suggestion.startsWith("⚡") -> {
                                        val p = uiState.userPrompts.find {
                                            it.title.contains("TL;DR", ignoreCase = true) ||
                                                    it.title.contains("Short", ignoreCase = true)
                                        }
                                        if (p != null) onIntent(KeyboardIntent.TriggerAIAction(p)) else onIntent(KeyboardIntent.OpenAIMenu)
                                    }
                                    else -> {
                                        onIntent(KeyboardIntent.TextCommit(suggestion))
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 11.sp,
                            color = theme.keyTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. Active Model Pill (tap expands inline model quick switcher)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.keyBgColor)
                    .border(1.dp, theme.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable {
                        if (uiState.mode is KeyboardMode.ModelPickerMode) {
                            onIntent(KeyboardIntent.CloseModelPicker)
                        } else {
                            onIntent(KeyboardIntent.OpenModelPicker)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ $activeModelName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.accentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp)
                )
            }
        }
    }
}
