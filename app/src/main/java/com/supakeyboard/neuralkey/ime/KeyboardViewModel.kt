package com.supakeyboard.neuralkey.ime

import android.app.Application
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.supakeyboard.neuralkey.NeuralKeyApplication
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import com.supakeyboard.neuralkey.ime.mvi.KeyboardMode
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState
import com.supakeyboard.neuralkey.ime.mvi.OneHandedMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardThemes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeyboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app: NeuralKeyApplication? by lazy {
        try {
            getApplication<NeuralKeyApplication>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get NeuralKeyApplication, falling back to instance", e)
            if (NeuralKeyApplication::class.java.isInstance(application)) {
                application as NeuralKeyApplication
            } else {
                null
            }
        }
    }
    private val repository get() = app?.openRouterRepository
    private val preferences get() = app?.keyboardPreferences

    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    init {
        val repo = repository
        val prefs = preferences
        if (repo != null && prefs != null) {
            viewModelScope.launch {
                prefs.suggestionsEnabled.collect { enabled ->
                    _uiState.update { it.copy(suggestionsEnabled = enabled, suggestions = if (enabled) it.suggestions else emptyList()) }
                }
            }
            viewModelScope.launch {
                prefs.autoCorrectionEnabled.collect { enabled ->
                    _uiState.update { it.copy(autoCorrectionEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                prefs.keyMemoryEnabled.collect { enabled ->
                    _uiState.update { it.copy(keyMemoryEnabled = enabled) }
                    runCatching { com.supakeyboard.neuralkey.NeuralKeyApplication.instance.suggestionEngine?.keyMemoryEnabled = enabled }
                }
            }
            viewModelScope.launch {
                prefs.nextWordPredictionEnabled.collect { enabled ->
                    _uiState.update { it.copy(nextWordPredictionEnabled = enabled) }
                    runCatching { com.supakeyboard.neuralkey.NeuralKeyApplication.instance.suggestionEngine?.nextWordPredictionEnabled = enabled }
                }
            }
            viewModelScope.launch {
                prefs.oneHandedMode.collect { mode ->
                    _uiState.update { it.copy(oneHandedMode = mode) }
                }
            }
            viewModelScope.launch {
                prefs.splitKeyboardOnFold.collect { split ->
                    _uiState.update { it.copy(splitKeyboardOnFold = split) }
                }
            }
            // Use individual launch/collect for robustness — avoids fragile chained combine
            viewModelScope.launch {
                repo.activeModel.collect { model ->
                    _uiState.update { it.copy(activeModel = model) }
                }
            }
            viewModelScope.launch {
                repo.pinnedModels.collect { pinned ->
                    _uiState.update { it.copy(pinnedModels = pinned) }
                }
            }
            viewModelScope.launch {
                repo.allModels.collect { all ->
                    _uiState.update { it.copy(availableModels = all) }
                }
            }
            viewModelScope.launch {
                repo.userPrompts.collect { prompts ->
                    _uiState.update { it.copy(userPrompts = prompts) }
                }
            }
            viewModelScope.launch {
                prefs.hapticFeedbackEnabled.collect { enabled ->
                    _uiState.update { it.copy(hapticEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                prefs.hapticIntensity.collect { intensity ->
                    _uiState.update { it.copy(hapticIntensity = intensity) }
                }
            }
            viewModelScope.launch {
                prefs.freeModelsOnly.collect { freeOnly ->
                    _uiState.update { it.copy(freeModelsOnly = freeOnly) }
                }
            }
            viewModelScope.launch {
                prefs.selectedTheme.collect { themeId ->
                    val theme = KeyboardThemes.getTheme(themeId)
                    _uiState.update { it.copy(currentTheme = theme) }
                }
            }
        } else {
            Log.w(TAG, "Repository or Preferences not available; skipping flow observation")
        }
    }

    fun updateInputContext(packageName: String?, editorInfo: EditorInfo?) {
        _uiState.update { current ->
            current.copy(
                targetPackageName = packageName,
                imeAction = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_UNSPECIFIED
            )
        }
    }

    fun updateDraft(draft: String) {
        _uiState.update { it.copy(currentDraft = draft) }
    }

    fun updateInputType(inputType: Int) {
        _uiState.update { it.copy(inputType = inputType) }
    }

    fun onAppSwitch(targetPackage: String?) {
        // Editor content and AI requests must never survive a focus/app change.
        cancelAIStreaming()
        _uiState.update {
            it.copy(targetPackageName = targetPackage, currentDraft = "", suggestions = emptyList(),
                isShifted = false, isCapsLocked = false, isSymbols = false, isSymbolsPage2 = false)
        }
    }

    fun updateSuggestions(words: List<String>) { _uiState.update { it.copy(suggestions = words) } }
    fun setSensitiveField(sensitive: Boolean) { _uiState.update { it.copy(isSensitiveField = sensitive) } }
    fun characterTyped() {
        _uiState.update { if (it.isCapsLocked) it else it.copy(isShifted = false) }
    }

    // ── State-mutating intent handlers ──────────────────────────────

    private var lastShiftTap = 0L
    fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        _uiState.update {
            when {
                it.isCapsLocked -> it.copy(isShifted = false, isCapsLocked = false)
                it.isShifted && now - lastShiftTap < 350 -> it.copy(isShifted = true, isCapsLocked = true)
                else -> it.copy(isShifted = !it.isShifted, isCapsLocked = false)
            }
        }
        lastShiftTap = now
    }

    fun toggleSymbols() {
        _uiState.update { it.copy(isSymbols = !it.isSymbols, isSymbolsPage2 = false) }
    }

    fun switchSymbolsPage() {
        _uiState.update { it.copy(isSymbolsPage2 = !it.isSymbolsPage2) }
    }

    fun setOneHandedMode(mode: OneHandedMode) {
        _uiState.update { it.copy(oneHandedMode = mode) }
        val prefs = preferences ?: return
        viewModelScope.launch { prefs.setOneHandedMode(mode) }
    }

    fun cycleOneHandedMode() {
        val next = when (_uiState.value.oneHandedMode) {
            OneHandedMode.OFF -> OneHandedMode.RIGHT
            OneHandedMode.RIGHT -> OneHandedMode.LEFT
            OneHandedMode.LEFT -> OneHandedMode.OFF
        }
        setOneHandedMode(next)
    }

    fun toggleSplitLayout() {
        val next = !_uiState.value.splitKeyboardOnFold
        _uiState.update { it.copy(splitKeyboardOnFold = next) }
        val prefs = preferences ?: return
        viewModelScope.launch { prefs.setSplitKeyboardOnFold(next) }
    }

    fun openAIMenu(draft: String) {
        _uiState.update {
            it.copy(
                currentDraft = draft,
                mode = KeyboardMode.AIMenuMode(draftText = draft, actions = it.userPrompts)
            )
        }
    }

    fun closeAIMenu() {
        _uiState.update { it.copy(mode = KeyboardMode.TypingMode()) }
    }

    fun triggerAIAction(prompt: UserPromptEntity, fallbackDraft: String) {
        val draft = _uiState.value.currentDraft.ifBlank { fallbackDraft }
        startAIStreaming(prompt.promptInstruction, draft)
    }

    fun triggerCustomPrompt(customInstruction: String, fallbackDraft: String) {
        val draft = _uiState.value.currentDraft.ifBlank { fallbackDraft }
        startAIStreaming(customInstruction, draft)
    }

    fun commitAIResult() {
        _uiState.update { it.copy(mode = KeyboardMode.TypingMode()) }
    }

    fun cancelAIStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update { it.copy(mode = KeyboardMode.TypingMode()) }
    }

    fun openModelPicker() {
        _uiState.update { it.copy(mode = KeyboardMode.ModelPickerMode()) }
    }

    fun closeModelPicker() {
        _uiState.update { it.copy(mode = KeyboardMode.TypingMode()) }
    }

    fun selectModel(modelId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.setActiveModel(modelId)
            _uiState.update { it.copy(mode = KeyboardMode.TypingMode()) }
        }
    }

    fun updateModelSearch(query: String) {
        val currentMode = _uiState.value.mode
        if (currentMode is KeyboardMode.ModelPickerMode) {
            _uiState.update { it.copy(mode = currentMode.copy(searchQuery = query)) }
        }
    }

    fun toggleFreeModelsOnly() {
        val prefs = preferences ?: return
        viewModelScope.launch {
            val current = _uiState.value.freeModelsOnly
            prefs.setFreeModelsOnly(!current)
        }
    }

    fun updateTargetContext(packageName: String?, imeAction: Int) {
        _uiState.update {
            it.copy(
                targetPackageName = packageName,
                imeAction = imeAction
            )
        }
    }

    fun draftChanged(draftText: String) {
        _uiState.update { it.copy(currentDraft = draftText) }
    }

    // ── AI Streaming ─────────────────────────────────────────────────

    fun startAIStreaming(promptInstruction: String, draftText: String) {
        val repo = repository ?: return
        val modelName = _uiState.value.activeModel?.name ?: "OpenRouter AI"
        _uiState.update {
            it.copy(
                mode = KeyboardMode.AIStreamingMode(
                    originalText = draftText,
                    streamedText = "",
                    isStreaming = true,
                    modelUsed = modelName
                )
            )
        }

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                repo.streamAIAction(
                    promptInstruction = promptInstruction,
                    draftText = draftText,
                    targetPackageName = _uiState.value.targetPackageName
                ).collect { token ->
                    _uiState.update { state ->
                        val currentStream = state.mode as? KeyboardMode.AIStreamingMode
                        if (currentStream != null) {
                            state.copy(
                                mode = currentStream.copy(
                                    streamedText = currentStream.streamedText + token
                                )
                            )
                        } else state
                    }
                }

                _uiState.update { state ->
                    val currentStream = state.mode as? KeyboardMode.AIStreamingMode
                    if (currentStream != null) {
                        state.copy(mode = currentStream.copy(isStreaming = false))
                    } else state
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Streaming error", e)
                _uiState.update { state ->
                    val currentStream = state.mode as? KeyboardMode.AIStreamingMode
                    if (currentStream != null) {
                        state.copy(
                            mode = currentStream.copy(
                                isStreaming = false,
                                error = e.localizedMessage ?: "Inference failed"
                            )
                        )
                    } else state
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        streamingJob = null
    }

    companion object {
        private const val TAG = "KeyboardViewModel"
    }
}
