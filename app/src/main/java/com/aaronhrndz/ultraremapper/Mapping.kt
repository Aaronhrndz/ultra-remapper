package com.aaronhrndz.ultraremapper

import com.aaronhrndz.ultraremapper.R

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast

/** Gesture â†’ action assignments, categorized into Samsung Factory Actions, Utilities, Home Assistant, Media & Volume, and Installed Apps. */
object Mapping {
    val actionGestures = listOf(
        "single" to R.string.gesture_single,
        "double" to R.string.gesture_double,
        "long" to R.string.gesture_long,
    )
    val bottomGestures = listOf(
        "bottom_double_tap" to R.string.gesture_double,
        "bottom_triple_tap" to R.string.gesture_triple,
        "bottom_long" to R.string.gesture_long,
    )
    val gestures = actionGestures + bottomGestures

    val shealthActions = listOf(
        "shealth_workout" to R.string.action_shealth_workout,
        "shealth_route" to R.string.action_shealth_route,
    )

    val samsungActions = listOf(
        "stopwatch" to R.string.action_stopwatch,
        "water_lock" to R.string.action_water_lock,
        "assistant" to R.string.action_assistant,
        "find_phone" to R.string.action_find_phone,
    )

    val devActions = listOf(
        "wireless_debugging" to R.string.action_wireless_debugging,
        "adb_mode" to R.string.action_adb_mode,
    )

    val utilityActions = listOf(
        "flashlight" to R.string.action_flashlight,
        "red_flashlight" to R.string.action_red_flashlight,
        "dnd_toggle" to R.string.action_dnd_toggle,
        "glove_mode" to R.string.action_glove_mode,
        "save_parking" to R.string.action_save_parking,
        "nav_parking" to R.string.action_nav_parking,
        "water_eject" to R.string.action_water_eject,
        "lte_toggle" to R.string.action_lte_toggle,
    )

    val homeAssistantActions = listOf(
        "ha_assist" to R.string.action_ha_assist,
    )

    val mediaActions = listOf(
        "play_pause" to R.string.action_play_pause,
        "next" to R.string.action_next,
        "previous" to R.string.action_previous,
        "vol_up" to R.string.action_vol_up,
        "vol_down" to R.string.action_vol_down,
        "open_media_player" to R.string.action_open_media_player,
    )

    val actionDefinitions = (shealthActions + samsungActions + devActions + utilityActions + homeAssistantActions + mediaActions)

    private val defaults = emptyMap<String, String>()

    private const val KEY_APP_ENABLED = "remapper_master_enabled"
    private const val KEY_HAPTIC_ENABLED = "remapper_haptic_feedback_enabled"

    fun isHapticFeedbackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTIC_ENABLED, true)

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()

    private fun prefs(context: Context) = context.getSharedPreferences("mapping", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_APP_ENABLED, enabled).apply()

    fun get(context: Context, gesture: String): String =
        prefs(context).getString(gesture, defaults[gesture]) ?: "none"

    fun set(context: Context, gesture: String, action: String) =
        prefs(context).edit().putString(gesture, action).apply()

    fun label(context: Context, action: String): String {
        if (action == "none") return context.getString(R.string.nothing)
        if (!action.startsWith("app:")) {
            val resId = actionDefinitions.firstOrNull { it.first == action }?.second
            return if (resId != null) context.getString(resId) else action
        }
        val pkg = action.removePrefix("app:")
        return try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun categoryLabel(context: Context, action: String): String = when {
        action == "none" -> ""
        action.startsWith("app:") -> "App"
        shealthActions.any { it.first == action } -> "Health"
        samsungActions.any { it.first == action } -> "Samsung"
        devActions.any { it.first == action } -> "Dev"
        utilityActions.any { it.first == action } -> "Utility"
        homeAssistantActions.any { it.first == action } -> "HA"
        mediaActions.any { it.first == action } -> "Media"
        else -> ""
    }

    @Volatile
    private var lastOpenTimestamp = 0L

    private fun isFlashlightCurrentlyOpen(): Boolean {
        val svc = BottomButtonService.instance
        if (svc != null) {
            if (svc.isSamsungFlashlightOnScreen()) return true
            if (System.currentTimeMillis() - lastOpenTimestamp < 1200L) return true
        }
        return false
    }

    /** [backToHome]: Back from a launched app returns to the watch face instead of the previous task. */
    fun perform(context: Context, gesture: String, backToHome: Boolean = false) {
        HapticFeedbackHelper.playSpringHaptic(context)
        val action = get(context, gesture)
        val audio = context.getSystemService(AudioManager::class.java)
        when (action) {
            "none" -> Unit
            "flashlight" -> toggleFlashlight(context, backToHome)
            "red_flashlight" -> toggleRedFlashlight(context)
            "dnd_toggle" -> toggleDoNotDisturb(context)
            "glove_mode" -> UtilityActions.toggleTouchSensitivity(context)
            "save_parking" -> UtilityActions.saveParking(context)
            "nav_parking" -> UtilityActions.navigateToParking(context, backToHome)
            "adb_mode" -> UtilityActions.toggleAdbEasyMode(context)
            "wireless_debugging" -> UtilityActions.openWirelessDebugging(context, backToHome)
            "water_eject" -> UtilityActions.ejectWater(context)
            "lte_toggle" -> UtilityActions.toggleLteData(context)
            "find_phone" -> triggerFindPhone(context, backToHome)
            "ha_assist" -> triggerHaAssist(context, backToHome)
            "shealth_workout" -> triggerSamsungHealthQuickButton(context, backToHome)
            "shealth_route" -> launchRouteMode(context, backToHome)
            "stopwatch" -> triggerStopwatch(context, backToHome)
            "water_lock" -> triggerWaterLock(context, backToHome)
            "assistant" -> triggerAssistant(context, backToHome)
            "play_pause" -> sendMediaCommand(context, audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> sendMediaCommand(context, audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            "previous" -> sendMediaCommand(context, audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "vol_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "vol_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "open_media_player" -> openMediaPlayer(context, backToHome)
            else -> if (action.startsWith("app:")) {
                context.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
                    ?.let {
                        try {
                            context.startActivity(it)
                        } catch (ignored: Exception) {
                        }
                    }
            }
        }
    }

    private fun triggerFindPhone(context: Context, backToHome: Boolean) {
        val appCtx = context.applicationContext
        // 1. Samsung Find Quick Remote Companion finding (direct alert/ring)
        val intentQuickFind = Intent("com.samsung.android.app.find.ACTION_REMOTE_COMPANION_FINDING")
            .setClassName("com.samsung.android.app.find", "com.samsung.android.app.find.uiview.main.disconnected.RemoteCompanionFindingActivity")
            .putExtra("extra_direct_alert", true)
            .putExtra("is_quick_mode", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            appCtx.startActivity(intentQuickFind)
            return
        } catch (_: Exception) {
        }

        // 2. Samsung Find My Watch / Alert trigger
        val alertIntent = Intent("com.samsung.android.watch.findmywatch.START_ALERT")
            .setPackage("com.samsung.android.watch.findmywatch")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            appCtx.startActivity(alertIntent)
            return
        } catch (_: Exception) {
        }

        // 3. Samsung Find Main Launcher fallback
        val intentFindLaunch = appCtx.packageManager.getLaunchIntentForPackage("com.samsung.android.app.find")
        if (intentFindLaunch != null) {
            intentFindLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (backToHome) intentFindLaunch.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            try {
                appCtx.startActivity(intentFindLaunch)
                return
            } catch (_: Exception) {
            }
        }

        // 4. Google Find My Phone Wear OS
        val intentGoogleFind = appCtx.packageManager.getLaunchIntentForPackage("com.google.android.clockwork.findmyphone")
        if (intentGoogleFind != null) {
            intentGoogleFind.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (backToHome) intentGoogleFind.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            try {
                appCtx.startActivity(intentGoogleFind)
            } catch (_: Exception) {
            }
        }
    }

    private fun launchRouteMode(context: Context, backToHome: Boolean) {
        val appCtx = context.applicationContext
        // 1. Direct Activity intent for Samsung Health Route Mode (Tile / Detail)
        val routeIntent = Intent()
            .setComponent(ComponentName("com.samsung.android.wear.shealth", "com.samsung.android.wear.shealth.app.exercise.view.setting.route.tile.ExerciseTileRouteNoDataDetailActivity"))
            .setAction("com.samsung.android.wear.shealth.intent.action.ACTION_VIEW_TILE_EXERCISE_ROUTE_NO_DATA_DETAILS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }

        try {
            appCtx.startActivity(routeIntent)
            return
        } catch (_: Exception) {
        }

        // 2. Fallback: Target settings with route action
        try {
            val targetIntent = Intent("com.samsung.android.wear.shealth.intent.action.ACTION_VIEW_EXERCISE_SETTING_ROUTE_LIST")
                .setPackage("com.samsung.android.wear.shealth")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appCtx.startActivity(targetIntent)
        } catch (_: Exception) {
        }
    }

    private fun triggerHaAssist(context: Context, backToHome: Boolean) {
        val appCtx = context.applicationContext
        val assistIntent = Intent(Intent.ACTION_ASSIST)
            .setClassName("io.homeassistant.companion.android", "io.homeassistant.companion.android.conversation.ConversationActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            appCtx.startActivity(assistIntent)
            return
        } catch (_: Exception) {
        }

        // Fallback: AssistantActivity alias
        val aliasIntent = Intent("android.intent.action.ASSIST")
            .setClassName("io.homeassistant.companion.android", "io.homeassistant.companion.android.conversation.AssistantActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appCtx.startActivity(aliasIntent)
            return
        } catch (_: Exception) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_ha_not_installed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRedFlashlight(context: Context) {
        val current = RedFlashlightActivity.currentInstance
        if (current != null) {
            current.finish()
            return
        }
        val intent = Intent(context.applicationContext, RedFlashlightActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            context.applicationContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun toggleDoNotDisturb(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        val currentFilter = nm.currentInterruptionFilter
        val enableDnd = currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val nextFilter = if (enableDnd) {
            NotificationManager.INTERRUPTION_FILTER_NONE
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        try {
            nm.setInterruptionFilter(nextFilter)
            val vib = context.getSystemService(Vibrator::class.java)
            if (enableDnd) {
                vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60), -1))
                Toast.makeText(context.applicationContext, context.getString(R.string.toast_dnd_on), Toast.LENGTH_SHORT).show()
            } else {
                vib?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
                Toast.makeText(context.applicationContext, context.getString(R.string.toast_dnd_off), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
        }
    }

    private fun triggerSamsungHealthQuickButton(context: Context, backToHome: Boolean) {
        val appCtx = context.applicationContext

        // 1. Direct standard action for Samsung Health exercise carousel / list
        try {
            val intent = Intent("com.samsung.android.wear.shealth.intent.action.VIEW_OPEN_EXERCISE_ACTIVITY").apply {
                setPackage("com.samsung.android.wear.shealth")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            }
            appCtx.startActivity(intent)
            return
        } catch (_: Exception) {}

        // 2. Component launch: ExerciseActivity
        try {
            val intent = Intent().apply {
                component = ComponentName("com.samsung.android.wear.shealth", "com.samsung.android.wear.shealth.app.exercise.view.ExerciseActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            }
            appCtx.startActivity(intent)
            return
        } catch (_: Exception) {}

        // 3. Fallback: package main launch intent
        val launch = appCtx.packageManager.getLaunchIntentForPackage("com.samsung.android.wear.shealth")
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (backToHome) launch.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            try {
                appCtx.startActivity(launch)
            } catch (_: Exception) {}
        }
    }

    private fun triggerStopwatch(context: Context, backToHome: Boolean) {
        val ctsIntent = Intent("com.google.android.wearable.action.STOPWATCH")
            .setPackage("com.samsung.android.watch.stopwatch")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            context.startActivity(ctsIntent)
            return
        } catch (ignored: Exception) {
        }
        launchComponent(
            context,
            "com.samsung.android.watch.stopwatch",
            "com.samsung.android.watch.stopwatch.activity.StopwatchActivity",
            backToHome
        )
    }

    private fun triggerWaterLock(context: Context, backToHome: Boolean) {
        val waterLockIntent = Intent("com.samsung.android.clockwork.settings.action.WATER_LOCK_SETTING")
            .setClassName(
                "com.google.android.apps.wearable.settings",
                "com.samsung.android.clockwork.settings.advanced.waterlock.StWaterLockSettingActivity"
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            context.startActivity(waterLockIntent)
        } catch (ignored: Exception) {
        }
    }

    private fun triggerAssistant(context: Context, backToHome: Boolean) {
        val assistIntent = Intent(Intent.ACTION_VOICE_COMMAND)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            context.startActivity(assistIntent)
        } catch (e: Exception) {
            val fallback = Intent("android.intent.action.ASSIST")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(fallback)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun openMediaPlayer(context: Context, backToHome: Boolean) {
        val pkgs = listOf(
            "com.samsung.android.wear.mediacontroller",
            "com.google.android.wearable.media.sessions"
        )
        for (pkg in pkgs) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (backToHome) launch.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                try {
                    context.startActivity(launch)
                    return
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun launchComponent(context: Context, pkg: String, cls: String, backToHome: Boolean) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(pkg, cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.packageManager.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let {
                    try {
                        context.startActivity(it)
                    } catch (ignored: Exception) {
                    }
                }
        }
    }

    private fun toggleFlashlight(context: Context, backToHome: Boolean) {
        val appContext = context.applicationContext
        if (isFlashlightCurrentlyOpen()) {
            lastOpenTimestamp = 0L
            val svc = BottomButtonService.instance
            svc?.cancelFlashlightTaps()
            svc?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            try {
                val offIntent = Intent("com.samsung.android.watch.flashlight.INTENT_BIXBY_FLASHLIGHT_TURN_OFF")
                    .setClassName("com.samsung.android.watch.flashlight", "com.samsung.android.watch.flashlight.main.FlashLightActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                appContext.startActivity(offIntent)
            } catch (_: Exception) {
            }
            return
        }

        val svc = BottomButtonService.instance
        svc?.tapBoostOnOpen()
        val samsungIntent = Intent("turn_on_flashlight")
            .setClassName("com.samsung.android.watch.flashlight", "com.samsung.android.watch.flashlight.main.FlashLightActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            appContext.startActivity(samsungIntent)
            if (svc != null) {
                lastOpenTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            svc?.cancelFlashlightTaps()
            val genericIntent = Intent("android.intent.action.MAIN")
                .addCategory("com.google.android.wearable.action.FLASHLIGHT")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
            try {
                appContext.startActivity(genericIntent)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Dispatches media key via:
     * 1. Active Wear OS MediaSessions (MediaControlService), which reaches bridged phone sessions like KDE Connect, Spotify, etc.
     * 2. AudioManager.dispatchMediaKeyEvent fallback for local players.
     */
    private fun sendMediaCommand(context: Context, audio: AudioManager, code: Int) {
        val handledBySession = MediaControlService.dispatchMediaAction(context, code)
        if (!handledBySession) {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }
}
