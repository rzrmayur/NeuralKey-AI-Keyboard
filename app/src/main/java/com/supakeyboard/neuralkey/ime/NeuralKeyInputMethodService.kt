package com.supakeyboard.neuralkey.ime

import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.supakeyboard.neuralkey.ime.lifecycle.LifecycleInputMethodService
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import kotlinx.coroutines.flow.StateFlow

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

    override fun onCreateInputView(): View {
        return try {
            ComposeView(this).apply {
                attachViewTreeOwners(this)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnLifecycleDestroyed(
                        this@NeuralKeyInputMethodService
                    )
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

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // Make the whole keyboard frame touchable so taps on the bottom row
        // (space, globe, enter) reach our views instead of being dropped.
        outInsets.contentTopInsets = outInsets.visibleTopInsets
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (info == null) return

        viewModel.onAppSwitch(info.packageName)
        viewModel.updateInputContext(info.packageName, info)
        viewModel.updateInputType(info.inputType)

        val draft = extractCurrentDraft()
        viewModel.updateDraft(draft)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Real-time draft extraction when selection changes
        val ic = currentInputConnection ?: return
        val draft = try {
            val selected = ic.getSelectedText(0)?.toString()
            if (!selected.isNullOrBlank()) selected
            else ic.getTextBeforeCursor(MAX_DRAFT_LENGTH, 0)?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
        viewModel.updateDraft(draft)
    }

    /**
     * Extracts the user's current draft from the active InputConnection.
     * Prioritizes selected text, then falls back to extracted text or text before cursor.
     */
    fun extractCurrentDraft(): String {
        val ic = currentInputConnection ?: return ""
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) {
            return selected
        }
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
        if (!extracted.isNullOrBlank()) {
            return extracted
        }
        val beforeCursor = ic.getTextBeforeCursor(1000, 0)?.toString()
        return beforeCursor ?: ""
    }

    /**
     * Handles deterministic user intents from the UI layer.
     * IME-level operations (input connection calls) stay here;
     * state mutations are delegated to [KeyboardViewModel].
     */
    fun processIntent(intent: KeyboardIntent) {
        when (intent) {
            is KeyboardIntent.KeyPress -> {
                currentInputConnection?.commitText(intent.char, 1)
                updateDraftAfterEdit()
            }
            is KeyboardIntent.KeyCodePress -> {
                sendDownUpKeyEvents(intent.primaryCode)
                updateDraftAfterEdit()
            }
            is KeyboardIntent.TextCommit -> {
                currentInputConnection?.commitText(intent.text, 1)
                updateDraftAfterEdit()
            }
            is KeyboardIntent.DeleteBackwards -> {
                currentInputConnection?.deleteSurroundingText(1, 0)
                updateDraftAfterEdit()
            }
            is KeyboardIntent.AcceleratedDelete -> {
                if (intent.isWordMode) {
                    deleteWordBeforeCursor()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                updateDraftAfterEdit()
            }
            is KeyboardIntent.MoveCursor -> {
                moveCursor(intent.steps)
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
                commitAIText(streamingState.streamedText, intent.action)
                viewModel.commitAIResult()
            }
            is KeyboardIntent.CancelAIStreaming -> viewModel.cancelAIStreaming()
            is KeyboardIntent.OpenModelPicker -> viewModel.openModelPicker()
            is KeyboardIntent.CloseModelPicker -> viewModel.closeModelPicker()
            is KeyboardIntent.SelectModel -> viewModel.selectModel(intent.modelId)
            is KeyboardIntent.UpdateModelSearch -> viewModel.updateModelSearch(intent.query)
            is KeyboardIntent.ToggleFreeModelsOnly -> viewModel.toggleFreeModelsOnly()
            is KeyboardIntent.SwitchToNextIme -> switchToNextInputMethod(false)
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
        val newSelectionStart =
            (extracted.selectionStart + steps).coerceIn(0, extracted.text.length)
        val newSelectionEnd =
            (extracted.selectionEnd + steps).coerceIn(0, extracted.text.length)
        ic.setSelection(newSelectionStart, newSelectionEnd)
    }

    private fun updateDraftAfterEdit() {
        viewModel.updateDraft(extractCurrentDraft())
    }

    companion object {
        private const val TAG = "NeuralKeyService"
        private const val MAX_DRAFT_LENGTH = 5000
    }
}
