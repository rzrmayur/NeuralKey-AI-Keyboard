package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
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
import com.supakeyboard.neuralkey.data.network.SystemPromptBuilder
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme

/**
 * Intuitive, full-keyboard replacement panel for selecting AI action prompts.
 * Automatically prioritizes Email & Drafts when an email client is detected.
 */
@Composable
fun AIPromptSelectionPanel(
    uiState: KeyboardUiState,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val hostCategory = remember(uiState.targetPackageName) {
        SystemPromptBuilder.detectAppCategory(uiState.targetPackageName)
    }
    val initialCategory = remember(hostCategory) {
        if (hostCategory == SystemPromptBuilder.HostAppCategory.EMAIL) "Email & Drafts" else "All"
    }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var customPromptInput by remember { mutableStateOf("") }

    val categories = listOf(
        "All",
        "Email & Drafts",
        "Productivity",
        "Smart Writing",
        "Humor & Vibes",
        "Styles",
        "Translation"
    )

    val allPrompts = remember(uiState.userPrompts) {
        uiState.userPrompts.ifEmpty { UserPromptEntity.defaultPrompts() }
    }

    val filteredPrompts = remember(selectedCategory, allPrompts) {
        if (selectedCategory == "All") {
            allPrompts
        } else {
            allPrompts.filter { it.category.equals(selectedCategory, ignoreCase = true) }
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

                if (hostCategory == SystemPromptBuilder.HostAppCategory.EMAIL) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✉️ Email Mode",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                    text = "💡 Type notes/draft first, then tap an Email or AI tool below",
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
                val count = if (cat == "All") allPrompts.size else allPrompts.count { it.category.equals(cat, ignoreCase = true) }
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
                            text = "💬 Or type custom instruction (e.g. 'Reply asking for Tuesday at 2pm')...",
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
        t.contains("confirm") || t.contains("accept") -> "✅"
        t.contains("decline") -> "🛡️"
        t.contains("cold") || t.contains("outreach") -> "🚀"
        t.contains("follow-up") || t.contains("nudge") -> "⏰"
        t.contains("recap") || t.contains("meeting") -> "📋"
        t.contains("subject") -> "🎯"
        t.contains("expand") || t.contains("elaborate") -> "🔍"
        t.contains("brainstorm") || t.contains("angles") -> "💡"
        t.contains("pros") || t.contains("risks") -> "⚖️"
        t.contains("bug") || t.contains("jira") -> "💻"
        t.contains("polish") -> "✨"
        t.contains("grammar") -> "✍️"
        t.contains("professional") || t.contains("executive") -> "💼"
        t.contains("punchy") -> "✂️"
        t.contains("bullet") -> "📌"
        t.contains("email") -> "📧"
        t.contains("tl;dr") || t.contains("short") -> "⚡"
        t.contains("roast") -> "💀"
        t.contains("sarcastic") -> "🙄"
        t.contains("gen-z") || t.contains("slang") -> "🔥"
        t.contains("gigachad") || t.contains("stoic") || t.contains("zen") -> "🗿"
        t.contains("shakespeare") || t.contains("dramatic") -> "🎭"
        t.contains("5") || t.contains("eli5") -> "👶"
        t.contains("pirate") -> "🏴‍☠️"
        t.contains("diplomatic") || t.contains("polite") || t.contains("soft") -> "🤝"
        t.contains("academic") -> "🧐"
        t.contains("cyberpunk") -> "🥷"
        t.contains("poetic") -> "📜"
        t.contains("chill") || t.contains("casual") -> "😎"
        t.contains("charming") -> "💫"
        t.contains("hype") -> "🎉"
        t.contains("english") -> "🇬🇧"
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
        t.contains("confirm") || t.contains("accept") -> "Warm email confirmation"
        t.contains("decline") -> "Diplomatic refusal email"
        t.contains("cold") || t.contains("outreach") -> "High-converting pitch"
        t.contains("follow-up") || t.contains("nudge") -> "Courteous thread check-in"
        t.contains("recap") || t.contains("meeting") -> "Summary + action items"
        t.contains("subject") -> "3 click-worthy subjects"
        t.contains("expand") || t.contains("elaborate") -> "Notes to full paragraph"
        t.contains("brainstorm") || t.contains("angles") -> "5 creative approaches"
        t.contains("pros") || t.contains("risks") -> "Balanced risk analysis"
        t.contains("bug") || t.contains("jira") -> "Structured ticket spec"
        t.contains("polish") -> "Elevate flow & clarity"
        t.contains("grammar") -> "Fix spelling & syntax"
        t.contains("professional") || t.contains("executive") -> "C-suite business tone"
        t.contains("punchy") -> "Tight, zero filler"
        t.contains("bullet") -> "Extract concrete to-dos"
        t.contains("email") -> "Courteous context reply"
        t.contains("tl;dr") || t.contains("short") -> "1-2 sentence summary"
        t.contains("roast") -> "Savage witty punchlines"
        t.contains("sarcastic") -> "Dry deadpan irony"
        t.contains("gen-z") || t.contains("slang") -> "Internet slang (no cap, fr)"
        t.contains("stoic") || t.contains("zen") -> "Calm grounded perspective"
        t.contains("shakespeare") || t.contains("dramatic") -> "Elizabethan theatrics"
        t.contains("5") || t.contains("eli5") -> "Simple everyday words"
        t.contains("pirate") -> "Swashbuckling dialect"
        t.contains("diplomatic") || t.contains("polite") -> "Conflict-diffusing tone"
        t.contains("cyberpunk") -> "Netrunner comms style"
        t.contains("chill") || t.contains("casual") -> "Relaxed texting vibe"
        t.contains("charming") -> "Warm charisma & wit"
        t.contains("hype") -> "High-energy motivation"
        t.contains("english") -> "Translate to English"
        t.contains("spanish") -> "Translate to Spanish"
        t.contains("french") -> "Translate to French"
        t.contains("german") -> "Translate to German"
        t.contains("japanese") -> "Translate to Japanese"
        else -> prompt.category
    }
}
