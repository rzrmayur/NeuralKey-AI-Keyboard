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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme

/**
 * Intuitive, full-keyboard replacement panel for selecting AI action prompts.
 * Replaces the cramped 52dp horizontal scroll with a crystal-clear, categorized 2-column grid.
 */
@Composable
fun AIPromptSelectionPanel(
    uiState: KeyboardUiState,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var customPromptInput by remember { mutableStateOf("") }

    val categories = listOf("All", "Productivity", "Humor & Vibes", "Styles", "Translation")

    val filteredPrompts = remember(selectedCategory, uiState.userPrompts) {
        if (selectedCategory == "All") {
            uiState.userPrompts
        } else {
            uiState.userPrompts.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    val activeModelName = uiState.activeModel?.name ?: "Gemini 2.5 Flash"
    val hasDraft = uiState.currentDraft.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(theme.previewCardBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = theme.accentPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AI Studio",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Model Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.accentPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⚡ $activeModelName",
                        color = theme.accentPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Back to Keyboard button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.keyFunctionalBgColor)
                    .clickable { onIntent(KeyboardIntent.CloseAIMenu) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Return to Keyboard",
                    tint = theme.keyTextColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Keyboard",
                    color = theme.keyTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. Draft Preview Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.keyBgColor.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (hasDraft) {
                val previewSnippet = uiState.currentDraft.trim().replace("\n", " ")
                Text(
                    text = "Selected text: \"${previewSnippet.take(48)}${if (previewSnippet.length > 48) "..." else ""}\"",
                    fontSize = 11.sp,
                    color = theme.keySecondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "💡 Type or select text first, or tap an action below to draft",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. Category Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                val count = if (cat == "All") uiState.userPrompts.size else uiState.userPrompts.count { it.category.equals(cat, ignoreCase = true) }
                val label = if (count > 0) "$cat ($count)" else cat

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) theme.accentPrimary else theme.keyBgColor)
                        .border(
                            1.dp,
                            if (isSelected) theme.accentPrimary else theme.keyFunctionalBgColor,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) (if (theme.isDark) Color.Black else Color.White) else theme.keySecondaryTextColor
                    )
                }
            }
        }

        // 4. Prompts 2-Column Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredPrompts, key = { it.id }) { prompt ->
                PromptCard(
                    prompt = prompt,
                    theme = theme,
                    onClick = { onIntent(KeyboardIntent.TriggerAIAction(prompt)) }
                )
            }
        }

        // 5. Custom Prompt Quick Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.keyBgColor)
                .border(1.dp, theme.keyFunctionalBgColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = customPromptInput,
                onValueChange = { customPromptInput = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(theme.accentPrimary),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                decorationBox = { innerTextField ->
                    if (customPromptInput.isEmpty()) {
                        Text(
                            text = "💬 Or type custom prompt (e.g. 'Make it rhyme')...",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (customPromptInput.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(theme.accentPrimary)
                        .clickable {
                            onIntent(KeyboardIntent.TriggerCustomPrompt(customPromptInput.trim()))
                            customPromptInput = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Custom Prompt",
                        tint = if (theme.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    prompt: UserPromptEntity,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    val emoji = getPromptEmoji(prompt.title)
    val subtitle = getPromptSubtitle(prompt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.keyBgColor)
            .border(1.dp, theme.keyFunctionalBgColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = emoji,
            fontSize = 18.sp
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prompt.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun getPromptEmoji(title: String): String {
    val t = title.lowercase()
    return when {
        t.contains("grammar") -> "✍️"
        t.contains("executive") -> "💼"
        t.contains("punchy") -> "✂️"
        t.contains("bullet") -> "📌"
        t.contains("email") -> "📧"
        t.contains("tl;dr") || t.contains("short") -> "⚡"
        t.contains("roast") -> "💀"
        t.contains("gen-z") || t.contains("slang") -> "🔥"
        t.contains("gigachad") -> "🗿"
        t.contains("dramatic") -> "🎭"
        t.contains("5") || t.contains("eli5") -> "👶"
        t.contains("pirate") -> "🏴‍☠️"
        t.contains("polite") || t.contains("soft") -> "🤝"
        t.contains("academic") -> "🧐"
        t.contains("cyberpunk") -> "🥷"
        t.contains("poetic") -> "📜"
        t.contains("chill") || t.contains("casual") -> "😎"
        t.contains("spanish") -> "🇪🇸"
        t.contains("french") -> "🇫🇷"
        t.contains("german") -> "🇩🇪"
        t.contains("japanese") -> "🇯🇵"
        else -> "✨"
    }
}

private fun getPromptSubtitle(prompt: UserPromptEntity): String {
    val t = prompt.title.lowercase()
    return when {
        t.contains("grammar") -> "Polish grammar & rhythm"
        t.contains("executive") -> "C-suite business tone"
        t.contains("punchy") -> "Tight, zero filler"
        t.contains("bullet") -> "Extract concrete to-dos"
        t.contains("email") -> "Courteous context reply"
        t.contains("tl;dr") || t.contains("short") -> "Under 10 words"
        t.contains("roast") -> "Savage witty punchlines"
        t.contains("gen-z") || t.contains("slang") -> "Brainrot slang (no cap, fr)"
        t.contains("gigachad") -> "Hyper-confident stoic"
        t.contains("dramatic") -> "Victorian tragedy style"
        t.contains("5") || t.contains("eli5") -> "Playful 5yo analogies"
        t.contains("pirate") -> "Swashbuckling dialect"
        t.contains("polite") || t.contains("soft") -> "Gentle & considerate"
        t.contains("academic") -> "Elevate formal discourse"
        t.contains("cyberpunk") -> "Terminal net vernacular"
        t.contains("poetic") -> "Transform into rhyme"
        t.contains("chill") || t.contains("casual") -> "Relaxed texting vibe"
        t.contains("spanish") -> "Translate to Spanish"
        t.contains("french") -> "Translate to French"
        t.contains("german") -> "Translate to German"
        t.contains("japanese") -> "Translate to Japanese"
        else -> prompt.category
    }
}
