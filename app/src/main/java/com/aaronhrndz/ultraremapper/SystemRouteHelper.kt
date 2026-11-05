package com.aaronhrndz.ultraremapper

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object SystemRouteHelper {
    const val SAMSUNG_HEALTH_ACTION_SERVICE = "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"
    const val DEFAULT_RECENT_APPS_ALIAS = "com.samsung.android.apps.wearable.recent/com.samsung.android.apps.wearable.recent.RecentActivityAlias"

    fun hasHardwareActionButton(context: Context): Boolean = true

    fun hasSecureSettingsPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun syncSamsungRoutes(context: Context) {
        if (!hasSecureSettingsPermission(context)) return
        try {
            val isMasterOn = Mapping.isEnabled(context)

            // 1. Top button long press (Assistant route)
            val topLongAction = Mapping.get(context, "top_long")
            if (isMasterOn && topLongAction != "none") {
                val currentAssistant = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
                    ?: Settings.Secure.getString(context.contentResolver, "assistant")
                val isOurs = currentAssistant?.contains(context.packageName) == true
                if (isOurs) {
                    Settings.Global.putString(context.contentResolver, "setting_home_key_long_press", "googlevoiceassistant/googlevoiceassistant")
                }
            }

            // 2. Top button double press
            val topDoubleAction = Mapping.get(context, "top_double")
            val expectedTopDouble = if (isMasterOn && topDoubleAction != "none") {
                "${context.packageName}/${TopDoubleTrampolineActivity::class.java.name}"
            } else {
                DEFAULT_RECENT_APPS_ALIAS
            }
            val currentTopDouble = Settings.Global.getString(context.contentResolver, "setting_home_key_double_press")
            if (currentTopDouble != expectedTopDouble) {
                Settings.Global.putString(context.contentResolver, "setting_home_key_double_press", expectedTopDouble)
            }

            // 3. Toc-toc (Wrist Knock gesture)
            val toctocAction = Mapping.get(context, "toctoc")
            val toctocTarget = "${context.packageName}/${TocTocTrampolineActivity::class.java.name}"
            if (isMasterOn && toctocAction != "none") {
                Settings.Global.putString(context.contentResolver, "setting_gesture_launch_on", "1")
                Settings.Global.putString(context.contentResolver, "setting_gesture_launch_app", toctocTarget)
                Settings.Global.putString(context.contentResolver, "setting_gesture_launch_selected_app", toctocTarget)
            } else if (!isMasterOn || toctocAction == "none") {
                val currentLaunchApp = Settings.Global.getString(context.contentResolver, "setting_gesture_launch_app")
                if (currentLaunchApp?.contains(context.packageName) == true) {
                    Settings.Global.putString(context.contentResolver, "setting_gesture_launch_on", "0")
                    Settings.Global.putString(context.contentResolver, "setting_gesture_launch_app", "fixed/turn_on_flashlight")
                    Settings.Global.putString(context.contentResolver, "setting_gesture_launch_selected_app", "")
                }
            }
        } catch (_: Throwable) {}
    }

    fun syncTopButtonRoute(context: Context) = syncSamsungRoutes(context)

    fun isActionButtonRouted(context: Context, autoFix: Boolean = true): Boolean {
        if (!hasHardwareActionButton(context)) return true

        // Do NOT fight with Samsung Health if workout is currently active
        if (BottomButtonService.instance?.isSamsungHealthWorkoutActive() == true) {
            return true
        }

        val remapperTarget = "${context.packageName}/${ActionButtonService::class.java.name}"
        val isMasterOn = Mapping.isEnabled(context)
        val expected = if (!isMasterOn) {
            SAMSUNG_HEALTH_ACTION_SERVICE
        } else {
            remapperTarget
        }

        val current = try {
            Settings.Global.getString(context.contentResolver, "setting_action_button_press")
        } catch (_: Throwable) {
            null
        }
        if (current == expected) return true

        if (autoFix && hasSecureSettingsPermission(context)) {
            try {
                Settings.Global.putString(context.contentResolver, "setting_action_button_press", expected)
                return true
            } catch (_: Throwable) {}
        }
        return false
    }

    fun onMasterToggle(context: Context, enabled: Boolean) {
        Mapping.setEnabled(context, enabled)
        isActionButtonRouted(context)
        syncTopButtonRoute(context)
    }
}
