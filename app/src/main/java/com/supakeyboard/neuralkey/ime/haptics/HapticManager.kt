package com.supakeyboard.neuralkey.ime.haptics

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * High-performance haptics manager delivering ultra-crisp tactile feedback
 * for key taps, cursor scrubbing, and long-press actions.
 */
class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Crisp key tap haptic. Uses hardware-calibrated click or scaled amplitude.
     */
    fun performKeyTap(view: View?, enabled: Boolean, intensity: Int) {
        if (!enabled) return

        if (view != null && view.isHapticFeedbackEnabled) {
            val flags = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            val performed = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags)
            if (performed) return
        }

        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                val amplitude = ((intensity.coerceIn(1, 100) / 100f) * 255).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(15, amplitude)
                vib.vibrate(effect)
            }
        }
    }

    /**
     * Subtle tick haptic for character-by-character spacebar cursor trackpad scrubbing.
     */
    fun performCursorTick(view: View?, enabled: Boolean) {
        if (!enabled) return

        if (view != null && view.isHapticFeedbackEnabled) {
            val flags = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            val performed = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, flags)
            if (performed) return
        }

        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                val effect = VibrationEffect.createOneShot(8, 70)
                vib.vibrate(effect)
            }
        }
    }

    /**
     * Heavy haptic for long-press trigger.
     */
    fun performLongPress(view: View?, enabled: Boolean) {
        if (!enabled) return

        if (view != null && view.isHapticFeedbackEnabled) {
            val flags = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            val performed = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, flags)
            if (performed) return
        }

        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                val effect = VibrationEffect.createOneShot(35, 200)
                vib.vibrate(effect)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: HapticManager? = null

        fun getInstance(context: Context): HapticManager {
            return instance ?: synchronized(this) {
                instance ?: HapticManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
