package com.aaronhrndz.ultraremapper.adb

import android.content.Context
import android.content.pm.PackageManager

/**
 * High-level ADB controller executing targeted permission grants and Samsung Pay freezing.
 */
object AdbLocalManager {

    const val SAMSUNG_PAY_PACKAGE = "com.samsung.android.samsungpay.gear"

    fun executeSingleGrant(client: LocalAdbClient, pkgName: String, permissionType: PermissionType): Boolean =
        setPermissionState(client, pkgName, permissionType, true)

    fun setPermissionState(client: LocalAdbClient, pkgName: String, permissionType: PermissionType, enable: Boolean): Boolean {
        val targetClient = if (client.isConnected) client else LocalAdbClient.activeInstance
        if (targetClient?.isConnected != true) return false
        return try {
            when (permissionType) {
                PermissionType.OVERLAY -> {
                    val mode = if (enable) "allow" else "ignore"
                    targetClient.exec("appops set $pkgName SYSTEM_ALERT_WINDOW $mode")
                    true
                }
                PermissionType.SECURE_SETTINGS -> {
                    val op = if (enable) "grant" else "revoke"
                    targetClient.exec("pm $op $pkgName android.permission.WRITE_SECURE_SETTINGS")
                    true
                }
                PermissionType.ACTION_ROUTE -> {
                    val target = if (enable) {
                        "$pkgName/$pkgName.ActionButtonService"
                    } else {
                        "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"
                    }
                    targetClient.exec("settings put global setting_action_button_press $target")
                    true
                }
                PermissionType.NOTIFICATION_LISTENER -> {
                    val op = if (enable) "allow_listener" else "disallow_listener"
                    targetClient.exec("cmd notification $op $pkgName/$pkgName.MediaControlService")
                    true
                }
                PermissionType.BATTERY_WHITELIST -> {
                    val sign = if (enable) "+" else "-"
                    targetClient.exec("cmd deviceidle whitelist $sign$pkgName")
                    true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AdbLocalManager", "setPermissionState failed for $permissionType", e)
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
        val targetClient = if (client.isConnected) client else LocalAdbClient.activeInstance
        if (targetClient?.isConnected != true) return false
        return try {
            val cmd = if (targetEnabled) {
                "pm enable $SAMSUNG_PAY_PACKAGE; pm unhide $SAMSUNG_PAY_PACKAGE; cmd package install-existing $SAMSUNG_PAY_PACKAGE"
            } else {
                "pm disable-user --user 0 $SAMSUNG_PAY_PACKAGE || pm disable-user $SAMSUNG_PAY_PACKAGE || pm disable $SAMSUNG_PAY_PACKAGE || pm hide $SAMSUNG_PAY_PACKAGE"
            }
            val res = targetClient.exec(cmd)
            android.util.Log.d("AdbLocalManager", "toggleSamsungPay($targetEnabled) -> '$res'")
            // Verify actual package status via pm list
            val checkDisabled = targetClient.exec("pm list packages -d $SAMSUNG_PAY_PACKAGE")
            val isPkgDisabled = checkDisabled.contains(SAMSUNG_PAY_PACKAGE)
            if (!targetEnabled) {
                isPkgDisabled || 
                res.contains("disabled", ignoreCase = true) || 
                res.contains("hidden", ignoreCase = true) ||
                res.contains("new state", ignoreCase = true) ||
                res.contains("true", ignoreCase = true)
            } else {
                !isPkgDisabled || 
                res.contains("enabled", ignoreCase = true) || 
                res.contains("unhidden", ignoreCase = true) ||
                res.contains("new state", ignoreCase = true) ||
                res.contains("installed", ignoreCase = true)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdbLocalManager", "toggleSamsungPay failed", e)
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
