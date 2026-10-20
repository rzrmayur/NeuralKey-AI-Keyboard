package com.supakeyboard.neuralkey.ime.typing

import android.text.InputType

object InputFieldPolicy {
    fun isSensitive(inputType: Int): Boolean {
        val type = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (type == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    fun allowsSuggestions(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT || isSensitive(inputType)) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 ||
            inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) return false
        return inputType and InputType.TYPE_MASK_VARIATION !in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER
        )
    }
}
