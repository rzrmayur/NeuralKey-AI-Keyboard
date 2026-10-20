package com.supakeyboard.neuralkey.ime.typing

import org.junit.Assert.*
import org.junit.Test

class TypingControllerTest {
    private val engine = SuggestionEngine(listOf("hello", "help", "world", "keyboard", "meeting", "meetings"))
    private val controller = TypingController { engine }

    @Test fun correctsOnSpaceAndBackspaceRestoresOriginal() {
        val editor = Editor("Please recieve")
        controller.type(editor, " ", true)
        assertEquals("Please receive ", editor.text)
        controller.backspace(editor)
        assertEquals("Please recieve", editor.text)
    }
    @Test fun correctionKeepsCapitalization() {
        val editor = Editor("Thsi")
        controller.type(editor, " ", true)
        assertEquals("This ", editor.text)
    }
    @Test fun disabledCorrectionCommitsLiteralText() {
        val editor = Editor("teh")
        controller.type(editor, " ", false)
        assertEquals("teh ", editor.text)
    }
    @Test fun normalTypingAfterCorrectionCancelsUndo() {
        val editor = Editor("teh")
        controller.type(editor, " ", true)
        controller.type(editor, "x", true)
        controller.backspace(editor)
        assertEquals("the ", editor.text)
    }
    @Test fun cursorMovementDoesNotUndoOrReplaceAnotherWord() {
        val editor = Editor("hello teh")
        controller.type(editor, " ", true)
        editor.cursor = 3
        controller.selectionChanged(editor)
        controller.backspace(editor)
        assertEquals("helo the ", editor.text)
    }
    @Test fun suggestionsReplaceOnlyCurrentWordAndAddSpace() {
        val editor = Editor("say hell")
        assertTrue(controller.choose(editor, "hello", listOf("hell", "hello", "help")))
        assertEquals("say hello ", editor.text)
    }
    @Test fun cannotApplySuggestionInsideWordOrToSelectedText() {
        val editor = Editor("hello")
        editor.cursor = 3
        assertNull(controller.currentWord(editor))
        assertFalse(controller.choose(editor, "help", listOf("hello", "help")))
        editor.cursor = 5
        editor.selectionStart = 0
        assertFalse(controller.choose(editor, "help", listOf("hello", "help")))
        assertEquals("hello", editor.text)
    }
    @Test fun selectedTextIsDeletedInsteadOfTextBeforeIt() {
        val editor = Editor("hello world")
        editor.selectionStart = 6
        controller.backspace(editor)
        assertEquals("hello ", editor.text)
    }
    @Test fun deletesEmojiWithoutLeavingHalfSurrogate() {
        val editor = Editor("hello 😀")
        controller.backspace(editor)
        assertEquals("hello ", editor.text)
    }
    @Test fun emailUrlAndIdentifiersRemainLiteral() {
        for (value in listOf("user@teh", "https://teh", "www.teh", "user_teh", "a1teh", "Slovák")) {
            val editor = Editor(value)
            controller.type(editor, " ", true)
            assertEquals(value + " ", editor.text)
        }
    }
    @Test fun focusResetPreventsUndoLeakingToAnotherEditor() {
        val editor = Editor("teh")
        controller.type(editor, " ", true)
        controller.reset()
        controller.backspace(editor)
        assertEquals("the", editor.text)
    }
    @Test fun rejectsCandidateNotOfferedByKeyboard() {
        val editor = Editor("hell")
        assertFalse(controller.choose(editor, "unrelated", engine.suggestions("hell")))
        assertEquals("hell", editor.text)
    }

    private class Editor(var text: String) : TypingEditor {
        var cursor = text.length
        var selectionStart: Int? = null
        override fun beforeCursor(limit: Int) = text.take(cursor).takeLast(limit)
        override fun afterCursor(limit: Int) = text.drop(cursor).take(limit)
        // Only a selection explicitly moved behind the cursor counts in this fake.
        override fun hasSelection() = selectionStart != null && selectionStart != cursor
        override fun commit(text: String) {
            val start = if (hasSelection()) selectionStart!! else cursor
            this.text = this.text.take(start) + text + this.text.drop(cursor)
            cursor = start + text.length
            selectionStart = null
        }
        override fun deleteBefore(length: Int) {
            val start = (cursor - length).coerceAtLeast(0)
            text = text.removeRange(start, cursor)
            cursor = start
            selectionStart = null
        }
        override fun deleteSelection() { commit("") }
        override fun batch(block: () -> Unit) { block() }
    }
}
