package com.aaronhrndz.ultraremapper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import com.aaronhrndz.ultraremapper.adb.AdbLocalManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Bidirectional Bluetooth / DataLayer bridge between Watch and Phone Companion.
 * Publishes watch state to `/ultra_remapper/state` DataItem and handles
 * instant remote commands (`/ultra_remapper/cmd`) from the phone without requiring ADB.
 */
class CompanionSyncService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        super.onMessageReceived(event)
        when (event.path) {
            PATH_REQUEST_SYNC -> {
                syncStateToDataLayer(applicationContext)
            }
            PATH_CMD -> {
                val payload = String(event.data, Charsets.UTF_8)
                handlePhoneCommand(applicationContext, payload)
            }
        }
    }

    companion object {
        const val PATH_STATE = "/ultra_remapper/state"
        const val PATH_REQUEST_SYNC = "/ultra_remapper/request_sync"
        const val PATH_CMD = "/ultra_remapper/cmd"
        const val ACTION_STATE_UPDATED = "com.aaronhrndz.ultraremapper.ACTION_STATE_UPDATED"

        private val executor = Executors.newSingleThreadExecutor()

        fun syncStateToDataLayer(context: Context) {
            val appCtx = context.applicationContext
            executor.execute {
                try {
                    val json = buildWatchStateJson(appCtx)
                    val req = PutDataMapRequest.create(PATH_STATE).apply {
                        dataMap.putString("state_json", json.toString())
                        dataMap.putLong("ts", System.currentTimeMillis())
                    }.asPutDataRequest().setUrgent()

                    Wearable.getDataClient(appCtx).putDataItem(req)
                } catch (_: Exception) {
                }
            }
        }

        private fun handlePhoneCommand(context: Context, payloadStr: String) {
            executor.execute {
                try {
                    val json = JSONObject(payloadStr)
                    when (json.optString("type")) {
                        "set_mapping" -> {
                            val gesture = json.optString("gesture")
                            val action = json.optString("action")
                            if (gesture.isNotBlank() && action.isNotBlank()) {
                                Mapping.set(context, gesture, action)
                                SystemRouteHelper.isActionButtonRouted(context, autoFix = true)
                                SystemRouteHelper.syncSamsungRoutes(context)
                            }
                        }
                        "set_master" -> {
                            val enabled = json.optBoolean("enabled", true)
                            SystemRouteHelper.onMasterToggle(context, enabled)
                            SystemRouteHelper.syncSamsungRoutes(context)
                        }
                        "set_haptic" -> {
                            val enabled = json.optBoolean("enabled", true)
                            Mapping.setHapticFeedbackEnabled(context, enabled)
                        }
                        "open_a11y" -> {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Throwable) {}
                        }
                        "open_assistant" -> {
                            try {
                                context.startActivity(
                                    Intent("android.settings.VOICE_INPUT_SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Throwable) {}
                        }
                        "open_toctoc" -> {
                            try {
                                context.startActivity(
                                    Intent("android.settings.BUTTONS_GESTURES_SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Throwable) {}
                        }
                    }
                    // Notify watch UI if open
                    context.sendBroadcast(Intent(ACTION_STATE_UPDATED).setPackage(context.packageName))
                    // Immediately push updated state back to phone
                    syncStateToDataLayer(context)
                } catch (_: Exception) {
                }
            }
        }

        fun buildWatchStateJson(context: Context): JSONObject {
            val root = JSONObject()
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.3"
            } catch (_: Exception) {
                "2.2.3"
            }
            root.put("versionName", versionName)
            root.put("masterEnabled", Mapping.isEnabled(context))
            root.put("hapticEnabled", Mapping.isHapticFeedbackEnabled(context))

            // Mappings
            val mappingsObj = JSONObject()
            for ((gesture, _) in Mapping.gestures) {
                mappingsObj.put(gesture, Mapping.get(context, gesture))
            }
            root.put("mappings", mappingsObj)

            // Permissions & System States
            val overlayOk = Settings.canDrawOverlays(context)
            val secureOk = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
            val actionRouteOk = try {
                val cur = Settings.Global.getString(context.contentResolver, "setting_action_button_press")
                cur == "${context.packageName}/${ActionButtonService::class.java.name}"
            } catch (_: Exception) {
                false
            }
            val notifOk = try {
                val expected = ComponentName(context, MediaControlService::class.java)
                val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
                enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == expected }
            } catch (_: Exception) {
                false
            }
            val batteryOk = try {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } catch (_: Exception) {
                false
            }
            val a11yOk = try {
                val a11yGlobal = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                val expected = ComponentName(context, BottomButtonService::class.java)
                val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                a11yGlobal && enabledServices.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == expected }
            } catch (_: Exception) {
                false
            }
            val isAssistant = try {
                val cur = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
                    ?: Settings.Secure.getString(context.contentResolver, "assistant")
                cur?.contains(context.packageName) == true
            } catch (_: Exception) {
                false
            }
            val isTocToc = try {
                val launchApp = Settings.Global.getString(context.contentResolver, "setting_gesture_launch_app")
                val selectedApp = Settings.Global.getString(context.contentResolver, "setting_gesture_launch_selected_app")
                val launchOn = Settings.Global.getInt(context.contentResolver, "setting_gesture_launch_on", 0)
                val expected = "${context.packageName}/${TocTocTrampolineActivity::class.java.name}"
                launchOn == 1 && (launchApp == expected || selectedApp == expected || launchApp?.contains(context.packageName) == true)
            } catch (_: Exception) {
                false
            }
            val haInstalled = try {
                context.packageManager.getPackageInfo("io.homeassistant.companion.android", 0)
                true
            } catch (_: Exception) {
                false
            }
            val samsungPayEnabled = AdbLocalManager.isSamsungPayEnabled(context)

            val permsObj = JSONObject().apply {
                put("OVERLAY", overlayOk)
                put("WRITE_SECURE", secureOk)
                put("ACTION_ROUTING", actionRouteOk)
                put("MEDIA_BRIDGE", notifOk)
                put("BATTERY_WHITELIST", batteryOk)
                put("ACCESSIBILITY", a11yOk)
                put("IS_ASSISTANT", isAssistant)
                put("IS_TOCTOC", isTocToc)
                put("HA_INSTALLED", haInstalled)
                put("SAMSUNG_PAY_ENABLED", samsungPayEnabled)
            }
            root.put("permissions", permsObj)

            // Installed launcher apps on watch (package + label)
            val appsArr = JSONArray()
            try {
                val pm = context.packageManager
                val apps = pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0
                )
                    .map { it.activityInfo.packageName }
                    .filter { it != context.packageName }
                    .distinct()
                    .map { pkg ->
                        val label = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) {
                            pkg
                        }
                        pkg to label
                    }
                    .sortedBy { it.second.lowercase() }

                for ((pkg, label) in apps) {
                    appsArr.put(JSONObject().apply {
                        put("pkg", pkg)
                        put("label", label)
                    })
                }
            } catch (_: Exception) {
            }
            root.put("installedApps", appsArr)

            return root
        }
    }
}
