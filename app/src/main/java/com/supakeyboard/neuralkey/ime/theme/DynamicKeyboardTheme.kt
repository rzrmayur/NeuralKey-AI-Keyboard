package com.supakeyboard.neuralkey.ime.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView

/**
 * Creates a KeyboardTheme derived from the device's Material You dynamic colors.
 * Falls back to Cyberpunk Neon if dynamic colors aren't available.
 */
fun createDynamicKeyboardTheme(context: Context, isDark: Boolean = true): KeyboardTheme {
    val fallback = KeyboardThemes.CYBERPUNK_NEON
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return fallback

    return try {
        val colorScheme = if (isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }

        val accent = colorScheme.primary
        val accent2 = colorScheme.tertiary
        val surface = colorScheme.surface
        val onSurface = colorScheme.onSurface

        KeyboardTheme(
            id = "SYSTEM_DYNAMIC",
            name = "Dynamic (Material You)",
            description = "Adapts to your device wallpaper colors in real-time",
            emoji = "🎨",
            backgroundBrush = Brush.verticalGradient(
                listOf(surface, Color(surface.toArgb()).copy(alpha = 0.95f))
            ),
            surfaceColor = surface.copy(alpha = 0.8f),
            keyBgColor = surface.copy(alpha = 0.6f),
            keyFunctionalBgColor = surface.copy(alpha = 0.4f),
            keyPressedBgColor = accent.copy(alpha = 0.3f),
            keyTextColor = onSurface,
            keySecondaryTextColor = accent.copy(alpha = 0.7f),
            keyFunctionalTextColor = onSurface.copy(alpha = 0.7f),
            accentPrimary = accent,
            accentSecondary = accent2,
            commandBarBg = surface.copy(alpha = 0.9f),
            previewCardBg = surface.copy(alpha = 0.85f),
            isDark = isDark
        )
    } catch (e: Exception) {
        fallback
    }
}