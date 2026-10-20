package com.supakeyboard.neuralkey.data.network

/**
 * Constructs strict system prompts that eliminate conversational filler
 * and tailor instructions to the active host application.
 */
object SystemPromptBuilder {

    private const val STRICT_CORE_RULES =
        "You are an integrated AI assistant inside a mobile keyboard (NeuralKey AI).\n" +
        "CRITICAL INSTRUCTIONS:\n" +
        "1. Return ONLY the final transformed or generated text.\n" +
        "2. Do NOT output conversational filler, introductory remarks, greetings, or conclusions.\n" +
        "3. Do NOT wrap the entire output in quotation marks or code blocks unless explicitly requested.\n" +
        "4. Preserve the core meaning, intention, and any technical terms or links from the input text.\n" +
        "5. The user will directly insert your output into their text field with one tap."

    enum class HostAppCategory(val description: String, val recommendedTone: String) {
        MESSAGING("Instant Messaging (e.g. WhatsApp, Telegram, Signal)", "conversational, concise, and natural"),
        PROFESSIONAL("Work & Collaboration (e.g. Slack, Teams, LinkedIn)", "professional, polished, and courteous"),
        EMAIL("Email Client (e.g. Gmail, Outlook)", "formal, structured, and clear"),
        SOCIAL_MEDIA("Social Media (e.g. X/Twitter, Instagram, Reddit)", "punchy, engaging, and expressive"),
        BROWSER_SEARCH("Search / Navigation", "concise and direct"),
        GENERAL("General Application", "balanced, clear, and direct")
    }

    fun detectAppCategory(packageName: String?): HostAppCategory {
        if (packageName == null) return HostAppCategory.GENERAL
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") ||
            pkg.contains("viber") || pkg.contains("messenger") || pkg.contains("line") ||
            pkg.contains("wechat") || pkg.contains("kakao") -> HostAppCategory.MESSAGING

            pkg.contains("slack") || pkg.contains("teams") || pkg.contains("linkedin") ||
            pkg.contains("zoom") || pkg.contains("skype") -> HostAppCategory.PROFESSIONAL

            pkg.contains("gm") || pkg.contains("mail") || pkg.contains("outlook") ||
            pkg.contains("k9") || pkg.contains("spark") || pkg.contains("thunderbird") -> HostAppCategory.EMAIL

            pkg.contains("twitter") || pkg.contains("x.android") || pkg.contains("instagram") ||
            pkg.contains("facebook") || pkg.contains("reddit") || pkg.contains("threads") ||
            pkg.contains("tiktok") -> HostAppCategory.SOCIAL_MEDIA

            pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") ||
            pkg.contains("opera") || pkg.contains("edge") -> HostAppCategory.BROWSER_SEARCH

            else -> HostAppCategory.GENERAL
        }
    }

    fun buildSystemPrompt(
        actionInstruction: String,
        targetPackageName: String? = null,
        customPromptOverride: String? = null
    ): String {
        val appCategory = detectAppCategory(targetPackageName)
        val sb = StringBuilder()

        sb.append(STRICT_CORE_RULES).append("\n\n")

        sb.append("TARGET CONTEXT:\n")
        sb.append("- Current App Category: ").append(appCategory.description).append("\n")
        sb.append("- Preferred App Tone: ").append(appCategory.recommendedTone).append("\n\n")

        sb.append("TASK SPECIFICATION:\n")
        sb.append(actionInstruction).append("\n")

        if (!customPromptOverride.isNullOrBlank()) {
            sb.append("\nADDITIONAL USER CONSTRAINTS:\n")
            sb.append(customPromptOverride.trim()).append("\n")
        }

        return sb.toString().trim()
    }
}
