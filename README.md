# 🧠 NeuralKey AI Keyboard (SupaKeyboard)

> **Next-Generation OpenRouter Intelligent Android IME built with 100% Jetpack Compose, Kotlin 2.x, Material You, Room FTS5, and Low-Latency Touch Dispatch.**

[![Android](https://img.shields.io/badge/Platform-Android%2011%2B%20(API%2030--36)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.05.00-blue.svg)](https://developer.android.com/jetpack/compose)
[![OpenRouter](https://img.shields.io/badge/AI%20Engine-OpenRouter%20API-orange.svg)](https://openrouter.ai)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

---

## 🌟 Highlights

* **100% Jetpack Compose IME:** Entire keyboard UI, action panels, and streaming views rendered natively via `ComposeView` inside a lifecycle-aware `InputMethodService`.
* **Direct OpenRouter Streaming:** Zero intermediary proxy servers. Streams real-time Server-Sent Events (SSE) token by token directly to your keyboard with typewriter animation.
* **Hardware-Backed Keystore:** Your personal OpenRouter API key is encrypted using AES-GCM-256 with Android Keystore.
* **🎭 21 Pre-Seeded AI Action Prompts Across 4 Categories:**
  * **Productivity:** *Fix Grammar & Flow, Executive Polish, Punchy & Direct, Action Item Bullets, Reply to Email, TL;DR (Ultra Short)*
  * **Humor & Vibes:** *Roast Me, Gen-Z Slang, Gigachad, Overdramatic Shakespeare, Explain Like I'm 5, Pirate Speak*
  * **Styles:** *Polite & Soft, Academic Scholar, Cyberpunk Hacker, Poetic Verse, Casual & Chill*
  * **Multilingual:** *Instant translation to Spanish, French, German, Japanese*
* **🎨 9 Dynamic Modern Aesthetic Themes:**
  * *Dynamic (Material You)* — Adapts in real-time to your device wallpaper colors.
  * *Cyberpunk Neon* — High-voltage cyan & neon fuchsia.
  * *Midnight OLED* — Pitch-black pure AMOLED power saver.
  * *Aurora Glass* — Emerald & sapphire frosted glass.
  * *Tokyo Night* — Japanese synthwave pastel blues & sakura pink.
  * *Sunset Horizon* — Dusk twilight fading into coral & amber.
  * *Emerald Matrix* — Obsidian terminal with radioactive green.
  * *Deep Space* — Cosmic nebula dark navy with royal ultraviolet.
  * *Chalk Studio* — Clean Scandinavian light studio.
* **⚡ Spacebar Trackpad Cursor Glide:** Drag your thumb horizontally across the spacebar to glide the cursor with character precision.
* **⌫ Swipe & Accelerated Word Deletion:** Hold or swipe backspace for accelerated word-by-word deletion with distinct haptic ticks.
* **📳 Tactile Hardware Haptics:** Custom haptic feedback with real-time intensity slider (1–100%).
* **300+ Model Catalog Explorer:** Live search, free-only filter, and 1-tap model switching (Gemini 2.5 Flash, Llama 3.3 70B, Claude 3.5 Sonnet, DeepSeek Chat, and free-tier models).

---

## 🏗️ Architecture

```
com.supakeyboard.neuralkey/
├── NeuralKeyApplication.kt            # App singleton & dependency container
├── data/
│   ├── db/                            # Room database & FTS5 full-text search
│   ├── network/                       # Ktor 3 SSE streaming client & models
│   ├── preferences/                   # Jetpack DataStore preferences
│   ├── repository/                    # OpenRouter unified repository & cache
│   └── security/                      # Android Keystore AES-GCM encryption
├── ime/
│   ├── NeuralKeyInputMethodService.kt # Core Android InputMethodService
│   ├── KeyboardViewModel.kt           # Scoped state holder
│   ├── keyboard/                      # Normalized key grid, layouts & definitions
│   ├── mvi/                           # MVI state, intents & operational modes
│   ├── theme/                         # Theme definitions & dynamic palette engine
│   └── ui/
│       ├── KeyboardRootView.kt        # Rock-solid height container & dispatcher
│       ├── AICommandBar.kt            # Compact 44dp utility toolbar
│       ├── AIPromptSelectionPanel.kt  # 2-column action studio panel
│       ├── AIStreamingPreviewCard.kt  # Live token typewriter streaming view
│       └── InlineModelQuickSwitcher.kt# Fast model switcher
└── ui/settings/
    └── SettingsActivity.kt            # Setup wizard, model catalog, themes & prefs
```

---

## 🛠️ Building & Installing

### Prerequisites
* JDK 17 (Eclipse Temurin recommended)
* Android SDK 36 (Android 11+ baseline, target Android 15/16)
* Gradle 8.11+

### Build Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Build Optimized Release APK (R8 + Resource Shrinking)
```bash
./gradlew assembleRelease
```

---

## 📄 License

Licensed under the Apache License, Version 2.0.
