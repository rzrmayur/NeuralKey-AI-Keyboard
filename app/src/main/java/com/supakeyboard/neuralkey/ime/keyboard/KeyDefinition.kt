package com.supakeyboard.neuralkey.ime.keyboard

import androidx.compose.runtime.Immutable

enum class KeyType {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    SPACE,
    ENTER,
    SYMBOLS_TOGGLE,
    SYMBOLS_PAGE_TOGGLE,
    SWITCH_IME,
    COMMA,
    PERIOD,
    SPACER
}

@Immutable
data class KeyDefinition(
    val primaryText: String,
    val secondaryText: String? = null,
    val type: KeyType = KeyType.CHARACTER,
    val weight: Float = 1.0f,
    val isFunctional: Boolean = false
) {
    fun getDisplayText(isShifted: Boolean, isCapsLocked: Boolean): String {
        return if (type == KeyType.CHARACTER && (isShifted || isCapsLocked)) {
            primaryText.uppercase()
        } else {
            primaryText
        }
    }
}
