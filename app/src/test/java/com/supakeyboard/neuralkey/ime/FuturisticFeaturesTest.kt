package com.supakeyboard.neuralkey.ime

import com.supakeyboard.neuralkey.data.network.OpenRouterApiClient
import com.supakeyboard.neuralkey.ime.clipboard.ClipboardHistoryManager
import com.supakeyboard.neuralkey.ime.persona.AppPersonaEngine
import com.supakeyboard.neuralkey.ime.slash.SlashCommandEngine
import com.supakeyboard.neuralkey.ime.typing.SuggestionEngine
import com.supakeyboard.neuralkey.ime.typing.TypingController
import com.supakeyboard.neuralkey.ime.typing.TypingEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturisticFeaturesTest {

    @Test
    fun slashCommandCalcEvaluatesMathWithPrecedenceAndParentheses() {
        val r1 = SlashCommandEngine.evaluateOfflineMacro("Total is /calc (12 + 8) * 3.5")
        assertEquals("Total is 70", r1)

        val r2 = SlashCommandEngine.evaluateOfflineMacro("/calc 2^8 + 4")
        assertEquals("260", r2)

        val divZero = SlashCommandEngine.evaluateOfflineMacro("/calc 10 / 0")
        assertNull(divZero)
    }

    @Test
    fun slashCommandMacrosAndAiCommandsResolveProperly() {
        val shrug = SlashCommandEngine.evaluateOfflineMacro("oh well /shrug")
        assertEquals("oh well ¯\\_(ツ)_/¯", shrug)

        val clipEmpty = SlashCommandEngine.evaluateOfflineMacro("/clip", latestClipboardText = null)
        assertNull(clipEmpty)

        val clipPopulated = SlashCommandEngine.evaluateOfflineMacro("Ref: /clip", latestClipboardText = "INC-4920")
        assertEquals("Ref: INC-4920", clipPopulated)

        val aiEmail = SlashCommandEngine.parseAISlashCommand("/email ask Sarah if we can move the design sync to Thursday")
        assertNotNull(aiEmail)
        assertEquals("/email", aiEmail!!.command)
        assertEquals("ask Sarah if we can move the design sync to Thursday", aiEmail.cleanedDraft)
        assertTrue(aiEmail.aiInstruction.contains("email"))

        val aiTrailing = SlashCommandEngine.parseAISlashCommand("SELECT users joined last 7 days /sql")
        assertNotNull(aiTrailing)
        assertEquals("/sql", aiTrailing!!.command)
        assertEquals("SELECT users joined last 7 days", aiTrailing.cleanedDraft)

        val aiEmptyArg = SlashCommandEngine.parseAISlashCommand("/sql")
        assertNull(aiEmptyArg)
    }

    @Test
    fun slashCommandAutocompleteAndSelectionInTypingController() {
        val suggestions = SlashCommandEngine.suggestCommands("/ca")
        assertTrue("/calc" in suggestions)

        val engine = SuggestionEngine(listOf("hello", "world"))
        val controller = TypingController { engine }
        val editor = FakeEditor("Price: /ca")
        assertTrue(controller.choose(editor, "/calc", suggestions))
        assertEquals("Price: /calc ", editor.text)
    }

    @Test
    fun neuralTrailGlideDecodesSwipedWordAndInsertsSmartSpacing() {
        val engine = SuggestionEngine(listOf("hello", "help", "world", "neural", "keyboard"))
        val controller = TypingController { engine }

        val decoded = engine.decodeGlidePath("helo")
        assertTrue("hello" in decoded)

        val editor = FakeEditor("")
        val candidates1 = controller.glide(editor, "Helo")
        assertEquals("Hello", candidates1.first())
        assertEquals("Hello ", editor.text)

        val candidates2 = controller.glide(editor, "world")
        assertEquals("world", candidates2.first())
        assertEquals("Hello world ", editor.text)
    }

    @Test
    fun perAppPersonaEngineDetectsHostAppsAndBuildsGhostInstruction() {
        val emailPersona = AppPersonaEngine.resolvePersona("com.google.android.gm")
        assertEquals(AppPersonaEngine.EMAIL_EXECUTIVE.id, emailPersona.id)

        val chatPersona = AppPersonaEngine.resolvePersona("com.whatsapp")
        assertEquals(AppPersonaEngine.INSTANT_MESSENGER.id, chatPersona.id)

        val slackPersona = AppPersonaEngine.resolvePersona("com.Slack")
        assertEquals(AppPersonaEngine.WORKSPACE_PRO.id, slackPersona.id)

        val devPersona = AppPersonaEngine.resolvePersona("com.github.android")
        assertEquals(AppPersonaEngine.DEVELOPER_CODE.id, devPersona.id)

        val socialPersona = AppPersonaEngine.resolvePersona("com.linkedin.android")
        assertEquals(AppPersonaEngine.SOCIAL_CREATOR.id, socialPersona.id)

        val instruction = AppPersonaEngine.buildGhostCompletionInstruction("com.google.android.gm")
        assertTrue(instruction.contains("ENTIRE"))
    }

    @Test
    fun clipboardFusionPayloadCombinesCopiedClipAndCurrentDraft() {
        val (_, payloadWithDraft) = ClipboardHistoryManager.buildContextFusionReplyPayload(
            copiedClip = "Can you send the Q3 deck by 3pm?",
            currentDraft = "tell him yes I will send it at 2pm"
        )
        assertTrue(payloadWithDraft.contains("Can you send the Q3 deck by 3pm?"))
        assertTrue(payloadWithDraft.contains("tell him yes I will send it at 2pm"))

        val (_, payloadWithoutDraft) = ClipboardHistoryManager.buildContextFusionReplyPayload(
            copiedClip = "Are you free for a quick call?",
            currentDraft = ""
        )
        assertTrue(payloadWithoutDraft.contains("Are you free for a quick call?"))
    }

    @Test
    fun openRouterErrorParserProducesActionableDiagnosticsAndRetriableFlags() {
        val client = OpenRouterApiClient()
        val err401 = client.parseApiError(401, """{"error":{"message":"Invalid token"}}""")
        assertEquals(401, err401.statusCode)
        assertTrue(err401.userFriendlyMessage.contains("API key"))
        assertFalse(err401.isRetriableWithFreeFallback)

        val err402 = client.parseApiError(402, """{"error":{"message":"Insufficient credits"}}""")
        assertEquals(402, err402.statusCode)
        assertTrue(err402.isRetriableWithFreeFallback)

        val err429 = client.parseApiError(429, """{"error":{"message":"Rate limit reached"}}""")
        assertEquals(429, err429.statusCode)
        assertTrue(err429.isRetriableWithFreeFallback)

        val err503 = client.parseApiError(503, """{"error":{"message":"Upstream unavailable"}}""")
        assertEquals(503, err503.statusCode)
        assertTrue(err503.isRetriableWithFreeFallback)

        assertTrue("google/gemini-2.5-flash" in OpenRouterApiClient.DEFAULT_AUDIO_CAPABLE_MODELS)
    }

    private class FakeEditor(var text: String) : TypingEditor {
        var cursor = text.length
        override fun beforeCursor(limit: Int) = text.take(cursor).takeLast(limit)
        override fun afterCursor(limit: Int) = text.drop(cursor).take(limit)
        override fun hasSelection() = false
        override fun commit(text: String) {
            this.text = this.text.take(cursor) + text + this.text.drop(cursor)
            cursor += text.length
        }
        override fun deleteBefore(length: Int) {
            val start = (cursor - length).coerceAtLeast(0)
            text = text.removeRange(start, cursor)
            cursor = start
        }
        override fun deleteSelection() {}
        override fun batch(block: () -> Unit) { block() }
    }
}
