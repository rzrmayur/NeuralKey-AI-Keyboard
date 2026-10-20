package com.supakeyboard.neuralkey.ime.persona

import androidx.compose.runtime.Immutable

@Immutable
data class AppPersona(
    val id: String,
    val badgeLabel: String,
    val emoji: String,
    val description: String,
    val systemToneDirective: String
)

/**
 * Automatically detects the active host application's domain and adapts AI tone,
 * formatting, and Ghost-Text sentence completion behavior.
 */
object AppPersonaEngine {

    val EMAIL_EXECUTIVE = AppPersona(
        id = "EMAIL_EXECUTIVE",
        badgeLabel = "Email Mode",
        emoji = "✉️",
        description = "Executive clarity, structured paragraphs, and polite sign-offs",
        systemToneDirective = "Format for an email client: use crisp executive clarity, structured paragraphs, and a natural sign-off when appropriate."
    )

    val INSTANT_MESSENGER = AppPersona(
        id = "INSTANT_MESSENGER",
        badgeLabel = "Chat Mode",
        emoji = "💬",
        description = "Natural conversational warmth, concise and human",
        systemToneDirective = "Format for instant messaging: keep it natural, warm, concise, and conversational without stiff corporate jargon."
    )

    val WORKSPACE_PRO = AppPersona(
        id = "WORKSPACE_PRO",
        badgeLabel = "Workspace Mode",
        emoji = "💼",
        description = "Action-oriented team updates, clear owners and blockers",
        systemToneDirective = "Format for team collaboration (Slack/Teams/Notion): be direct, action-oriented, and scannable."
    )

    val DEVELOPER_CODE = AppPersona(
        id = "DEVELOPER_CODE",
        badgeLabel = "Dev Mode",
        emoji = "💻",
        description = "Technical precision, Markdown/code awareness, PR & commit style",
        systemToneDirective = "Format for software engineering (GitHub/IDE/Terminal): use technical precision, clean Markdown or code syntax, and zero fluff."
    )

    val SOCIAL_CREATOR = AppPersona(
        id = "SOCIAL_CREATOR",
        badgeLabel = "Social Mode",
        emoji = "🔥",
        description = "Punchy, high-engagement hooks and expressive flow",
        systemToneDirective = "Format for social media: make it punchy, engaging, authentic, and concise."
    )

    val UNIVERSAL_NEURAL = AppPersona(
        id = "UNIVERSAL_NEURAL",
        badgeLabel = "Universal AI",
        emoji = "🧠",
        description = "Balanced, adaptive intelligence across any app",
        systemToneDirective = "Maintain a clear, natural, and well-structured tone matching the user's draft."
    )

    val allPersonas = listOf(
        EMAIL_EXECUTIVE,
        INSTANT_MESSENGER,
        WORKSPACE_PRO,
        DEVELOPER_CODE,
        SOCIAL_CREATOR,
        UNIVERSAL_NEURAL
    )

    fun resolvePersona(packageName: String?): AppPersona {
        if (packageName.isNullOrBlank()) return UNIVERSAL_NEURAL
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("gm") || pkg.contains("mail") || pkg.contains("outlook") ||
                pkg.contains("proton") || pkg.contains("spark") || pkg.contains("thunderbird") ||
                pkg.contains("k9") -> EMAIL_EXECUTIVE

            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") ||
                pkg.contains("mms") || pkg.contains("messaging") || pkg.contains("messenger") ||
                pkg.contains("viber") || pkg.contains("naver.line") -> INSTANT_MESSENGER

            pkg.contains("slack") || pkg.contains("teams") || pkg.contains("discord") ||
                pkg.contains("notion") || pkg.contains("linear") ||
                pkg.contains("asana") || pkg.contains("jira") -> WORKSPACE_PRO

            pkg.contains("github") || pkg.contains("gitlab") || pkg.contains("termux") ||
                pkg.contains("acode") || pkg.contains("stackoverflow") || pkg.contains("replit") ||
                pkg.contains("code") -> DEVELOPER_CODE

            pkg.contains("twitter") || pkg.contains("x.android") || pkg.contains("instagram") ||
                pkg.contains("reddit") || pkg.contains("threads") || pkg.contains("tiktok") ||
                pkg.contains("youtube") || pkg.contains("linkedin") -> SOCIAL_CREATOR

            else -> UNIVERSAL_NEURAL
        }
    }

    /**
     * Builds the instruction for Ghost-Text Inline AI Completion (completing the user's unfinished thought
     * and returning the full completed text ready for 1-tap replacement).
     */
    fun buildGhostCompletionInstruction(packageName: String?): String {
        val persona = resolvePersona(packageName)
        return "Seamless Ghost-Text Autocomplete (${persona.badgeLabel}): " +
            "Take the user's unfinished sentence or draft and naturally complete the rest of the thought or paragraph. " +
            "Return the ENTIRE text (the user's existing text seamlessly followed by your natural continuation). " +
            persona.systemToneDirective
    }
}
