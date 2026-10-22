package com.aaronhrndz.ultraremapper

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants

object HapticFeedbackHelper {

    // Spring pattern parameters (simulates an elastic spring with rapid physical decay)
    // Timings in ms and amplitudes [0-255]
    private val SPRING_TIMINGS = longArrayOf(0, 20, 25, 20, 25, 20)
    private val SPRING_AMPLITUDES = intArrayOf(0, 255, 60, 180, 40, 90)

    fun playSpringHaptic(context: Context) {
        if (!Mapping.isHapticFeedbackEnabled(context)) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            // 1. Check if device supports custom amplitude waveforms (Galaxy Watch Ultra/Ultra 2/Watch 6/etc.)
            if (vibrator.hasAmplitudeControl()) {
                val effect = VibrationEffect.createWaveform(SPRING_TIMINGS, SPRING_AMPLITUDES, -1)
                vibrator.vibrate(effect)
                return
            }

            // 2. Prebaked or standard tick fallback
            val fallback = VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(fallback)
        } catch (_: Throwable) {
            // Absolute safety: never crash service on haptic call
        }
    }
}
