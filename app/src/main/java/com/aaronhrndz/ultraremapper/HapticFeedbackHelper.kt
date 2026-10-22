package com.aaronhrndz.ultraremapper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object HapticFeedbackHelper {
    private const val TAG = "HapticFeedback"
    private const val SPRING_CHANNEL_ID = "haptic_pulse_spring_v1"
    private const val SPRING_NOTIF_ID = 99991

    private val handler = Handler(Looper.getMainLooper())

    fun playButtonHaptic(context: Context) {
        if (!Mapping.isHapticFeedbackEnabled(context)) return

        try {
            ensureNotificationChannel(context)

            val builder = NotificationCompat.Builder(context, SPRING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(true)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(SPRING_NOTIF_ID, builder.build())

            // Cancelar inmediatamente la notificación visual para que solo actúe la vibración háptica
            handler.postDelayed({
                try {
                    notificationManager.cancel(SPRING_NOTIF_ID)
                } catch (_: Throwable) {}
            }, 30L)

        } catch (t: Throwable) {
            Log.e(TAG, "Native spring notification haptic failed: ${t.message}")
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(SPRING_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    SPRING_CHANNEL_ID,
                    "Haptic Feedback",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                    // Dejar vibrationPattern nulo para que el sistema Samsung aplique su patrón nativo (Spring)
                    description = "Canal de respuesta háptica nativa"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
