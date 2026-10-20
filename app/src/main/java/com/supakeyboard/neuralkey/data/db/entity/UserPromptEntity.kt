package com.supakeyboard.neuralkey.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_prompts")
@Immutable
data class UserPromptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val promptInstruction: String,
    val iconKey: String,
    val category: String = "Core",
    val orderIndex: Int,
    val isBuiltIn: Boolean = false
) {
    companion object {
        fun defaultPrompts(): List<UserPromptEntity> = listOf(
            // --- Core & Productivity ---
            UserPromptEntity(
                id = 1,
                title = "Polish",
                promptInstruction = "Polish the following draft. Elevate phrasing, enhance flow, and improve clarity while keeping the exact meaning and tone intact.",
                iconKey = "auto_fix_high",
                category = "Productivity",
                orderIndex = 0,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 2,
                title = "Fix Grammar",
                promptInstruction = "Correct all spelling, grammatical, punctuation, and capitalization errors in the following text. Do not alter the intended meaning.",
                iconKey = "spellcheck",
                category = "Productivity",
                orderIndex = 1,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 3,
                title = "Professional",
                promptInstruction = "Rewrite the following draft into a courteous, polished, and professional tone suitable for a workplace, client, or business communication.",
                iconKey = "work",
                category = "Productivity",
                orderIndex = 2,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 4,
                title = "TL;DR Crisp",
                promptInstruction = "Condense the following draft into a punchy, ultra-concise summary of 1 or 2 sentences max. Keep only the absolute core message.",
                iconKey = "short_text",
                category = "Productivity",
                orderIndex = 3,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 5,
                title = "Diplomatic",
                promptInstruction = "Soften the following message to be exceptionally polite, constructive, and conflict-diffusing, while preserving the core request or boundary.",
                iconKey = "handshake",
                category = "Productivity",
                orderIndex = 4,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 6,
                title = "Bullet Points",
                promptInstruction = "Structure the following draft into clean, formatted bullet points with clear takeaways or action items.",
                iconKey = "format_list_bulleted",
                category = "Productivity",
                orderIndex = 5,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 7,
                title = "Explain Like I'm 5",
                promptInstruction = "Explain and rewrite the following text in extremely simple, friendly, everyday words that a 5-year-old could easily understand.",
                iconKey = "child_care",
                category = "Productivity",
                orderIndex = 6,
                isBuiltIn = true
            ),

            // --- Funny, Sarcastic & Creative ---
            UserPromptEntity(
                id = 8,
                title = "Playful Roast",
                promptInstruction = "Rewrite this message with witty, sharp, comedic banter and playful roast energy. Keep it hilarious, clever, and entertaining without being genuinely malicious.",
                iconKey = "local_fire_department",
                category = "Humor & Vibes",
                orderIndex = 7,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 9,
                title = "Sarcastic & Dry",
                promptInstruction = "Rewrite the following draft dripping with clever, dry, deadpan sarcasm and subtle irony while still communicating the essential point.",
                iconKey = "sentiment_very_dissatisfied",
                category = "Humor & Vibes",
                orderIndex = 8,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 10,
                title = "Gen-Z Slang",
                promptInstruction = "Rewrite the following text into modern Gen-Z / TikTok internet slang (e.g. no cap, fr fr, high key, main character, bet, vibes) with matching emojis. Keep it natural and funny.",
                iconKey = "bolt",
                category = "Humor & Vibes",
                orderIndex = 9,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 11,
                title = "Shakespearean",
                promptInstruction = "Translate the following message into dramatic, grand Elizabethan English worthy of William Shakespeare, complete with poetic rhetoric and theatrical flair.",
                iconKey = "theater_comedy",
                category = "Humor & Vibes",
                orderIndex = 10,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 12,
                title = "Pirate Talk",
                promptInstruction = "Ahoy matey! Rewrite the draft in authentic, colorful, swashbuckling pirate vernacular with nautical terms and hearty enthusiasm.",
                iconKey = "sailing",
                category = "Humor & Vibes",
                orderIndex = 11,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 13,
                title = "Cyberpunk 2077",
                promptInstruction = "Rewrite the following text as a gritty, neon-soaked cyberpunk comms transmission from an underworld netrunner in a dystopian megacity.",
                iconKey = "memory",
                category = "Humor & Vibes",
                orderIndex = 12,
                isBuiltIn = true
            ),

            // --- Tone & Social Styles ---
            UserPromptEntity(
                id = 14,
                title = "Casual & Chill",
                promptInstruction = "Rewrite the following draft into a relaxed, friendly, effortless conversational chat tone for texting friends or informal DMs.",
                iconKey = "sentiment_satisfied",
                category = "Styles",
                orderIndex = 13,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 15,
                title = "Charming & Smooth",
                promptInstruction = "Rewrite the draft with effortless charm, subtle wit, warm charisma, and engaging banter.",
                iconKey = "favorite",
                category = "Styles",
                orderIndex = 14,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 16,
                title = "Maximum Hype",
                promptInstruction = "Inject explosive energy, massive excitement, bold motivational drive, and celebratory vibes into the text! Let's GO!",
                iconKey = "celebration",
                category = "Styles",
                orderIndex = 15,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 17,
                title = "Stoic / Zen",
                promptInstruction = "Rewrite the draft from a calm, grounded, Stoic philosophy perspective: rational, unbothered, composed, and focused only on what matters.",
                iconKey = "spa",
                category = "Styles",
                orderIndex = 16,
                isBuiltIn = true
            ),

            // --- Multi-Language Translation ---
            UserPromptEntity(
                id = 18,
                title = "Translate to English",
                promptInstruction = "Translate the following text into fluent, natural, idiomatic English.",
                iconKey = "translate",
                category = "Translation",
                orderIndex = 17,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 19,
                title = "Translate to Spanish",
                promptInstruction = "Translate the following text into natural, fluent Spanish (Español).",
                iconKey = "language",
                category = "Translation",
                orderIndex = 18,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 20,
                title = "Translate to French",
                promptInstruction = "Translate the following text into elegant, natural French (Français).",
                iconKey = "language",
                category = "Translation",
                orderIndex = 19,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 21,
                title = "Translate to Japanese",
                promptInstruction = "Translate the following text into polite, natural Japanese (日本語).",
                iconKey = "language",
                category = "Translation",
                orderIndex = 20,
                isBuiltIn = true
            ),

            // --- Email & Drafts Suite ---
            UserPromptEntity(
                id = 22,
                title = "Reply: Confirm & Accept",
                promptInstruction = "Transform the following notes or draft into a warm, structured professional email reply that confirms receipt, accepts the proposal or meeting, and clearly states next steps.",
                iconKey = "mark_email_read",
                category = "Email & Drafts",
                orderIndex = 21,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 23,
                title = "Reply: Polite Decline",
                promptInstruction = "Draft a gracious, diplomatic email reply that politely declines the request, invitation, or proposal while preserving goodwill and professional warmth.",
                iconKey = "unsubscribe",
                category = "Email & Drafts",
                orderIndex = 22,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 24,
                title = "Cold Outreach Email",
                promptInstruction = "Turn the following notes into a concise, high-converting executive cold outreach email with a compelling hook, clear value proposition, and a low-friction call to action.",
                iconKey = "forward_to_inbox",
                category = "Email & Drafts",
                orderIndex = 23,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 25,
                title = "Follow-Up Nudge",
                promptInstruction = "Write a courteous, action-oriented follow-up email checking in on a previous thread or pending deliverable without sounding pushy.",
                iconKey = "schedule_send",
                category = "Email & Drafts",
                orderIndex = 24,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 26,
                title = "Meeting Recap & Actions",
                promptInstruction = "Transform the following rough meeting notes into a structured executive recap email with: 1) Summary, 2) Key Decisions, and 3) Action Items with Owners.",
                iconKey = "fact_check",
                category = "Email & Drafts",
                orderIndex = 25,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 27,
                title = "Subject Line Generator",
                promptInstruction = "Generate 3 high-impact, click-worthy email subject lines (Executive, Urgent/Direct, and Conversational) plus a 1-line preview text for the following email draft.",
                iconKey = "title",
                category = "Email & Drafts",
                orderIndex = 26,
                isBuiltIn = true
            ),

            // --- Smart Writing & Engineering ---
            UserPromptEntity(
                id = 28,
                title = "Expand & Elaborate",
                promptInstruction = "Expand the following shorthand notes or brief thought into a well-structured, articulate, and persuasive paragraph with smooth transitions.",
                iconKey = "open_in_full",
                category = "Smart Writing",
                orderIndex = 27,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 29,
                title = "Brainstorm 5 Angles",
                promptInstruction = "Based on the following topic or draft, generate 5 distinct, creative angles, hooks, or strategic approaches in numbered bullet points.",
                iconKey = "lightbulb",
                category = "Smart Writing",
                orderIndex = 28,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 30,
                title = "Pros, Cons & Risks",
                promptInstruction = "Analyze the proposal or idea in the text and output a structured breakdown of Pros, Cons, and Potential Blind Spots / Risks.",
                iconKey = "balance",
                category = "Smart Writing",
                orderIndex = 29,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 31,
                title = "Bug / Jira Spec",
                promptInstruction = "Convert the following rough issue or feature description into a structured software engineering ticket with Summary, Context/Steps, Expected Outcome, and Acceptance Criteria.",
                iconKey = "bug_report",
                category = "Smart Writing",
                orderIndex = 30,
                isBuiltIn = true
            ),
            UserPromptEntity(
                id = 32,
                title = "Translate to German",
                promptInstruction = "Translate the following text into natural, fluent, idiomatic German (Deutsch).",
                iconKey = "language",
                category = "Translation",
                orderIndex = 31,
                isBuiltIn = true
            )
        )
    }
}
