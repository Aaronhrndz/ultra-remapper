package com.aaronhrndz.ultraremapper

import com.aaronhrndz.ultraremapper.R

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Target for Samsung's `setting_action_button_press`; the system starts it on every Action button event.
 * ACTION_KEY_EVENT: 0=down, 1=up, 2=single click, 3=long press, 5=multi press (ACTION_KEY_COUNT).
 */
class ActionButtonService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyEvent = intent?.getIntExtra("ACTION_KEY_EVENT", -1) ?: -1
        val gesture = when (keyEvent) {
            2 -> "single"
            3 -> "long"
            5 -> if (intent?.getIntExtra("ACTION_KEY_COUNT", 0) == 2) "double" else null
            else -> null
        }

        val appEnabled = Mapping.isEnabled(this)
        val workoutActive = BottomButtonService.instance?.isSamsungHealthWorkoutActive() == true
        val singleAction = Mapping.get(this, "single")

        // 1. If master switch is OFF, always forward to stock Samsung Health
        if (!appEnabled) {
            forwardToSamsungHealth(intent)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // 2. If Samsung Health is in foreground or workout is actively ongoing:
        // ALWAYS forward hardware event directly to Samsung Health so Lap / Pause / Carousel work natively
        val inSamsungHealth = BottomButtonService.currentActivePackage == "com.samsung.android.wear.shealth" || workoutActive
        if (inSamsungHealth) {
            forwardToSamsungHealth(intent)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // 3. Normal gesture dispatch (each gesture is routed independently)
        if (gesture != null) {
            val configuredAction = Mapping.get(this, gesture)
            when (configuredAction) {
                "none" -> {
                    forwardToSamsungHealth(intent)
                }
                "shealth_workout" -> {
                    // Launch workout activity carousel/list reliably
                    Mapping.perform(this, gesture)
                }
                else -> Mapping.perform(this, gesture)
            }
            UpdateChecker.checkOpportunistic(applicationContext)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    /** Re-sends the hardware event exactly as received (action, data, extras) to Samsung Health's handler. */
    private fun forwardToSamsungHealth(originalIntent: Intent?): Boolean {
        if (originalIntent == null) return false
        return try {
            val shealthIntent = Intent(originalIntent).setComponent(
                android.content.ComponentName(
                    "com.samsung.android.wear.shealth",
                    "com.samsung.android.wear.shealth.service.ActionButtonService"
                )
            )
            startService(shealthIntent) != null
        } catch (_: Exception) {
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
