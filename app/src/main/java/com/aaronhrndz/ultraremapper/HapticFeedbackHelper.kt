package com.aaronhrndz.ultraremapper

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.lang.reflect.Method

object HapticFeedbackHelper {
    private const val TAG = "HapticFeedbackHelper"

    // Samsung One UI Spring pattern constants (captured from Galaxy Watch Ultra)
    // Hardware motor mType=50080 (sepIndex=56)
    private const val SAMSUNG_SPRING_VIB_TYPE = 50080
    private const val SAMSUNG_SPRING_SEP_INDEX = 56

    private var semCreateWaveformMethod: Method? = null
    private var semMagnitudeTypeNotification: Any? = null
    private var reflectionChecked = false

    private fun initReflection() {
        if (reflectionChecked) return
        reflectionChecked = true
        try {
            val semMagnitudeTypeClass = Class.forName("android.os.VibrationEffect\$SemMagnitudeType")
            val fieldNotification = semMagnitudeTypeClass.getField("TYPE_NOTIFICATION")
            semMagnitudeTypeNotification = fieldNotification.get(null)

            semCreateWaveformMethod = VibrationEffect::class.java.getMethod(
                "semCreateWaveform",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                semMagnitudeTypeClass
            )
        } catch (_: Throwable) {
            // Non-Samsung or method signature variation; fallback to standard haptics
        }
    }

    /**
     * Executes the exact Samsung One UI Spring haptic effect on Galaxy Watch devices.
     * Falls back to standard tactile feedback on non-Samsung or unsupported devices.
     */
    fun playButtonHaptic(context: Context) {
        if (!Mapping.isHapticFeedbackEnabled(context)) return

        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        initReflection()

        // 1. Try Samsung OEM SemHaptic (Spring: 50080)
        if (semCreateWaveformMethod != null && semMagnitudeTypeNotification != null) {
            try {
                val effect = semCreateWaveformMethod!!.invoke(
                    null,
                    SAMSUNG_SPRING_VIB_TYPE,
                    -1,
                    semMagnitudeTypeNotification
                ) as? VibrationEffect

                if (effect != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val attrs = VibrationAttributes.Builder()
                            .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                            .build()
                        vibrator.vibrate(effect, attrs)
                    } else {
                        vibrator.vibrate(effect)
                    }
                    return
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Samsung SemHaptic invocation fallback: ${t.message}")
            }
        }

        // 2. Standard Android fallback for non-Samsung devices or standard actuators
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Standard vibration fallback failed", t)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
