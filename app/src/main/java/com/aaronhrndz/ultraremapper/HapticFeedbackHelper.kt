package com.aaronhrndz.ultraremapper

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Motor háptico de impacto seco y agresivo calibrado para el actuador LRA (Cirrus Logic CS40L27R)
 * del Samsung Galaxy Watch Ultra / Ultra 2.
 *
 * Perfil mecánico:
 * - Ataque contundente a máxima potencia (1.0f / 255) para excitar la masa del LRA al instante.
 * - Freno activo y rebotes secos ultra-cortos de alta amortiguación (sensación elástica agresiva y directa).
 */
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

            // 1. Composición de Primitivas agresiva y potente con THUD y CLICK de hardware
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasClick = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
                val hasThud = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)

                if (hasClick) {
                    val comp = VibrationEffect.startComposition()
                        // Golpe seco inicial contundente al 100% de fuerza
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)

                    if (hasThud) {
                        // Impacto de tope mecánico inmediato a los 12ms para dar sensación de cuerpo pesado
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, 12)
                    }

                    // Rebote elástico seco secundario a los 20ms
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.65f, 20)

                    val effect = comp.compose()
                    vibrator.cancel()
                    if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrator.vibrate(effect, attrs)
                    } else {
                        vibrator.vibrate(effect)
                    }
                    return
                }
            }

            // 2. Waveform PWM de alta potencia a 255 con pulsos secos (Full Power LRA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                // Secuencia muy agresiva: Golpe máximo de 35ms -> Pausa 14ms -> Rebote tenso 22ms -> Pausa 14ms -> Micro impacto final 12ms
                val timings = longArrayOf(0, 35, 14, 22, 14, 12)
                val amplitudes = intArrayOf(0, 255, 0, 210, 0, 140)

                val waveform = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.cancel()
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(waveform, attrs)
                } else {
                    vibrator.vibrate(waveform)
                }
                return
            }

            // 3. Fallback: Predefinido pesado
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val heavyClick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                vibrator.cancel()
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(heavyClick, attrs)
                } else {
                    vibrator.vibrate(heavyClick)
                }
                return
            }

            @Suppress("DEPRECATION")
            vibrator.vibrate(40L)
        } catch (t: Throwable) {
            Log.e(TAG, "Aggressive spring haptic failure: ${t.message}")
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

    fun playRotaryTick(context: Context) {
        if (!Mapping.isHapticFeedbackEnabled(context)) return
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f)
                vibrator.vibrate(comp.compose())
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10, 100))
                return
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(10L)
        } catch (_: Throwable) {}
    }
}
