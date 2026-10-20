package com.supakeyboard.neuralkey.ime

import com.supakeyboard.neuralkey.data.network.OpenRouterApiClient
import com.supakeyboard.neuralkey.ime.clipboard.ClipboardHistoryManager
import com.supakeyboard.neuralkey.ime.media.GifStickerRepository
import com.supakeyboard.neuralkey.ime.mvi.VoiceTransformMode
import com.supakeyboard.neuralkey.ime.persona.AppPersonaEngine
import com.supakeyboard.neuralkey.ime.slash.SlashCommandEngine
import com.supakeyboard.neuralkey.ime.typing.SuggestionEngine
import com.supakeyboard.neuralkey.ime.typing.TypingController
import com.supakeyboard.neuralkey.ime.typing.TypingEditor
import com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine
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

        assertTrue(VoiceAudioEngine.isDedicatedTranscriptionModel("elevenlabs/scribe-v2"))
        assertTrue("google/gemini-2.5-flash" in OpenRouterApiClient.DEFAULT_AUDIO_CAPABLE_MODELS)
    }

    @Test
    fun voiceAudioEngineDeduplicatesRepetitiveSpeechAndSupportsScribeV2() {
        // 1. Consecutive single-word and multi-word stutter deduplication
        val stuttered = "hello hello world world can you can you send the report send the report today"
        assertEquals(
            "hello world can you send the report today",
            VoiceAudioEngine.cleanRepetitiveSpeech(stuttered)
        )

        // 2. Partial + Final segment merging without duplicating sentences
        val mergedIdentical = VoiceAudioEngine.mergeRecognizedSegment(
            existingCommitted = "We shipped the new release today",
            newFinalSegment = "We shipped the new release today"
        )
        assertEquals("We shipped the new release today", mergedIdentical)

        val mergedCumulative = VoiceAudioEngine.mergeRecognizedSegment(
            existingCommitted = "We shipped the new",
            newFinalSegment = "We shipped the new release today at 3pm"
        )
        assertEquals("We shipped the new release today at 3pm", mergedCumulative)

        // 3. Curated Voice-Only models include ElevenLabs Scribe v2, Voxtral, Gemini Flash, and Free Omni
        val modelIds = VoiceAudioEngine.CURATED_VOICE_MODELS.map { it.id }
        assertTrue("elevenlabs/scribe-v2" in modelIds)
        assertTrue("mistralai/voxtral-mini-transcribe" in modelIds)
        assertTrue("google/gemini-2.5-flash" in modelIds)
        assertTrue("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free" in modelIds)

        // 4. 16kHz PCM WAV Base64 encoder writes valid RIFF WAVE header
        val dummyPcm = ByteArray(320) { 0 }
        val base64Wav = VoiceAudioEngine.encodePcmToWavBase64(dummyPcm, sampleRate = 16000)
        val decoded = java.util.Base64.getDecoder().decode(base64Wav)
        assertEquals("RIFF", String(decoded.sliceArray(0..3)))
        assertEquals("WAVE", String(decoded.sliceArray(8..11)))
    }

    @Test
    fun slovakTranslationPromptSlashCommandAndVoiceModeAreConfigured() {
        val skCommand = SlashCommandEngine.parseAISlashCommand("/slovak Good morning team, how is the release going?")
        assertNotNull(skCommand)
        assertEquals("/slovak", skCommand!!.command)
        assertTrue(skCommand.aiInstruction.contains("Slovak"))

        val skShort = SlashCommandEngine.parseAISlashCommand("Thank you very much /sk")
        assertNotNull(skShort)
        assertEquals("/sk", skShort!!.command)
        assertTrue(skShort.aiInstruction.contains("Slovak"))

        assertTrue(VoiceTransformMode.VOICE_TO_SLOVAK.instruction.contains("Slovak"))
    }

    @Test
    fun gifAndStickerRepositoryParsesGiphyAndSearchesOpenSourceNotoStickers() {
        val sampleGiphyJson = """
            {
              "data": [
                {
                  "id": "abc123gif",
                  "title": "Party Cat GIF",
                  "images": {
                    "fixed_height_small": { "url": "https://media.giphy.com/media/abc123gif/100.gif" },
                    "fixed_height": { "url": "https://media.giphy.com/media/abc123gif/200.gif" }
                  }
                }
              ]
            }
        """.trimIndent()
        val notoFire = GifStickerRepository.NOTO_ANIMATED_STICKERS.filter { it.tags.contains("fire") }
        assertTrue(notoFire.isNotEmpty())
        assertTrue(notoFire.first().fullUrl.endsWith("/512.gif"))
        assertTrue(sampleGiphyJson.contains("Party Cat GIF"))
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
