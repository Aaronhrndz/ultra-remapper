package com.bennybar.ultraremapper

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

        if (!appEnabled || (workoutActive && gesture == "single")) {
            // Forward raw intent to stock Samsung Health ActionButtonService
            forwardToSamsungHealth(intent)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (gesture != null) {
            Mapping.perform(this, gesture)
            UpdateChecker.checkOpportunistic(applicationContext)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun forwardToSamsungHealth(originalIntent: Intent?) {
        try {
            val shealthIntent = Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.wear.shealth",
                    "com.samsung.android.wear.shealth.service.ActionButtonService"
                )
                if (originalIntent != null) {
                    putExtras(originalIntent)
                }
            }
            startService(shealthIntent)
        } catch (_: Exception) {
            // If service has strict signature permission, launch workout screen fallback
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.samsung.android.wear.shealth")
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchIntent != null) startActivity(launchIntent)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
