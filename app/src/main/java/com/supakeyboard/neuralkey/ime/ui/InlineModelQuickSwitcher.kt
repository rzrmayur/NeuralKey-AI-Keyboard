package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState

@Composable
fun InlineModelQuickSwitcher(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    theme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme = uiState.currentTheme
) {
    val scrollState = rememberScrollState()
    val activeId = uiState.activeModel?.id

    // Use pinned models if available, otherwise show top models from catalog
    val displayModels = if (uiState.freeModelsOnly) {
        uiState.availableModels.filter { it.isFree }
    } else if (uiState.pinnedModels.isNotEmpty()) {
        uiState.pinnedModels
    } else {
        uiState.availableModels.take(8)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.previewCardBg)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Switch AI Inference Engine",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Free Models Only filter chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (uiState.freeModelsOnly) Color(0xFF10B981) else Color(0xFF27272A))
                            .clickable { onIntent(KeyboardIntent.ToggleFreeModelsOnly) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.freeModelsOnly) "✓ Free Only" else "Free Only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.freeModelsOnly) Color.Black else Color(0xFF94A3B8)
                        )
                    }

                    // Close icon
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF27272A))
                            .clickable { onIntent(KeyboardIntent.CloseModelPicker) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Horizontal Carousel of Models
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (displayModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No models cached yet. Open Settings to sync the catalog.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    displayModels.forEach { model ->
                        val isSelected = model.id == activeId
                        ModelItemPill(
                            model = model,
                            isSelected = isSelected,
                            theme = theme,
                            onClick = { onIntent(KeyboardIntent.SelectModel(model.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelItemPill(
    model: OpenRouterModelEntity,
    isSelected: Boolean,
    theme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) theme.accentPrimary else theme.keyFunctionalBgColor
    val bgColor = if (isSelected) theme.accentPrimary.copy(alpha = 0.2f) else theme.keyBgColor

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = theme.accentPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = model.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else Color(0xFFE2E8F0)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (model.isFree) "Free" else model.formattedCost,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (model.isFree) Color(0xFF34D399) else Color(0xFF94A3B8)
            )
            Text(
                text = "• ${model.formattedContextLength}",
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}
