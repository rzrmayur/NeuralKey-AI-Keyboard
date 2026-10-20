package com.supakeyboard.neuralkey.ime.mvi

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity

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
        val error: String? = null
    ) : KeyboardMode

    /**
     * Inline model quick-switcher overlay.
     */
    @Immutable
    data class ModelPickerMode(
        val searchQuery: String = "",
        val freeOnly: Boolean = false
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
    object OpenModelPicker : KeyboardIntent
    object CloseModelPicker : KeyboardIntent
    data class SelectModel(val modelId: String) : KeyboardIntent
    data class UpdateModelSearch(val query: String) : KeyboardIntent
    object ToggleFreeModelsOnly : KeyboardIntent
    object SwitchToNextIme : KeyboardIntent
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
    val pinnedModels: List<OpenRouterModelEntity> = emptyList(),
    val availableModels: List<OpenRouterModelEntity> = emptyList(),
    val userPrompts: List<UserPromptEntity> = emptyList(),
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
    val errorMessage: String? = null
)
