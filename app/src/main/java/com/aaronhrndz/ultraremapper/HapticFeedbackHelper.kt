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

            // 1. Android 11+ / Wear OS: Composition primitives for genuine hardware LRA haptics
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (vibrator.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    )
                ) {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.45f, 25)
                        .compose()

                    if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrator.vibrate(composition, attrs)
                    } else {
                        vibrator.vibrate(composition)
                    }
                    return
                }

                if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                        .compose()

                    if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrator.vibrate(composition, attrs)
                    } else {
                        vibrator.vibrate(composition)
                    }
                    return
                }
            }

            // 2. Predefined native system click
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(click, attrs)
                } else {
                    vibrator.vibrate(click)
                }
                return
            }

            // 3. Fallback: single sharp 12ms pulse
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val oneShot = VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(oneShot)
                return
            }

            @Suppress("DEPRECATION")
            vibrator.vibrate(12L)
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
