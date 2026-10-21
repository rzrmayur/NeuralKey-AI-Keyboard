package com.supakeyboard.neuralkey.ime

import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
import com.supakeyboard.neuralkey.ime.media.GifStickerRepository
import com.supakeyboard.neuralkey.ime.media.MediaCommitResult
import com.supakeyboard.neuralkey.ime.media.MediaTabCategory
import com.supakeyboard.neuralkey.ime.mvi.AICommitAction
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.VoiceTransformMode
import com.supakeyboard.neuralkey.ime.slash.SlashCommandEngine
import com.supakeyboard.neuralkey.ime.typing.InputFieldPolicy
import com.supakeyboard.neuralkey.ime.typing.TypingController
import com.supakeyboard.neuralkey.ime.typing.TypingEditor
import com.supakeyboard.neuralkey.ime.ui.ImeInsetsHost
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
    private var inputHost: ImeInsetsHost? = null

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

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        win.setDecorFitsSystemWindows(false)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        inputHost?.refreshInsets()
    }

    override fun onCreateInputView(): View {
        val host = ImeInsetsHost(this)
        inputHost = host
        attachViewTreeOwners(host)
        val content = try {
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
        host.addView(content)
        return host
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

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        inputActive = true
        typing.reset()
        suggestionJob?.cancel()
        viewModel.onAppSwitch(info?.packageName)
        viewModel.updateInputContext(info?.packageName, info)
        viewModel.updateInputType(info?.inputType ?: 0)
        viewModel.setSensitiveField(InputFieldPolicy.isSensitive(info?.inputType ?: 0))
        runCatching { (application as NeuralKeyApplication).clipboardHistoryManager.syncFromSystemClipboard() }
        updateDraftAfterEdit()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputHost?.refreshInsets()
        runCatching { (application as NeuralKeyApplication).clipboardHistoryManager.syncFromSystemClipboard() }
        updateDraftAfterEdit()
    }

    override fun onDestroy() {
        inputHost = null
        super.onDestroy()
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
     */
    fun extractCurrentDraft(): String {
        if (viewModel.uiState.value.isSensitiveField) return ""
        val ic = currentInputConnection ?: return ""
        return try {
            ic.getSelectedText(0)?.toString()?.takeIf { it.isNotEmpty() }
                ?: ic.getTextBeforeCursor(MAX_DRAFT_LENGTH, 0)?.toString().orEmpty()
        } catch (_: RuntimeException) { "" }
    }

    fun processIntent(intent: KeyboardIntent) {
        val sensitiveBlocked = viewModel.uiState.value.isSensitiveField && (
            intent is KeyboardIntent.OpenAIMenu ||
                intent is KeyboardIntent.TriggerAIAction ||
                intent is KeyboardIntent.TriggerCustomPrompt ||
                intent is KeyboardIntent.TriggerGhostCompletion ||
                intent is KeyboardIntent.OpenClipboardFusion ||
                intent is KeyboardIntent.OpenModelArena ||
                intent is KeyboardIntent.OpenVoiceAction ||
                intent is KeyboardIntent.OpenGifStickerSearch
            )
        if (sensitiveBlocked) return

        when (intent) {
            is KeyboardIntent.KeyPress -> {
                currentInputConnection?.let { ic ->
                    val state = viewModel.uiState.value
                    val allowsAssistance = InputFieldPolicy.allowsSuggestions(state.inputType)
                    val editor = ConnectionEditor(ic)

                    // Check instant offline slash macro expansion on Space (e.g. /date, /time, /shrug, /calc 12*4)
                    if (intent.char == " " && allowsAssistance && !state.isSensitiveField) {
                        val before = editor.beforeCursor(256)
                        val latestClip = (application as? NeuralKeyApplication)?.clipboardHistoryManager?.latestClipText()
                        val expanded = SlashCommandEngine.evaluateOfflineMacro(before, latestClip)
                        if (expanded != null && expanded != before) {
                            typing.reset()
                            editor.batch {
                                editor.deleteBefore(before.length)
                                editor.commit("$expanded ")
                            }
                            updateDraftAfterEdit()
                            return
                        }
                    }

                    typing.type(
                        editor = editor,
                        text = intent.char,
                        autoCorrect = state.autoCorrectionEnabled && allowsAssistance,
                        learnMemory = state.keyMemoryEnabled && allowsAssistance
                    )
                }
                if (intent.char.any { it.isLetter() }) viewModel.characterTyped()
                updateDraftAfterEdit()
            }
            is KeyboardIntent.GlideTypingPath -> {
                val state = viewModel.uiState.value
                val allowsAssistance = InputFieldPolicy.allowsSuggestions(state.inputType)
                if (state.glideTypingEnabled && allowsAssistance && !state.isSensitiveField) {
                    currentInputConnection?.let { ic ->
                        val candidates = typing.glide(
                            editor = ConnectionEditor(ic),
                            rawPath = intent.rawKeySequence,
                            learnMemory = state.keyMemoryEnabled
                        )
                        viewModel.characterTyped()
                        updateDraftAfterEdit()
                        if (candidates.isNotEmpty() && state.suggestionsEnabled) {
                            viewModel.updateSuggestions(candidates)
                        }
                    }
                }
            }
            is KeyboardIntent.KeyCodePress -> {
                typing.reset()
                if (intent.primaryCode == KeyEvent.KEYCODE_ENTER && !viewModel.uiState.value.isSensitiveField) {
                    val ic = currentInputConnection
                    if (ic != null) {
                        val editor = ConnectionEditor(ic)
                        val before = editor.beforeCursor(512)
                        val latestClip = (application as? NeuralKeyApplication)?.clipboardHistoryManager?.latestClipText()
                        // 1. Check /gif or /sticker slash command on Enter
                        val trimmedBefore = before.trim()
                        if (trimmedBefore.startsWith("/gif", ignoreCase = true) ||
                            trimmedBefore.startsWith("/sticker", ignoreCase = true)
                        ) {
                            val isSticker = trimmedBefore.startsWith("/sticker", ignoreCase = true)
                            val query = trimmedBefore.substringAfter(" ", "").trim()
                            editor.batch {
                                editor.deleteBefore(before.length)
                            }
                            updateDraftAfterEdit()
                            viewModel.openGifStickerSearch(
                                initialQuery = query,
                                category = if (isSticker) MediaTabCategory.STICKERS else MediaTabCategory.GIFS
                            )
                            return
                        }
                        // 2. Offline macro evaluation on Enter (e.g. /calc (150 * 4) / 2)
                        val expandedMacro = SlashCommandEngine.evaluateOfflineMacro(before, latestClip)
                        if (expandedMacro != null && expandedMacro != before) {
                            editor.batch {
                                editor.deleteBefore(before.length)
                                editor.commit(expandedMacro)
                            }
                            updateDraftAfterEdit()
                            return
                        }
                        // 2. AI slash command evaluation on Enter (e.g. /email ask team to move sync)
                        val aiSlash = SlashCommandEngine.parseAISlashCommand(before)
                        if (aiSlash != null) {
                            editor.batch {
                                editor.deleteBefore(before.length)
                                editor.commit(aiSlash.cleanedDraft)
                            }
                            updateDraftAfterEdit()
                            viewModel.triggerCustomPrompt(aiSlash.aiInstruction, aiSlash.cleanedDraft)
                            return
                        }
                    }
                }
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
                val allowsAssistance = InputFieldPolicy.allowsSuggestions(state.inputType)
                if (state.suggestionsEnabled && allowsAssistance) {
                    currentInputConnection?.let {
                        typing.choose(
                            editor = ConnectionEditor(it),
                            suggestion = intent.word,
                            candidates = state.suggestions,
                            learnMemory = state.keyMemoryEnabled && allowsAssistance
                        )
                    }
                    updateDraftAfterEdit()
                }
            }
            is KeyboardIntent.TriggerGhostCompletion -> {
                val draft = extractCurrentDraft()
                if (draft.isNotBlank()) {
                    viewModel.triggerGhostCompletion(draft)
                }
            }
            is KeyboardIntent.OpenClipboardFusion -> {
                val draft = extractCurrentDraft()
                viewModel.openClipboardFusion(draft)
            }
            is KeyboardIntent.CloseClipboardFusion -> viewModel.closeClipboardFusion()
            is KeyboardIntent.PasteClipboardItem -> {
                typing.reset()
                currentInputConnection?.commitText(intent.text, 1)
                viewModel.closeClipboardFusion()
                updateDraftAfterEdit()
            }
            is KeyboardIntent.TogglePinClipboardItem -> viewModel.togglePinClipboardItem(intent.id)
            is KeyboardIntent.DeleteClipboardItem -> viewModel.deleteClipboardItem(intent.id)
            is KeyboardIntent.ClearUnpinnedClipboard -> viewModel.clearUnpinnedClipboard()
            is KeyboardIntent.ReplyToClipboardItem -> {
                val draft = extractCurrentDraft()
                viewModel.replyToClipboardItem(intent.clipText, draft)
            }
            is KeyboardIntent.SummarizeClipboardItem -> viewModel.summarizeClipboardItem(intent.clipText)
            is KeyboardIntent.TranslateClipboardItem -> viewModel.translateClipboardItem(intent.clipText)
            is KeyboardIntent.OpenModelArena -> {
                val draft = extractCurrentDraft()
                viewModel.openAndRunModelArena(draft)
            }
            is KeyboardIntent.CloseModelArena -> viewModel.cancelAIStreaming()
            is KeyboardIntent.RunModelArena -> {
                val draft = extractCurrentDraft()
                viewModel.openAndRunModelArena(
                    fallbackDraft = draft,
                    instruction = intent.instruction,
                    overrideModelAId = intent.modelAId,
                    overrideModelBId = intent.modelBId
                )
            }
            is KeyboardIntent.CommitArenaWinner -> {
                commitAIText(intent.text, AICommitAction.REPLACE_DRAFT)
                viewModel.commitAIResult()
            }
            is KeyboardIntent.OpenVoiceAction -> viewModel.openVoiceAction()
            is KeyboardIntent.CloseVoiceAction -> viewModel.closeVoiceAction()
            is KeyboardIntent.SelectVoiceModel -> viewModel.selectVoiceModel(intent.modelId)
            is KeyboardIntent.ProcessVoiceTranscript -> {
                val cleaned = com.supakeyboard.neuralkey.ime.voice.VoiceAudioEngine.cleanRepetitiveSpeech(intent.transcript)
                if (intent.transformMode == VoiceTransformMode.RAW_DICTATION) {
                    typing.reset()
                    if (cleaned.isNotBlank()) {
                        currentInputConnection?.commitText("$cleaned ", 1)
                    }
                    viewModel.closeVoiceAction()
                    updateDraftAfterEdit()
                } else {
                    viewModel.processVoiceTranscript(cleaned, intent.transformMode)
                }
            }
            is KeyboardIntent.ProcessVoiceAudioWav -> {
                viewModel.processVoiceAudioWav(intent.base64Wav, intent.transformMode)
            }
            is KeyboardIntent.OpenGifStickerSearch -> {
                val initialQuery = intent.initialQuery.ifBlank {
                    extractCurrentDraft().trim().takeIf { it.length in 1..32 && !it.contains('\n') }.orEmpty()
                }
                viewModel.openGifStickerSearch(initialQuery, intent.initialCategory)
            }
            is KeyboardIntent.CloseGifStickerSearch -> viewModel.closeGifStickerSearch()
            is KeyboardIntent.CommitGifSticker -> {
                val ic = currentInputConnection
                val editorInfo = currentInputEditorInfo
                viewModel.closeGifStickerSearch()
                if (ic != null) {
                    lifecycleScope.launch {
                        val commitResult = GifStickerRepository.getInstance(this@NeuralKeyInputMethodService)
                            .commitMediaToEditor(
                                item = intent.item,
                                inputConnection = ic,
                                editorInfo = editorInfo
                            )
                        if (commitResult != MediaCommitResult.FAILED) {
                            updateDraftAfterEdit()
                        } else {
                            android.widget.Toast.makeText(
                                this@NeuralKeyInputMethodService,
                                "Couldn't insert media into this field",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
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
            is KeyboardIntent.SetOneHandedMode -> viewModel.setOneHandedMode(intent.mode)
            is KeyboardIntent.CycleOneHandedMode -> viewModel.cycleOneHandedMode()
            is KeyboardIntent.ToggleSplitLayout -> viewModel.toggleSplitLayout()
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
                val currentEditorText = extractCurrentDraft()
                if (intent.action != AICommitAction.COPY_TO_CLIPBOARD &&
                    streamingState.originalText != "[Voice Recording]" &&
                    !streamingState.originalText.startsWith("COPIED MESSAGE TO REPLY TO:") &&
                    currentEditorText.isNotBlank() &&
                    currentEditorText != streamingState.originalText
                ) {
                    android.widget.Toast.makeText(this, "Text changed. Run the AI action again before applying it.", android.widget.Toast.LENGTH_LONG).show()
                    viewModel.cancelAIStreaming()
                    return
                }
                commitAIText(streamingState.streamedText, intent.action)
                viewModel.commitAIResult()
            }
            is KeyboardIntent.CancelAIStreaming -> viewModel.cancelAIStreaming()
            is KeyboardIntent.RetryAIStreaming -> viewModel.retryAIStreaming()
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
        action: AICommitAction
    ) {
        val ic = currentInputConnection ?: return
        typing.reset()
        when (action) {
            AICommitAction.REPLACE_DRAFT -> {
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
            AICommitAction.INSERT_BELOW -> {
                ic.commitText("\n\n$text", 1)
            }
            AICommitAction.COPY_TO_CLIPBOARD -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("NeuralKey AI Output", text)
                clipboard?.setPrimaryClip(clip)
                viewModel.recordCopiedText(text)
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
        val editor = ConnectionEditor(ic)

        // 1. Check Slash Command autocomplete (/email, /fix, /calc, /sql, /date, etc.)
        val beforeSlice = editor.beforeCursor(64)
        val slashMatches = SlashCommandEngine.suggestCommands(beforeSlice)
        if (slashMatches.isNotEmpty()) {
            viewModel.updateSuggestions(slashMatches)
            return
        }

        val word = typing.currentWord(editor)
        val prevWord = typing.previousWord(editor)
        val engine = (application as NeuralKeyApplication).suggestionEngine
        if (word == null) {
            if (state.nextWordPredictionEnabled && prevWord != null && editor.beforeCursor(1) == " " && engine != null) {
                suggestionJob = lifecycleScope.launch {
                    val nextWords = withContext(Dispatchers.Default) { engine.nextWordSuggestions(prevWord) }
                    val latest = viewModel.uiState.value
                    if (inputActive && latest.suggestionsEnabled && currentInputConnection === ic &&
                        typing.currentWord(ConnectionEditor(ic)) == null
                    ) {
                        viewModel.updateSuggestions(nextWords)
                    }
                }
            } else {
                viewModel.updateSuggestions(emptyList())
            }
            return
        }
        viewModel.updateSuggestions(listOf(word))
        if (engine == null) return
        suggestionJob = lifecycleScope.launch {
            val words = withContext(Dispatchers.Default) { engine.suggestions(word, prevWord) }
            val latest = viewModel.uiState.value
            if (inputActive && latest.suggestionsEnabled && currentInputConnection === ic &&
                typing.currentWord(ConnectionEditor(ic)) == word
            ) viewModel.updateSuggestions(words)
        }
    }

    companion object {
        private const val TAG = "NeuralKeyService"
        private const val MAX_DRAFT_LENGTH = 5000
    }
}
