package com.aaronhrndz.ultraremapper

import com.aaronhrndz.ultraremapper.R

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Handles background install trigger from notification action and schedules daily passive update checks.
 */
class UpdateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_UPDATE = "com.aaronhrndz.ultraremapper.action.INSTALL_UPDATE"
        const val ACTION_CHECK_UPDATE = "com.aaronhrndz.ultraremapper.action.CHECK_UPDATE"
        const val EXTRA_VERSION = "extra_version"
        const val EXTRA_APK_URL = "extra_apk_url"
        const val CHANNEL_ID = "app_updates_channel"
        const val NOTIFICATION_ID = 2026

        private val executor = Executors.newSingleThreadExecutor()


        fun showUpdateNotification(context: Context, update: ReleaseUpdate) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_update_desc)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)

            // Tap notification body -> Open MainActivity with update details
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_destination", "update_details")
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notif_update_title, update.versionName))
                .setContentText(context.getString(R.string.notif_update_desc))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)

            // Direct install action button right from notification
            if (!update.apkDownloadUrl.isNullOrEmpty()) {
                val installIntent = Intent(context, UpdateReceiver::class.java).apply {
                    action = ACTION_INSTALL_UPDATE
                    putExtra(EXTRA_VERSION, update.versionName)
                    putExtra(EXTRA_APK_URL, update.apkDownloadUrl)
                }
                val installPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    R.mipmap.ic_launcher,
                    context.getString(R.string.notif_action_install),
                    installPendingIntent
                )
            }

            nm.notify(NOTIFICATION_ID, builder.build())
        }

        fun clearNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext

        when (action) {
            ACTION_INSTALL_UPDATE -> {
                val version = intent.getStringExtra(EXTRA_VERSION) ?: return
                val apkUrl = intent.getStringExtra(EXTRA_APK_URL) ?: return
                clearNotification(appContext)
                val update = ReleaseUpdate(
                    versionName = version,
                    htmlUrl = "https://github.com/Aaronhrndz/ultra-remapper/releases/latest",
                    apkDownloadUrl = apkUrl,
                    releaseNotes = ""
                )
                UpdateChecker.downloadAndInstallOnWatch(appContext, update)
            }
            ACTION_CHECK_UPDATE, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                UpdateChecker.scheduleDailyCheck(appContext)
                UpdateChecker.checkOpportunistic(appContext)
                ensureActionButtonRouting(appContext)
            }
        }
    }

    private fun ensureActionButtonRouting(context: Context) {
        if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            val isMasterOn = Mapping.isEnabled(context)
            val target = if (!isMasterOn) {
                "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"
            } else {
                "${context.packageName}/${ActionButtonService::class.java.name}"
            }
            val current = android.provider.Settings.Global.getString(context.contentResolver, "setting_action_button_press")
            if (current != target) {
                android.provider.Settings.Global.putString(context.contentResolver, "setting_action_button_press", target)
            }
        } catch (_: Throwable) {}
    }
}
