package com.aaronhrndz.ultraremapper

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Motor háptico optimizado para el actuador LRA (Cirrus Logic CS40L27) del Galaxy Watch Ultra.
 *
 * Simula el retroceso mecánico de un resorte (Spring) con extrema precisión:
 * - Pulso inicial nítido y enérgico (impacto/tensión).
 * - 3 oscilaciones de amortiguación física decreciente rápida con tiempos de ciclo de resonancia LRA (~210 Hz).
 * - Cero latencia (disparo síncrono directo sin pasar por NotificationManager ni colas de sistema).
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

            // 1. Alta fidelidad mediante Primitivas de Composición de Hardware (LRA DSP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val neededPrimitives = intArrayOf(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK
                )

                if (vibrator.areAllPrimitivesSupported(*neededPrimitives)) {
                    // Simulación física de rebote de muelle:
                    // Click fuerte (1.0) -> Rebound 1 (0.45 a los 16ms) -> Rebound 2 (0.22 a los 22ms) -> Micro-tick final (0.10 a los 26ms)
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.45f, 16)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.22f, 22)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.10f, 26)
                        .compose()

                    vibrator.cancel()
                    if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrator.vibrate(composition, attrs)
                    } else {
                        vibrator.vibrate(composition)
                    }
                    return
                }
            }

            // 2. Simulación analógica con Waveform y Modulación de Amplitud PWM (LRA tuned)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                // Tiempos ajustados al período de resonancia (~4.7ms por semiciclo a 210Hz)
                // Patrón alterno de oscilación y amortiguación elástica:
                val timings = longArrayOf(0, 18, 12, 12, 16, 8, 22, 5)
                val amplitudes = intArrayOf(0, 255, 0, 150, 0, 75, 0, 30)

                val waveform = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.cancel()
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(waveform, attrs)
                } else {
                    vibrator.vibrate(waveform)
                }
                return
            }

            // 3. Fallback inmediato predefinido
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator.cancel()
                if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(click, attrs)
                } else {
                    vibrator.vibrate(click)
                }
                return
            }

            @Suppress("DEPRECATION")
            vibrator.vibrate(18L)
        } catch (t: Throwable) {
            Log.e(TAG, "Spring haptic failure: ${t.message}")
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
