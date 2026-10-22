package com.aaronhrndz.ultraremapper

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object HapticFeedbackHelper {
    private const val TAG = "HapticFeedback"

    // Elastic physical spring bounce simulation for Linear Resonant Actuators
    private val SPRING_TIMINGS = longArrayOf(0, 18, 22, 18, 22, 16)
    private val SPRING_AMPLITUDES = intArrayOf(0, 255, 45, 170, 30, 80)

    fun playButtonHaptic(context: Context) {
        if (!Mapping.isHapticFeedbackEnabled(context)) return

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            val attrs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
            } else null

            // 1. If amplitude control supported (Galaxy Watch Ultra / Watch 6 / etc.), play sharp physical bounce
            if (vibrator.hasAmplitudeControl()) {
                val wave = VibrationEffect.createWaveform(SPRING_TIMINGS, SPRING_AMPLITUDES, -1)
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(wave, attrs)
                } else {
                    vibrator.vibrate(wave)
                }
                return
            }

            // 2. Prebaked system click
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(click, attrs)
                } else {
                    vibrator.vibrate(click)
                }
                return
            }

            // 3. Raw pulse fallback
            @Suppress("DEPRECATION")
            vibrator.vibrate(25L)
        } catch (t: Throwable) {
            Log.e(TAG, "Haptic pulse failed: ${t.message}")
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
