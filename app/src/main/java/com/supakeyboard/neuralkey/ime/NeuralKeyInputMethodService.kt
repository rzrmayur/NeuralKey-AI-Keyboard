package com.supakeyboard.neuralkey.ime

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.supakeyboard.neuralkey.NeuralKeyApplication
import com.supakeyboard.neuralkey.ime.lifecycle.LifecycleInputMethodService
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.typing.InputFieldPolicy
import com.supakeyboard.neuralkey.ime.typing.TypingController
import com.supakeyboard.neuralkey.ime.typing.TypingEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Core NeuralKey Input Method Service.
 * Hosts Jetpack Compose via [LifecycleInputMethodService] with full lifecycle integration.
 * State management is delegated to [KeyboardViewModel].
 */
class NeuralKeyInputMethodService : LifecycleInputMethodService() {

    private val viewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[KeyboardViewModel::class.java]
    }
    val uiState: StateFlow<KeyboardUiState> get() = viewModel.uiState

    private val typing = TypingController { (application as NeuralKeyApplication).suggestionEngine }
    private var suggestionJob: Job? = null
    private var inputActive = false

    private class ConnectionEditor(private val ic: InputConnection) : TypingEditor {
        override fun beforeCursor(limit: Int) = ic.getTextBeforeCursor(limit, 0)?.toString().orEmpty()
        override fun afterCursor(limit: Int) = ic.getTextAfterCursor(limit, 0)?.toString().orEmpty()
        override fun hasSelection() = !ic.getSelectedText(0).isNullOrEmpty()
        override fun commit(text: String) { ic.commitText(text, 1) }
        override fun deleteBefore(length: Int) { ic.deleteSurroundingText(length, 0) }
        override fun deleteSelection() { ic.commitText("", 1) }
        override fun batch(block: () -> Unit) {
            ic.beginBatchEdit()
            try { block() } finally { ic.endBatchEdit() }
        }
    }

    // Keep the host editor visible in landscape and split-screen as well.
    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        return try {
            ComposeView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                attachViewTreeOwners(this)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
                )
                setContent {
                    MaterialTheme(
                        colorScheme = darkColorScheme(
                            primary = Color(0xFF7C4DFF),
                            secondary = Color(0xFF00E5FF),
                            background = Color(0xFF141416),
                            surface = Color(0xFF1E1E1E)
                        )
                    ) {
                        val state by uiState.collectAsState()
                        com.supakeyboard.neuralkey.ime.ui.KeyboardRootView(
                            uiState = state,
                            onIntent = ::processIntent
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Compose input view, falling back to simple view", e)
            createFallbackInputView()
        }
    }

    private fun createFallbackInputView(): View {
        val context = this
        return android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(android.widget.TextView(context).apply {
                text = "NeuralKey AI Keyboard\n\nFailed to initialize. Please restart the app."
                setTextColor(android.graphics.Color.WHITE)
                textSize = 16f
                gravity = android.view.Gravity.CENTER
            })
        }
    }

    // Preserve InputMethodService's touch region; the system navigation controls
    // must not become part of the keyboard's touchable frame.
    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        inputActive = true
        typing.reset()
        suggestionJob?.cancel()
        viewModel.onAppSwitch(info?.packageName)
        viewModel.updateInputContext(info?.packageName, info)
        viewModel.updateInputType(info?.inputType ?: 0)
        viewModel.setSensitiveField(InputFieldPolicy.isSensitive(info?.inputType ?: 0))
        updateDraftAfterEdit()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateDraftAfterEdit()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        suggestionJob?.cancel()
        typing.reset()
        viewModel.cancelAIStreaming()
        viewModel.updateSuggestions(emptyList())
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        inputActive = false
        suggestionJob?.cancel()
        typing.reset()
        viewModel.cancelAIStreaming()
        viewModel.updateDraft("")
        viewModel.updateSuggestions(emptyList())
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        currentInputConnection?.let { typing.selectionChanged(ConnectionEditor(it)) }
        updateDraftAfterEdit()
    }

    /**
     * Extracts the user's current draft from the active InputConnection.
     * Uses selected text or a bounded prefix before the cursor, matching replacement scope.
     */
    fun extractCurrentDraft(): String {
        if (viewModel.uiState.value.isSensitiveField) return ""
        val ic = currentInputConnection ?: return ""
        return try {
            ic.getSelectedText(0)?.toString()?.takeIf { it.isNotEmpty() }
                ?: ic.getTextBeforeCursor(MAX_DRAFT_LENGTH, 0)?.toString().orEmpty()
        } catch (_: RuntimeException) { "" }
    }

    /**
     * Handles deterministic user intents from the UI layer.
     * IME-level operations (input connection calls) stay here;
     * state mutations are delegated to [KeyboardViewModel].
     */
    fun processIntent(intent: KeyboardIntent) {
        if (viewModel.uiState.value.isSensitiveField && (intent is KeyboardIntent.OpenAIMenu ||
                intent is KeyboardIntent.TriggerAIAction || intent is KeyboardIntent.TriggerCustomPrompt)) return
        when (intent) {
            is KeyboardIntent.KeyPress -> {
                currentInputConnection?.let {
                    val state = viewModel.uiState.value
                    typing.type(ConnectionEditor(it), intent.char,
                        state.autoCorrectionEnabled && InputFieldPolicy.allowsSuggestions(state.inputType))
                }
                if (intent.char.any { it.isLetter() }) viewModel.characterTyped()
                updateDraftAfterEdit()
            }
            is KeyboardIntent.KeyCodePress -> {
                typing.reset()
                if (intent.primaryCode != KeyEvent.KEYCODE_ENTER || !sendDefaultEditorAction(true)) {
                    sendDownUpKeyEvents(intent.primaryCode)
                }
                updateDraftAfterEdit()
            }
            is KeyboardIntent.TextCommit -> {
                typing.reset()
                currentInputConnection?.commitText(intent.text, 1)
                updateDraftAfterEdit()
            }
            is KeyboardIntent.DeleteBackwards -> {
                currentInputConnection?.let { typing.backspace(ConnectionEditor(it)) }
                updateDraftAfterEdit()
            }
            is KeyboardIntent.AcceleratedDelete -> {
                if (intent.isWordMode) {
                    deleteWordBeforeCursor()
                } else {
                    currentInputConnection?.let { typing.backspace(ConnectionEditor(it)) }
                }
                updateDraftAfterEdit()
            }
            is KeyboardIntent.SelectSuggestion -> {
                val state = viewModel.uiState.value
                if (state.suggestionsEnabled && InputFieldPolicy.allowsSuggestions(state.inputType)) {
                    currentInputConnection?.let {
                        typing.choose(ConnectionEditor(it), intent.word, state.suggestions)
                    }
                    updateDraftAfterEdit()
                }
            }
            is KeyboardIntent.HideKeyboard -> requestHideSelf(0)
            is KeyboardIntent.MoveCursor -> {
                typing.reset()
                moveCursor(intent.steps)
                updateDraftAfterEdit()
            }
            is KeyboardIntent.ToggleShift -> viewModel.toggleShift()
            is KeyboardIntent.ToggleSymbols -> viewModel.toggleSymbols()
            is KeyboardIntent.SwitchSymbolsPage -> viewModel.switchSymbolsPage()
            is KeyboardIntent.OpenAIMenu -> {
                val draft = extractCurrentDraft()
                viewModel.openAIMenu(draft)
            }
            is KeyboardIntent.CloseAIMenu -> viewModel.closeAIMenu()
            is KeyboardIntent.TriggerAIAction -> {
                val draft = extractCurrentDraft()
                viewModel.triggerAIAction(intent.prompt, draft)
            }
            is KeyboardIntent.TriggerCustomPrompt -> {
                val draft = extractCurrentDraft()
                viewModel.triggerCustomPrompt(intent.customInstruction, draft)
            }
            is KeyboardIntent.CommitAIResult -> {
                val streamingState =
                    viewModel.uiState.value.mode as? KeyboardMode.AIStreamingMode ?: return
                if (streamingState.isStreaming || streamingState.error != null || streamingState.streamedText.isBlank()) return
                if (intent.action != com.supakeyboard.neuralkey.ime.mvi.AICommitAction.COPY_TO_CLIPBOARD &&
                    extractCurrentDraft() != streamingState.originalText) {
                    android.widget.Toast.makeText(this, "Text changed. Run the AI action again before applying it.", android.widget.Toast.LENGTH_LONG).show()
                    viewModel.cancelAIStreaming()
                    return
                }
                commitAIText(streamingState.streamedText, intent.action)
                viewModel.commitAIResult()
            }
            is KeyboardIntent.CancelAIStreaming -> viewModel.cancelAIStreaming()
            is KeyboardIntent.OpenModelPicker -> viewModel.openModelPicker()
            is KeyboardIntent.CloseModelPicker -> viewModel.closeModelPicker()
            is KeyboardIntent.SelectModel -> viewModel.selectModel(intent.modelId)
            is KeyboardIntent.UpdateModelSearch -> viewModel.updateModelSearch(intent.query)
            is KeyboardIntent.ToggleFreeModelsOnly -> viewModel.toggleFreeModelsOnly()
            is KeyboardIntent.SwitchToNextIme -> {
                typing.reset()
                if (!switchToNextInputMethod(false)) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                }
            }
            is KeyboardIntent.UpdateTargetContext ->
                viewModel.updateTargetContext(intent.packageName, intent.imeAction)
            is KeyboardIntent.DraftChanged -> viewModel.draftChanged(intent.draftText)
        }
    }

    private fun commitAIText(
        text: String,
        action: com.supakeyboard.neuralkey.ime.mvi.AICommitAction
    ) {
        val ic = currentInputConnection ?: return
        typing.reset()
        when (action) {
            com.supakeyboard.neuralkey.ime.mvi.AICommitAction.REPLACE_DRAFT -> {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText(text, 1)
                } else {
                    val currentDraftLength = viewModel.uiState.value.currentDraft.length
                    if (currentDraftLength > 0) {
                        ic.deleteSurroundingText(currentDraftLength, 0)
                    }
                    ic.commitText(text, 1)
                }
            }
            com.supakeyboard.neuralkey.ime.mvi.AICommitAction.INSERT_BELOW -> {
                ic.commitText("\n\n$text", 1)
            }
            com.supakeyboard.neuralkey.ime.mvi.AICommitAction.COPY_TO_CLIPBOARD -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("NeuralKey AI Output", text)
                clipboard?.setPrimaryClip(clip)
            }
        }
        updateDraftAfterEdit()
    }

    private fun deleteWordBeforeCursor() {
        val ic = currentInputConnection ?: return
        typing.reset()
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        if (textBefore.isEmpty()) return

        var index = textBefore.length - 1
        while (index >= 0 && textBefore[index].isWhitespace()) {
            index--
        }
        while (index >= 0 && !textBefore[index].isWhitespace()) {
            index--
        }
        val charsToDelete = textBefore.length - (index + 1)
        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        }
    }

    private fun moveCursor(steps: Int) {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val position = (extracted.selectionEnd + steps).coerceIn(0, extracted.text.length)
        ic.setSelection(extracted.startOffset + position, extracted.startOffset + position)
    }

    private fun updateDraftAfterEdit() {
        if (!inputActive) return
        viewModel.updateDraft(extractCurrentDraft())
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        suggestionJob?.cancel()
        val state = viewModel.uiState.value
        if (!inputActive || !state.suggestionsEnabled || !InputFieldPolicy.allowsSuggestions(state.inputType)) {
            viewModel.updateSuggestions(emptyList())
            return
        }
        val ic = currentInputConnection ?: return
        val word = typing.currentWord(ConnectionEditor(ic))
        if (word == null) { viewModel.updateSuggestions(emptyList()); return }
        // Publish the literal word immediately; compute dictionary matches off the UI thread.
        viewModel.updateSuggestions(listOf(word))
        val engine = (application as NeuralKeyApplication).suggestionEngine ?: return
        suggestionJob = lifecycleScope.launch {
            val words = withContext(Dispatchers.Default) { engine.suggestions(word) }
            val latest = viewModel.uiState.value
            if (inputActive && latest.suggestionsEnabled && currentInputConnection === ic &&
                typing.currentWord(ConnectionEditor(ic)) == word) viewModel.updateSuggestions(words)
        }
    }

    companion object {
        private const val TAG = "NeuralKeyService"
        private const val MAX_DRAFT_LENGTH = 5000
    }
}
