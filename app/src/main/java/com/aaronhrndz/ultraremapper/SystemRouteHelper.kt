package com.aaronhrndz.ultraremapper

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object SystemRouteHelper {
    const val SAMSUNG_HEALTH_ACTION_SERVICE = "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"
    const val DEFAULT_RECENT_APPS_ALIAS = "com.samsung.android.apps.wearable.recent/com.samsung.android.apps.wearable.recent.RecentActivityAlias"

    private const val PREFS_ASSISTANT_BACKUP = "assistant_backup_prefs"
    private const val KEY_ORIG_VOICE_SERVICE = "orig_voice_interaction_service"
    private const val KEY_ORIG_ASSISTANT = "orig_assistant"

    fun hasHardwareActionButton(context: Context): Boolean = true

    fun hasSecureSettingsPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Resolves the real external VoiceInteractionService (Google Gemini / Assistant or Bixby)
     * installed on the watch, so we can restore it when top_long is NOT remapped.
     */
    fun findExternalAssistantComponent(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_ASSISTANT_BACKUP, Context.MODE_PRIVATE)
        val savedVoice = prefs.getString(KEY_ORIG_VOICE_SERVICE, null)
        if (!savedVoice.isNullOrBlank() && !savedVoice.contains(context.packageName)) {
            return savedVoice
        }
        val savedAssist = prefs.getString(KEY_ORIG_ASSISTANT, null)
        if (!savedAssist.isNullOrBlank() && !savedAssist.contains(context.packageName)) {
            return savedAssist
        }

        return try {
            val pm = context.packageManager
            val services = pm.queryIntentServices(
                android.content.Intent("android.service.voice.VoiceInteractionService"),
                0
            )
            // Prefer Google Assistant / Gemini over Bixby if both are installed
            val external = services
                .mapNotNull { it.serviceInfo }
                .filter { it.packageName != context.packageName }
                .sortedByDescending { info ->
                    when {
                        info.packageName.contains("google", ignoreCase = true) -> 2
                        info.packageName.contains("bixby", ignoreCase = true) -> 1
                        else -> 0
                    }
                }
                .firstOrNull()

            if (external != null) {
                ComponentName(external.packageName, external.name).flattenToShortString()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolves an explicit Intent for the external assistant's VOICE_COMMAND or ASSIST activity
     * so we never resolve back to Ultra Remapper's own AssistEntryActivity.
     */
    fun buildExternalAssistantLaunchIntent(context: Context, backToHome: Boolean = false): android.content.Intent? {
        val pm = context.packageManager
        val actions = listOf(
            android.content.Intent.ACTION_VOICE_COMMAND,
            "com.google.android.wearable.action.GOOGLE_ASSIST",
            android.content.Intent.ACTION_ASSIST,
            "com.samsung.android.bixby.agent.START"
        )
        for (act in actions) {
            try {
                val query = android.content.Intent(act)
                val resolved = pm.queryIntentActivities(query, 0)
                    .mapNotNull { it.activityInfo }
                    .filter { it.packageName != context.packageName }
                    .sortedByDescending { info ->
                        when {
                            info.packageName.contains("google", ignoreCase = true) -> 2
                            info.packageName.contains("bixby", ignoreCase = true) -> 1
                            else -> 0
                        }
                    }
                    .firstOrNull()

                if (resolved != null) {
                    return android.content.Intent(act).apply {
                        component = ComponentName(resolved.packageName, resolved.name)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        if (backToHome) addFlags(android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun backupCurrentExternalAssistantIfAny(context: Context) {
        try {
            val curVoice = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            val curAssist = Settings.Secure.getString(context.contentResolver, "assistant")
            val prefs = context.getSharedPreferences(PREFS_ASSISTANT_BACKUP, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (!curVoice.isNullOrBlank() && !curVoice.contains(context.packageName)) {
                editor.putString(KEY_ORIG_VOICE_SERVICE, curVoice)
            }
            if (!curAssist.isNullOrBlank() && !curAssist.contains(context.packageName)) {
                editor.putString(KEY_ORIG_ASSISTANT, curAssist)
            }
            editor.apply()
        } catch (_: Throwable) {}
    }

    fun syncSamsungRoutes(context: Context) {
        if (!hasSecureSettingsPermission(context)) return
        try {
            backupCurrentExternalAssistantIfAny(context)
            val isMasterOn = Mapping.isEnabled(context)

            // 1. Top button long press (Assistant route)
            val topLongAction = Mapping.get(context, "top_long")
            val ourVoiceService = "${context.packageName}/.AppVoiceInteractionService"
            val currentAssistant = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
                ?: Settings.Secure.getString(context.contentResolver, "assistant")
            val isOurs = currentAssistant?.contains(context.packageName) == true

            // If top_long is actively mapped to a custom action (not 'none' and not 'assistant' itself),
            // claim voice_interaction_service so top_long triggers our custom action.
            // Otherwise (top_long == 'none', top_long == 'assistant', or master switch OFF),
            // restore the real Google Assistant / Gemini VoiceInteractionService so its native
            // overlay session opens on the VERY FIRST press without cold-start abort!
            if (isMasterOn && topLongAction != "none" && topLongAction != "assistant") {
                if (!isOurs) {
                    Settings.Secure.putString(context.contentResolver, "voice_interaction_service", ourVoiceService)
                    Settings.Secure.putString(context.contentResolver, "assistant", ourVoiceService)
                }
                Settings.Global.putString(context.contentResolver, "setting_home_key_long_press", "googlevoiceassistant/googlevoiceassistant")
            } else if (isOurs) {
                val realAssistant = findExternalAssistantComponent(context)
                if (!realAssistant.isNullOrBlank()) {
                    Settings.Secure.putString(context.contentResolver, "voice_interaction_service", realAssistant)
                    Settings.Secure.putString(context.contentResolver, "assistant", realAssistant)
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
