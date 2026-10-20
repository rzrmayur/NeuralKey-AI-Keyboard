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
    private data class Undo(val original: String, val replacement: String, val before: String, val after: String)
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

    fun type(editor: TypingEditor, text: String, autoCorrect: Boolean) {
        undo = null
        val word = if (autoCorrect && text == " ") currentWord(editor) else null
        val corrected = word?.takeUnless { it == keepOriginalOnce }?.let { engine()?.correction(it) }
        keepOriginalOnce = null
        if (word != null && corrected != null && word != corrected) {
            editor.batch {
                editor.deleteBefore(word.length)
                editor.commit(corrected + text)
            }
            undo = Undo(word, corrected + text, editor.beforeCursor(128), editor.afterCursor(32))
        } else {
            editor.commit(text)
        }
    }

    fun choose(editor: TypingEditor, suggestion: String, candidates: List<String>): Boolean {
        val word = currentWord(editor) ?: return false
        if (suggestion !in candidates) return false
        undo = null
        editor.batch {
            editor.deleteBefore(word.length)
            editor.commit(suggestion + " ")
        }
        return true
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
