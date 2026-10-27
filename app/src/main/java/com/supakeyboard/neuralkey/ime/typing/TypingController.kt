package com.supakeyboard.neuralkey.ime.typing

/** Small editor boundary keeps corrections testable without an Android service. */
interface TypingEditor {
    fun beforeCursor(limit: Int): String
    fun afterCursor(limit: Int): String
    fun hasSelection(): Boolean
    fun commit(text: String)
    fun deleteBefore(length: Int)
    fun deleteSelection()
    fun batch(block: () -> Unit)
}

class TypingController(private val engine: () -> SuggestionEngine?) {
    private data class Undo(
        val original: String,
        val replacement: String,
        val before: String,
        val after: String,
        val learnedWord: String? = null,
        val previousWord: String? = null
    )
    private var undo: Undo? = null
    private var keepOriginalOnce: String? = null

    fun currentWord(editor: TypingEditor): String? {
        if (editor.hasSelection()) return null
        val after = editor.afterCursor(1).firstOrNull()
        if (after != null && (after.isLetterOrDigit() || after in "_@'’./")) return null
        // Reject whole URL/email/code tokens instead of correcting a fragment within them.
        val token = editor.beforeCursor(128).takeLastWhile { !it.isWhitespace() }
            .trimStart('(', '[', '{', '"', '“')
        return token.takeIf { SuggestionEngine.isWord(it) }
    }

    /**
     * Extracts the completed word immediately preceding the current word (or preceding trailing spaces).
     * Used for contextual bigram next-word prediction and Key Memory learning.
     */
    fun previousWord(editor: TypingEditor): String? {
        if (editor.hasSelection()) return null
        val before = editor.beforeCursor(128)
        val withoutCurrent = if (before.lastOrNull()?.isWhitespace() == true) {
            before.trimEnd()
        } else {
            before.dropLastWhile { !it.isWhitespace() }.trimEnd()
        }
        if (withoutCurrent.isEmpty()) return null
        val rawPrev = withoutCurrent.takeLastWhile { !it.isWhitespace() }
            .trim('(', ')', '[', ']', '{', '}', '"', '“', '”', ',', '.', ';', ':')
        return rawPrev.takeIf { SuggestionEngine.isWord(it) }
    }

    fun type(editor: TypingEditor, text: String, autoCorrect: Boolean, learnMemory: Boolean = true) {
        undo = null
        val word = if (text == " ") currentWord(editor) else null
        val prev = if (text == " ") previousWord(editor) else null
        val corrected = if (autoCorrect && word != null) {
            word.takeUnless { it == keepOriginalOnce }?.let { engine()?.correction(it) }
        } else null
        keepOriginalOnce = null
        if (word != null && corrected != null && word != corrected) {
            editor.batch {
                editor.deleteBefore(word.length)
                editor.commit(corrected + text)
            }
            if (learnMemory) engine()?.recordWord(corrected, prev)
            undo = Undo(word, corrected + text, editor.beforeCursor(128), editor.afterCursor(32), corrected, prev)
        } else {
            editor.commit(text)
            if (learnMemory && word != null && text == " ") {
                engine()?.recordWord(word, prev)
            }
        }
    }

    fun choose(editor: TypingEditor, suggestion: String, candidates: List<String>, learnMemory: Boolean = true): Boolean {
        if (editor.hasSelection() || suggestion !in candidates) return false
        val word = currentWord(editor)
        val prev = previousWord(editor)
        if (word != null) {
            undo = null
            editor.batch {
                editor.deleteBefore(word.length)
                editor.commit(suggestion + " ")
            }
            if (learnMemory) engine()?.recordWord(suggestion, prev)
            return true
        }
        // Support selecting a slash command candidate when typing '/'
        val rawToken = editor.beforeCursor(32).takeLastWhile { !it.isWhitespace() }
        if (rawToken.startsWith("/") && suggestion.startsWith("/")) {
            undo = null
            editor.batch {
                editor.deleteBefore(rawToken.length)
                editor.commit(suggestion + " ")
            }
            return true
        }
        // Support selecting a next-word prediction directly after a space
        val after = editor.afterCursor(1).firstOrNull()
        val atWordBoundaryAfterSpace = editor.beforeCursor(1) == " " &&
            prev != null && (after == null || after.isWhitespace())
        if (atWordBoundaryAfterSpace) {
            undo = null
            editor.commit(suggestion + " ")
            if (learnMemory) engine()?.recordWord(suggestion, prev)
            return true
        }
        // Support selecting a sentence-starter suggestion in an empty field or after a line break
        val before = editor.beforeCursor(8)
        val atFieldStart = prev == null && (after == null || after.isWhitespace()) &&
            (before.isEmpty() || (before.isNotEmpty() && before.all { it.isWhitespace() }))
        if (atFieldStart) {
            undo = null
            editor.commit(suggestion + " ")
            if (learnMemory) engine()?.recordWord(suggestion, null)
            return true
        }
        return false
    }

    /**
     * Commits the top decoded word for a continuous Neural Trail Glide gesture
     * and returns the decoded candidate list.
     */
    fun glide(editor: TypingEditor, rawPath: String, learnMemory: Boolean = true): List<String> {
        val prev = previousWord(editor)
        val candidates = engine()?.decodeGlidePath(rawPath, prev).orEmpty()
        val best = candidates.firstOrNull() ?: return emptyList()
        undo = null
        val beforeChar = editor.beforeCursor(1).firstOrNull()
        val needsLeadingSpace = beforeChar != null && !beforeChar.isWhitespace() && beforeChar !in "([{\"'“"
        val toCommit = (if (needsLeadingSpace) " " else "") + best + " "
        editor.commit(toCommit)
        if (learnMemory) engine()?.recordWord(best, prev)
        return candidates
    }

    fun backspace(editor: TypingEditor) {
        val previous = undo
        undo = null
        if (editor.hasSelection()) {
            editor.deleteSelection()
        } else if (previous != null && editor.beforeCursor(128) == previous.before &&
            editor.afterCursor(32) == previous.after) {
            editor.batch {
                editor.deleteBefore(previous.replacement.length)
                editor.commit(previous.original)
            }
            previous.learnedWord?.let { engine()?.unlearnWord(it, previous.previousWord) }
            engine()?.recordWord(previous.original, previous.previousWord, force = true)
            keepOriginalOnce = previous.original
        } else {
            val before = editor.beforeCursor(2)
            // deleteSurroundingText uses UTF-16 units; don't split an emoji surrogate pair.
            val count = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
            editor.deleteBefore(count)
        }
    }

    fun selectionChanged(editor: TypingEditor) {
        undo?.let {
            if (editor.hasSelection() || editor.beforeCursor(128) != it.before || editor.afterCursor(32) != it.after) reset()
        }
    }

    fun reset() { undo = null; keepOriginalOnce = null }
}
