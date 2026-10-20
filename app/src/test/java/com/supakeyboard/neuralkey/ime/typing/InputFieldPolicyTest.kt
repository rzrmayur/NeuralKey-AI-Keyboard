package com.supakeyboard.neuralkey.ime.typing

import android.text.InputType.*
import org.junit.Assert.*
import org.junit.Test

class InputFieldPolicyTest {
    @Test fun normalAndChatFieldsAllowAssistance() {
        assertTrue(InputFieldPolicy.allowsSuggestions(TYPE_CLASS_TEXT))
        assertTrue(InputFieldPolicy.allowsSuggestions(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertTrue(InputFieldPolicy.allowsSuggestions(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE))
    }
    @Test fun passwordsAreSensitiveAndNeverAllowSuggestions() {
        for (variation in listOf(TYPE_TEXT_VARIATION_PASSWORD, TYPE_TEXT_VARIATION_WEB_PASSWORD, TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
            val type = TYPE_CLASS_TEXT or variation
            assertTrue(InputFieldPolicy.isSensitive(type))
            assertFalse(InputFieldPolicy.allowsSuggestions(type))
        }
        assertTrue(InputFieldPolicy.isSensitive(TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD))
    }
    @Test fun emailUrlNumericPhoneAndOptOutFieldsRemainLiteral() {
        for (type in listOf(0, TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI,
            TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_AUTO_COMPLETE)) {
            assertFalse("inputType=$type", InputFieldPolicy.allowsSuggestions(type))
        }
    }
}
