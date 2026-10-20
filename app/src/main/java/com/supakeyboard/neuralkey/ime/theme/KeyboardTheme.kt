package com.supakeyboard.neuralkey.ime.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class KeyboardTheme(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val backgroundBrush: Brush,
    val surfaceColor: Color,
    val keyBgColor: Color,
    val keyFunctionalBgColor: Color,
    val keyPressedBgColor: Color,
    val keyTextColor: Color,
    val keySecondaryTextColor: Color,
    val keyFunctionalTextColor: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val commandBarBg: Color,
    val previewCardBg: Color,
    val isDark: Boolean = true
)

object KeyboardThemes {

    val SYSTEM_DYNAMIC = KeyboardTheme(
        id = "SYSTEM_DYNAMIC", name = "Dynamic (Material You)",
        description = "Adapts to your device wallpaper colors in real-time", emoji = "🎨",
        backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF0F0C20), Color(0xFF07060D))), // Fallback
        surfaceColor = Color(0xFF0D0A1A), keyBgColor = Color(0xFF1A162B),
        keyFunctionalBgColor = Color(0xFF141122), keyPressedBgColor = Color(0xFF2C2448),
        keyTextColor = Color(0xFFF8FAFC), keySecondaryTextColor = Color(0xFF00E5FF),
        keyFunctionalTextColor = Color(0xFFE2E8F0),
        accentPrimary = Color(0xFF00E5FF), accentSecondary = Color(0xFFF43F5E),
        commandBarBg = Color(0xFF120E24), previewCardBg = Color(0xFF100C22), isDark = true
    )

    val CYBERPUNK_NEON = KeyboardTheme(
        id = "CYBERPUNK_NEON",
        name = "Cyberpunk Neon",
        description = "High-voltage electric cyan & neon fuchsia with glowing accents",
        emoji = "⚡",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0F0C20), Color(0xFF07060D))
        ),
        surfaceColor = Color(0xFF0D0A1A),
        keyBgColor = Color(0xFF1A162B),
        keyFunctionalBgColor = Color(0xFF141122),
        keyPressedBgColor = Color(0xFF2C2448),
        keyTextColor = Color(0xFFF8FAFC),
        keySecondaryTextColor = Color(0xFF00E5FF),
        keyFunctionalTextColor = Color(0xFFE2E8F0),
        accentPrimary = Color(0xFF00E5FF),
        accentSecondary = Color(0xFFF43F5E),
        commandBarBg = Color(0xFF120E24),
        previewCardBg = Color(0xFF100C22),
        isDark = true
    )

    val MIDNIGHT_OLED = KeyboardTheme(
        id = "MIDNIGHT_OLED",
        name = "Midnight OLED",
        description = "Pure pitch-black OLED power saver with crisp white typography",
        emoji = "🖤",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF000000), Color(0xFF000000))
        ),
        surfaceColor = Color(0xFF000000),
        keyBgColor = Color(0xFF121214),
        keyFunctionalBgColor = Color(0xFF0A0A0C),
        keyPressedBgColor = Color(0xFF242428),
        keyTextColor = Color(0xFFFFFFFF),
        keySecondaryTextColor = Color(0xFF71717A),
        keyFunctionalTextColor = Color(0xFFA1A1AA),
        accentPrimary = Color(0xFF38BDF8),
        accentSecondary = Color(0xFF818CF8),
        commandBarBg = Color(0xFF050507),
        previewCardBg = Color(0xFF0A0A0C),
        isDark = true
    )

    val AURORA_GLASS = KeyboardTheme(
        id = "AURORA_GLASS",
        name = "Aurora Glass",
        description = "Luminous northern lights emerald & sapphire frosted glass",
        emoji = "✨",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0B192C), Color(0xFF051120))
        ),
        surfaceColor = Color(0xFF081526),
        keyBgColor = Color(0xFF112642),
        keyFunctionalBgColor = Color(0xFF0C1B30),
        keyPressedBgColor = Color(0xFF1A3860),
        keyTextColor = Color(0xFFE6FFFA),
        keySecondaryTextColor = Color(0xFF2DD4BF),
        keyFunctionalTextColor = Color(0xFF99F6E4),
        accentPrimary = Color(0xFF2DD4BF),
        accentSecondary = Color(0xFF60A5FA),
        commandBarBg = Color(0xFF07182E),
        previewCardBg = Color(0xFF061426),
        isDark = true
    )

    val TOKYO_NIGHT = KeyboardTheme(
        id = "TOKYO_NIGHT",
        name = "Tokyo Night",
        description = "Iconic Japanese synth aesthetics with pastel blues & sakura pink",
        emoji = "🌸",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1A1B26), Color(0xFF13141C))
        ),
        surfaceColor = Color(0xFF161620),
        keyBgColor = Color(0xFF24283B),
        keyFunctionalBgColor = Color(0xFF1F2335),
        keyPressedBgColor = Color(0xFF3B4261),
        keyTextColor = Color(0xFFC0CAF5),
        keySecondaryTextColor = Color(0xFF7AA2F7),
        keyFunctionalTextColor = Color(0xFFA9B1D6),
        accentPrimary = Color(0xFF7AA2F7),
        accentSecondary = Color(0xFFF7768E),
        commandBarBg = Color(0xFF191A24),
        previewCardBg = Color(0xFF171722),
        isDark = true
    )

    val SUNSET_HORIZON = KeyboardTheme(
        id = "SUNSET_HORIZON",
        name = "Sunset Horizon",
        description = "Warm twilight dusk fading into coral pink & golden amber",
        emoji = "🌅",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1C1326), Color(0xFF110A1A))
        ),
        surfaceColor = Color(0xFF160E20),
        keyBgColor = Color(0xFF281C38),
        keyFunctionalBgColor = Color(0xFF1E142B),
        keyPressedBgColor = Color(0xFF402D59),
        keyTextColor = Color(0xFFFFF1F2),
        keySecondaryTextColor = Color(0xFFFB7185),
        keyFunctionalTextColor = Color(0xFFFDA4AF),
        accentPrimary = Color(0xFFFB7185),
        accentSecondary = Color(0xFFFBBF24),
        commandBarBg = Color(0xFF1B1126),
        previewCardBg = Color(0xFF180F22),
        isDark = true
    )

    val EMERALD_MATRIX = KeyboardTheme(
        id = "EMERALD_MATRIX",
        name = "Emerald Matrix",
        description = "Futuristic terminal obsidian with glowing radioactive green",
        emoji = "💚",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF08140E), Color(0xFF030A06))
        ),
        surfaceColor = Color(0xFF05100B),
        keyBgColor = Color(0xFF0E2419),
        keyFunctionalBgColor = Color(0xFF091A11),
        keyPressedBgColor = Color(0xFF173D2A),
        keyTextColor = Color(0xFFECFDF5),
        keySecondaryTextColor = Color(0xFF10B981),
        keyFunctionalTextColor = Color(0xFF6EE7B7),
        accentPrimary = Color(0xFF10B981),
        accentSecondary = Color(0xFF34D399),
        commandBarBg = Color(0xFF06140D),
        previewCardBg = Color(0xFF041009),
        isDark = true
    )

    val DEEP_SPACE = KeyboardTheme(
        id = "DEEP_SPACE",
        name = "Deep Space",
        description = "Cosmic nebula dark navy with royal ultraviolet & pulsar cyan",
        emoji = "🌌",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF090D1A))
        ),
        surfaceColor = Color(0xFF0B1120),
        keyBgColor = Color(0xFF1E293B),
        keyFunctionalBgColor = Color(0xFF172033),
        keyPressedBgColor = Color(0xFF334155),
        keyTextColor = Color(0xFFF8FAFC),
        keySecondaryTextColor = Color(0xFF818CF8),
        keyFunctionalTextColor = Color(0xFFCBD5E1),
        accentPrimary = Color(0xFF7C4DFF),
        accentSecondary = Color(0xFF00E5FF),
        commandBarBg = Color(0xFF0E1626),
        previewCardBg = Color(0xFF0C1322),
        isDark = true
    )

    val CHALK_STUDIO = KeyboardTheme(
        id = "CHALK_STUDIO",
        name = "Chalk Studio",
        description = "Clean Scandinavian studio light theme with royal blue accents",
        emoji = "☀️",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
        ),
        surfaceColor = Color(0xFFF8FAFC),
        keyBgColor = Color(0xFFFFFFFF),
        keyFunctionalBgColor = Color(0xFFE2E8F0),
        keyPressedBgColor = Color(0xFFCBD5E1),
        keyTextColor = Color(0xFF0F172A),
        keySecondaryTextColor = Color(0xFF64748B),
        keyFunctionalTextColor = Color(0xFF334155),
        accentPrimary = Color(0xFF2563EB),
        accentSecondary = Color(0xFF0EA5E9),
        commandBarBg = Color(0xFFF1F5F9),
        previewCardBg = Color(0xFFFFFFFF),
        isDark = false
    )

    val allThemes: List<KeyboardTheme> = listOf(
        SYSTEM_DYNAMIC,
        CYBERPUNK_NEON,
        MIDNIGHT_OLED,
        AURORA_GLASS,
        TOKYO_NIGHT,
        SUNSET_HORIZON,
        EMERALD_MATRIX,
        DEEP_SPACE,
        CHALK_STUDIO
    )

    fun getTheme(id: String): KeyboardTheme {
        return allThemes.find { it.id.equals(id, ignoreCase = true) } ?: CYBERPUNK_NEON
    }
}
