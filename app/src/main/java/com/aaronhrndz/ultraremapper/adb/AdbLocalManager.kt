package com.aaronhrndz.ultraremapper.adb

import android.content.Context
import android.content.pm.PackageManager

/**
 * High-level ADB controller executing targeted permission grants and Samsung Pay freezing.
 */
object AdbLocalManager {

    const val SAMSUNG_PAY_PACKAGE = "com.samsung.android.samsungpay.gear"

    fun executeSingleGrant(client: LocalAdbClient, pkgName: String, permissionType: PermissionType): Boolean {
        if (!client.isConnected) return false
        return try {
            when (permissionType) {
                PermissionType.OVERLAY -> {
                    client.exec("appops set $pkgName SYSTEM_ALERT_WINDOW allow")
                    true
                }
                PermissionType.SECURE_SETTINGS -> {
                    client.exec("pm grant $pkgName android.permission.WRITE_SECURE_SETTINGS")
                    true
                }
                PermissionType.ACTION_ROUTE -> {
                    client.exec("settings put global setting_action_button_press $pkgName/$pkgName.ActionButtonService")
                    true
                }
                PermissionType.NOTIFICATION_LISTENER -> {
                    client.exec("cmd notification allow_listener $pkgName/$pkgName.MediaControlService")
                    true
                }
                PermissionType.BATTERY_WHITELIST -> {
                    client.exec("cmd deviceidle whitelist +$pkgName")
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isSamsungPayEnabled(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(SAMSUNG_PAY_PACKAGE, 0)
            appInfo.enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Toggles Samsung Pay freezing.
     * Returns true if operation succeeded, false if ADB failed (leaving previous state unchanged).
     */
    fun toggleSamsungPay(client: LocalAdbClient, targetEnabled: Boolean): Boolean {
        if (!client.isConnected) return false
        return try {
            val cmd = if (targetEnabled) {
                "pm enable $SAMSUNG_PAY_PACKAGE"
            } else {
                "pm disable-user --user 0 $SAMSUNG_PAY_PACKAGE"
            }
            val res = client.exec(cmd)
            res.contains("new state", ignoreCase = true) || res.isEmpty()
        } catch (_: Exception) {
            false
        }
    }
}

enum class PermissionType {
    OVERLAY,
    SECURE_SETTINGS,
    ACTION_ROUTE,
    NOTIFICATION_LISTENER,
    BATTERY_WHITELIST
}
