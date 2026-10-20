package com.supakeyboard.neuralkey.ime.slash

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

data class AISlashInvocation(
    val command: String,
    val cleanedDraft: String,
    val aiInstruction: String
)

/**
 * In-Keyboard Slash Command & Dynamic Snippet Macro Engine.
 * Supports both zero-latency offline macros (/calc, /date, /time, /clip, /shrug)
 * and instant AI slash commands (/email, /fix, /tldr, /polite, /sql, /regex, /code, /translate).
 */
object SlashCommandEngine {

    val ALL_COMMANDS = listOf(
        "/email",
        "/fix",
        "/tldr",
        "/polite",
        "/calc",
        "/sql",
        "/regex",
        "/code",
        "/translate",
        "/slovak",
        "/gif",
        "/sticker",
        "/date",
        "/time",
        "/clip",
        "/shrug"
    )

    private val aiCommandInstructions = mapOf(
        "/email" to "Draft a complete, professional, ready-to-send email based on these notes or instructions.",
        "/fix" to "Fix all spelling, grammar, and punctuation errors while preserving the original meaning and tone.",
        "/tldr" to "Summarize this text into at most 3 ultra-concise, high-signal bullet points.",
        "/tl;dr" to "Summarize this text into at most 3 ultra-concise, high-signal bullet points.",
        "/polite" to "Rewrite this text to sound warm, diplomatic, respectful, and constructive.",
        "/sql" to "Write a clean, production-ready SQL query that accomplishes the described request. Output only the SQL query.",
        "/regex" to "Write an accurate regular expression for the described pattern. Output only the regex pattern.",
        "/code" to "Write a clean, idiomatic, production-ready code snippet for the described task. Output only the code.",
        "/translate" to "Translate this text into fluent, natural English (or into the target language if specified in the text).",
        "/slovak" to "Translate this text into natural, fluent, idiomatic Slovak (Slovenčina).",
        "/sk" to "Translate this text into natural, fluent, idiomatic Slovak (Slovenčina)."
    )

    /**
     * Returns slash command autocomplete candidates when the user's current token starts with '/'.
     */
    fun suggestCommands(currentDraft: String): List<String> {
        val trimmedEnd = currentDraft.trimEnd { false }
        val lastToken = trimmedEnd.substringAfterLast(' ', trimmedEnd).substringAfterLast('\n')
        if (!lastToken.startsWith("/") || lastToken.length > 12) return emptyList()
        val lower = lastToken.lowercase(Locale.ROOT)
        return ALL_COMMANDS.filter { it.startsWith(lower) && it != lower }.take(3)
    }

    /**
     * Evaluates instant offline macros (/calc, /date, /time, /datetime, /clip, /shrug, /tableflip).
     * Returns the expanded full draft if an offline macro was matched, or null otherwise.
     */
    fun evaluateOfflineMacro(
        draft: String,
        latestClipboardText: String? = null,
        now: LocalDateTime = LocalDateTime.now()
    ): String? {
        val trimmed = draft.trimEnd()
        if (trimmed.isEmpty()) return null

        // 1. Check /calc <expression> anywhere at the end of the text
        val calcIdx = trimmed.lastIndexOf("/calc ", ignoreCase = true)
        if (calcIdx != -1) {
            val expr = trimmed.substring(calcIdx + 6).trim()
            if (expr.isNotEmpty()) {
                val value = evaluateMathExpression(expr)
                if (value != null) {
                    val formatted = formatNumber(value)
                    return trimmed.substring(0, calcIdx) + formatted
                }
            }
        }

        // 2. Check trailing token macros
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val dateTimeStr = "$dateStr $timeStr"

        val replacements = mapOf(
            "/date" to dateStr,
            "/time" to timeStr,
            "/datetime" to dateTimeStr,
            "/now" to dateTimeStr,
            "/shrug" to "¯\\_(ツ)_/¯",
            "/tableflip" to "(╯°□°）╯︵ ┻━┻",
            "/clip" to (latestClipboardText?.takeIf { it.isNotBlank() } ?: "")
        )

        for ((macro, replacement) in replacements) {
            if (trimmed.endsWith(macro, ignoreCase = true)) {
                if (macro == "/clip" && replacement.isEmpty()) return null
                val prefix = trimmed.dropLast(macro.length)
                if (prefix.isEmpty() || prefix.last().isWhitespace()) {
                    return prefix + replacement
                }
            }
        }
        return null
    }

    /**
     * Parses AI slash commands either at the start ("/email ask team...") or end ("ask team... /email").
     */
    fun parseAISlashCommand(draft: String): AISlashInvocation? {
        val trimmed = draft.trim()
        if (trimmed.isEmpty()) return null

        // Leading slash command: "/email move meeting to 3pm"
        if (trimmed.startsWith("/")) {
            val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
            val cmd = (if (firstSpace == -1) trimmed else trimmed.substring(0, firstSpace)).lowercase(Locale.ROOT)
            val instruction = aiCommandInstructions[cmd]
            if (instruction != null) {
                val payload = if (firstSpace == -1) "" else trimmed.substring(firstSpace).trim()
                if (payload.isNotEmpty()) {
                    return AISlashInvocation(command = cmd, cleanedDraft = payload, aiInstruction = instruction)
                }
            }
        }

        // Trailing slash command: "move meeting to 3pm /email"
        val lastSpace = trimmed.indexOfLast { it.isWhitespace() }
        if (lastSpace != -1) {
            val trailingToken = trimmed.substring(lastSpace + 1).lowercase(Locale.ROOT)
            val instruction = aiCommandInstructions[trailingToken]
            if (instruction != null) {
                val payload = trimmed.substring(0, lastSpace).trim()
                if (payload.isNotEmpty()) {
                    return AISlashInvocation(command = trailingToken, cleanedDraft = payload, aiInstruction = instruction)
                }
            }
        }
        return null
    }

    /**
     * Safe recursive-descent arithmetic evaluator supporting +, -, *, /, %, ^, and parentheses.
     */
    fun evaluateMathExpression(expression: String): Double? {
        val sanitized = expression.replace(",", "").trim()
        if (sanitized.isEmpty() || sanitized.any { !it.isDigit() && it !in "+-*/%^(). " }) {
            return null
        }
        return runCatching {
            val parser = MathParser(sanitized)
            val result = parser.parseExpression()
            if (parser.pos < parser.input.length || result.isNaN() || result.isInfinite()) null else result
        }.getOrNull()
    }

    private fun formatNumber(value: Double): String {
        if (value == value.toLong().toDouble()) {
            return value.toLong().toString()
        }
        val df = DecimalFormat("#.####", DecimalFormatSymbols(Locale.US))
        return df.format(value)
    }

    private class MathParser(val input: String) {
        var pos = 0

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        private fun eat(charToEat: Char): Boolean {
            skipWhitespace()
            if (pos < input.length && input[pos] == charToEat) {
                pos++
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+') -> x += parseTerm()
                    eat('-') -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*') -> x *= parseFactor()
                    eat('/') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) return Double.NaN
                        x /= divisor
                    }
                    eat('%') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) return Double.NaN
                        x %= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+')) return parseFactor()
            if (eat('-')) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('(')) {
                x = parseExpression()
                if (!eat(')')) throw IllegalArgumentException("Missing closing parenthesis")
            } else {
                skipWhitespace()
                val numStart = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                if (pos == numStart) throw IllegalArgumentException("Unexpected character at $startPos")
                x = input.substring(numStart, pos).toDouble()
            }

            if (eat('^')) {
                x = x.pow(parseFactor())
            }
            skipWhitespace()
            return x
        }
    }
}
