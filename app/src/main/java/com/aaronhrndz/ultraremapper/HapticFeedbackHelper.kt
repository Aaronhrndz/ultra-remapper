package com.aaronhrndz.ultraremapper

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method

object HapticFeedbackHelper {
    private const val TAG = "HapticFeedback"

    fun playButtonHaptic(context: Context) {
        if (!Mapping.isHapticFeedbackEnabled(context)) return

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            // 1. Intentar llamar al método nativo semVibrate de Samsung para reproducir el patrón oficial del sistema
            if (trySamsungSemVibrate(context, vibrator)) {
                return
            }

            // 2. Fallback de alta fidelidad: Composition Primitives (Wear OS / Android 11+)
            val attrs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                    .build()
            } else null

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
            }

            // 3. Fallback: Predefined system click
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(click, attrs)
                } else {
                    vibrator.vibrate(click)
                }
                return
            }

            // 4. Fallback básico
            @Suppress("DEPRECATION")
            vibrator.vibrate(15L)
        } catch (t: Throwable) {
            Log.e(TAG, "Haptic pulse failed: ${t.message}")
        }
    }

    /**
     * Invoca el motor nativo de Samsung (One UI / SEP) a través de semVibrate.
     * Lee el patrón de notificación configurado por el usuario en Ajustes (Spring u otro).
     */
    private fun trySamsungSemVibrate(context: Context, vibrator: Vibrator): Boolean {
        return try {
            val sepIndex = getSystemNotificationVibrationIndex(context)
            val vibratorClass = vibrator.javaClass

            // Buscar métodos semVibrate disponibles en el Vibrator de Samsung
            val methods = vibratorClass.methods.filter { it.name.startsWith("semVibrate") }
            for (m in methods) {
                val params = m.parameterTypes
                try {
                    when (params.size) {
                        // semVibrate(int effectIndex, int repeat, int magnitude, AudioAttributes / VibrationAttributes)
                        4 -> {
                            m.isAccessible = true
                            m.invoke(vibrator, sepIndex, -1, 4000, null)
                            return true
                        }
                        // semVibrate(int effectIndex, int repeat, int magnitude)
                        3 -> {
                            if (params[0] == Int::class.javaPrimitiveType &&
                                params[1] == Int::class.javaPrimitiveType &&
                                params[2] == Int::class.javaPrimitiveType
                            ) {
                                m.isAccessible = true
                                m.invoke(vibrator, sepIndex, -1, 4000)
                                return true
                            }
                        }
                        // semVibrate(int effectIndex, int repeat)
                        2 -> {
                            if (params[0] == Int::class.javaPrimitiveType &&
                                params[1] == Int::class.javaPrimitiveType
                            ) {
                                m.isAccessible = true
                                m.invoke(vibrator, sepIndex, -1)
                                return true
                            }
                        }
                        // semVibrate(int effectIndex)
                        1 -> {
                            if (params[0] == Int::class.javaPrimitiveType) {
                                m.isAccessible = true
                                m.invoke(vibrator, sepIndex)
                                return true
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Probar siguiente sobrecarga
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Obtiene el índice SEP del patrón de vibración configurado en Ajustes de One UI.
     * Si no existe o da error, retorna 50034 (índice base de notificación de Samsung).
     */
    private fun getSystemNotificationVibrationIndex(context: Context): Int {
        return try {
            val idx = Settings.System.getInt(
                context.contentResolver,
                "notification_vibration_sep_index",
                -1
            )
            if (idx > 0) idx else 50034
        } catch (_: Exception) {
            50034
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
