package com.supakeyboard.neuralkey.ime.mvi

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import com.supakeyboard.neuralkey.ime.clipboard.ClipboardItem
import com.supakeyboard.neuralkey.ime.media.GifStickerItem
import com.supakeyboard.neuralkey.ime.media.MediaTabCategory
import com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine
import com.supakeyboard.neuralkey.ime.voice.VoiceModelOption

@Immutable
enum class OneHandedMode {
    OFF,
    LEFT,
    RIGHT
}

@Immutable
enum class VoiceTransformMode(val label: String, val emoji: String, val instruction: String) {
    SMART_POLISH(
        label = "Smart Polish",
        emoji = "✨",
        instruction = "Transform this spoken voice dictation into clean, well-punctuated, natural prose while removing filler words (um, uh, like)."
    ),
    SMART_GRAMMAR(
        label = "Smart Grammar",
        emoji = "✅",
        instruction = "Correct every grammar, spelling, punctuation, capitalization and verb-tense error in this spoken dictation. Remove filler words (um, uh, like) and stutters, but keep the speaker's exact meaning, wording, language and tone. Do not rephrase, summarize or add anything. Output ONLY the corrected text."
    ),
    SMART_REPHRASE(
        label = "Smart Rephrase",
        emoji = "🔄",
        instruction = "Rephrase this spoken dictation into a clearer, smoother and more natural version of the same message. Keep the original meaning, language and approximate length, vary the wording and sentence structure where it improves flow, and do not add new facts. Output ONLY the rephrased text."
    ),
    VOICE_TO_EMAIL(
        label = "Voice → Email",
        emoji = "✉️",
        instruction = "Transform these spoken voice notes into a complete, polite, well-structured professional email ready to send."
    ),
    VOICE_TO_BULLETS(
        label = "Voice → Bullets",
        emoji = "📋",
        instruction = "Distill these spoken thoughts into a crisp, scannable list of actionable bullet points."
    ),
    VOICE_TO_ENGLISH(
        label = "Voice → English",
        emoji = "🌐",
        instruction = "Translate and polish this spoken input into fluent, natural English."
    ),
    VOICE_TO_SLOVAK(
        label = "Voice → Slovak",
        emoji = "🇸🇰",
        instruction = "Translate and polish this spoken input into fluent, natural, idiomatic Slovak (Slovenčina)."
    ),
    RAW_DICTATION(
        label = "Raw Text",
        emoji = "✍️",
        instruction = ""
    )
}

/**
 * Sealed hierarchy of distinct operational modes for the NeuralKey keyboard.
 */
@Immutable
sealed interface KeyboardMode {
    /**
     * Standard typing mode with predictive candidates and QWERTY/Symbol layouts.
     */
    @Immutable
    data class TypingMode(
        val isShifted: Boolean = false,
        val isCapsLocked: Boolean = false,
        val isSymbols: Boolean = false,
        val isSymbolsPage2: Boolean = false
    ) : KeyboardMode

    /**
     * AI Command Bar expanded state displaying quick-actions chips.
     */
    @Immutable
    data class AIMenuMode(
        val draftText: String,
        val actions: List<UserPromptEntity> = emptyList()
    ) : KeyboardMode

    /**
     * Real-time SSE streaming state with token preview card.
     */
    @Immutable
    data class AIStreamingMode(
        val originalText: String,
        val streamedText: String = "",
        val isStreaming: Boolean = true,
        val modelUsed: String = "",
        val error: String? = null,
        val canRetry: Boolean = false
    ) : KeyboardMode

    /**
     * Inline model quick-switcher overlay.
     */
    @Immutable
    data class ModelPickerMode(
        val searchQuery: String = "",
        val freeOnly: Boolean = false
    ) : KeyboardMode

    /**
     * Smart Clipboard Time-Machine & AI Context Fusion panel.
     */
    @Immutable
    data class ClipboardFusionMode(
        val draftText: String = ""
    ) : KeyboardMode

    /**
     * Multi-Model Arena Split-Compare: streams 2 OpenRouter models side-by-side in parallel.
     */
    @Immutable
    data class ArenaCompareMode(
        val originalText: String,
        val instruction: String = "Polish, improve clarity, and make this text shine.",
        val modelAId: String,
        val modelAName: String,
        val modelAText: String = "",
        val modelAStreaming: Boolean = true,
        val modelALatencyMs: Long = 0L,
        val modelAError: String? = null,
        val modelBId: String,
        val modelBName: String,
        val modelBText: String = "",
        val modelBStreaming: Boolean = true,
        val modelBLatencyMs: Long = 0L,
        val modelBError: String? = null
    ) : KeyboardMode

    /**
     * Whisper / Multimodal Voice-to-Action studio panel.
     */
    @Immutable
    data class VoiceActionMode(
        val selectedTransform: VoiceTransformMode = VoiceTransformMode.SMART_POLISH,
        val transcriptDraft: String = "",
        val statusMessage: String? = null
    ) : KeyboardMode

    /**
     * Federated Internet GIF & Sticker search studio panel.
     */
    @Immutable
    data class GifStickerMode(
        val initialQuery: String = "",
        val initialCategory: MediaTabCategory = MediaTabCategory.GIFS
    ) : KeyboardMode
}

@Immutable
enum class AICommitAction {
    REPLACE_DRAFT,
    INSERT_BELOW,
    COPY_TO_CLIPBOARD
}

/**
 * Deterministic User & System Intents for the MVI loop.
 */
@Stable
sealed interface KeyboardIntent {
    data class KeyPress(val char: String) : KeyboardIntent
    data class KeyCodePress(val primaryCode: Int) : KeyboardIntent
    data class TextCommit(val text: String) : KeyboardIntent
    data class SelectSuggestion(val word: String) : KeyboardIntent
    data class GlideTypingPath(val rawKeySequence: String) : KeyboardIntent
    object TriggerGhostCompletion : KeyboardIntent
    object HideKeyboard : KeyboardIntent
    object DeleteBackwards : KeyboardIntent
    data class AcceleratedDelete(val isWordMode: Boolean) : KeyboardIntent
    data class MoveCursor(val steps: Int) : KeyboardIntent
    object ToggleShift : KeyboardIntent
    object ToggleSymbols : KeyboardIntent
    object SwitchSymbolsPage : KeyboardIntent
    object OpenAIMenu : KeyboardIntent
    object CloseAIMenu : KeyboardIntent
    data class TriggerAIAction(val prompt: UserPromptEntity) : KeyboardIntent
    data class TriggerCustomPrompt(val customInstruction: String) : KeyboardIntent
    data class CommitAIResult(val action: AICommitAction) : KeyboardIntent
    object CancelAIStreaming : KeyboardIntent
    object RetryAIStreaming : KeyboardIntent
    object OpenModelPicker : KeyboardIntent
    object CloseModelPicker : KeyboardIntent
    data class SelectModel(val modelId: String) : KeyboardIntent
    data class UpdateModelSearch(val query: String) : KeyboardIntent
    object ToggleFreeModelsOnly : KeyboardIntent
    object SwitchToNextIme : KeyboardIntent
    data class SetOneHandedMode(val mode: OneHandedMode) : KeyboardIntent
    object CycleOneHandedMode : KeyboardIntent
    object ToggleSplitLayout : KeyboardIntent
    object OpenClipboardFusion : KeyboardIntent
    object CloseClipboardFusion : KeyboardIntent
    data class PasteClipboardItem(val text: String) : KeyboardIntent
    /** Pastes a clipboard image / screenshot into the focused field (Gboard-style image paste). */
    data class PasteClipboardImage(val id: Long) : KeyboardIntent
    /** Hides the "paste screenshot" chip for the given image until a newer one arrives. */
    data class DismissPendingImage(val id: Long) : KeyboardIntent
    data class TogglePinClipboardItem(val id: Long) : KeyboardIntent
    data class DeleteClipboardItem(val id: Long) : KeyboardIntent
    object ClearUnpinnedClipboard : KeyboardIntent
    data class ReplyToClipboardItem(val clipText: String) : KeyboardIntent
    data class SummarizeClipboardItem(val clipText: String) : KeyboardIntent
    data class TranslateClipboardItem(val clipText: String) : KeyboardIntent
    object OpenModelArena : KeyboardIntent
    object CloseModelArena : KeyboardIntent
    data class RunModelArena(val instruction: String, val modelAId: String, val modelBId: String) : KeyboardIntent
    data class CommitArenaWinner(val text: String, val winningModelId: String) : KeyboardIntent
    object OpenVoiceAction : KeyboardIntent
    object CloseVoiceAction : KeyboardIntent
    data class SelectVoiceModel(val modelId: String) : KeyboardIntent
    data class ProcessVoiceTranscript(val transcript: String, val transformMode: VoiceTransformMode) : KeyboardIntent
    data class ProcessVoiceAudioWav(val base64Wav: String, val transformMode: VoiceTransformMode) : KeyboardIntent
    data class OpenGifStickerSearch(
        val initialQuery: String = "",
        val initialCategory: MediaTabCategory = MediaTabCategory.GIFS
    ) : KeyboardIntent
    object CloseGifStickerSearch : KeyboardIntent
    data class CommitGifSticker(val item: GifStickerItem) : KeyboardIntent
    data class UpdateTargetContext(val packageName: String?, val inputType: Int, val imeAction: Int) : KeyboardIntent
    data class DraftChanged(val draftText: String) : KeyboardIntent
}

/**
 * Single source of truth immutable UI state for the keyboard surface.
 */
@Stable
data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.TypingMode(),
    val activeModel: OpenRouterModelEntity? = null,
    val selectedVoiceModelId: String = VoiceAudioEngine.DEFAULT_VOICE_MODEL_ID,
    val availableVoiceModels: List<VoiceModelOption> = VoiceAudioEngine.CURATED_VOICE_MODELS,
    val pinnedModels: List<OpenRouterModelEntity> = emptyList(),
    val availableModels: List<OpenRouterModelEntity> = emptyList(),
    val userPrompts: List<UserPromptEntity> = emptyList(),
    val clipboardItems: List<ClipboardItem> = emptyList(),
    /** A just-copied image or fresh screenshot offered as a one-tap chip in the suggestion strip. */
    val pendingImage: ClipboardItem? = null,
    /** True when the focused field declares support for image content (commitContent). */
    val fieldAcceptsImages: Boolean = false,
    val currentDraft: String = "",
    val targetPackageName: String? = null,
    val imeAction: Int = android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED,
    val inputType: Int = android.view.inputmethod.EditorInfo.IME_NULL,
    val isShifted: Boolean = false,
    val isCapsLocked: Boolean = false,
    val isSymbols: Boolean = false,
    val isSymbolsPage2: Boolean = false,
    val hapticEnabled: Boolean = true,
    val hapticIntensity: Int = 70,
    val freeModelsOnly: Boolean = false,
    val currentTheme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme = com.supakeyboard.neuralkey.ime.theme.KeyboardThemes.CYBERPUNK_NEON,
    val suggestions: List<String> = emptyList(),
    val suggestionsEnabled: Boolean = true,
    val autoCorrectionEnabled: Boolean = true,
    val keyMemoryEnabled: Boolean = true,
    val nextWordPredictionEnabled: Boolean = true,
    val glideTypingEnabled: Boolean = true,
    val liveTranscriptEnabled: Boolean = true,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val splitKeyboardOnFold: Boolean = true,
    val isSensitiveField: Boolean = false,
    val errorMessage: String? = null
)
