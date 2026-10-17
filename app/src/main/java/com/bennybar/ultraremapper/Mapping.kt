package com.bennybar.ultraremapper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

/** Gesture → action assignments, categorized into Samsung Factory Actions, Media & Volume, and Installed Apps. */
object Mapping {
    val actionGestures = listOf(
        "single" to "Single press",
        "double" to "Double press",
        "long" to "Long press"
    )
    val bottomGestures = listOf("bottom_long" to "Long press")
    val gestures = actionGestures + bottomGestures

    val samsungActions = listOf(
        "flashlight" to "Flashlight (Boost)",
        "shealth_workout" to "Samsung Health (Workout)",
        "shealth_exercises" to "Exercise List",
        "shealth_pick_workout" to "Pick Quick Workout",
        "stopwatch" to "Stopwatch",
        "water_lock" to "Water Lock",
        "assistant" to "Digital Assistant",
    )

    val mediaActions = listOf(
        "play_pause" to "Play / Pause",
        "next" to "Next Track",
        "previous" to "Previous Track",
        "vol_up" to "Volume Up",
        "vol_down" to "Volume Down",
        "open_media_player" to "Open Media Controller",
    )

    val actions = listOf("none" to "Nothing") + samsungActions + mediaActions

    private val defaults = mapOf(
        "single" to "flashlight",
        "double" to "next",
        "long" to "play_pause",
        "bottom_long" to "app:com.google.android.apps.walletnfcrel",
    )

    private fun prefs(context: Context) = context.getSharedPreferences("mapping", Context.MODE_PRIVATE)

    fun get(context: Context, gesture: String): String =
        prefs(context).getString(gesture, defaults[gesture]) ?: "none"

    fun set(context: Context, gesture: String, action: String) =
        prefs(context).edit().putString(gesture, action).apply()

    fun label(context: Context, action: String): String {
        if (!action.startsWith("app:")) return actions.toMap()[action] ?: action
        val pkg = action.removePrefix("app:")
        return try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun categoryLabel(action: String): String = when {
        action == "none" -> "Disabled"
        action.startsWith("app:") -> "App"
        samsungActions.any { it.first == action } -> "Samsung"
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
        val action = get(context, gesture)
        val audio = context.getSystemService(AudioManager::class.java)
        when (action) {
            "none" -> Unit
            "flashlight" -> toggleFlashlight(context, backToHome)
            "shealth_workout" -> triggerSamsungHealthQuickButton(context, backToHome)
            "shealth_exercises" -> launchComponent(
                context,
                "com.samsung.android.wear.shealth",
                "com.samsung.android.wear.shealth.app.exercise.view.ExerciseActivity",
                backToHome
            )
            "shealth_pick_workout" -> launchComponent(
                context,
                "com.samsung.android.wear.shealth",
                "com.samsung.android.wear.shealth.app.exercise.view.actionButton.ExerciseActionButtonSelectionActivity",
                backToHome
            )
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

    /**
     * Invokes Samsung Health's native ActionButtonService with ACTION_KEY_EVENT=2 (or falls back to ExerciseActivity).
     * This executes the exact factory behavior (starts/pauses/laps the configured workout or opens Exercise List).
     */
    private fun triggerSamsungHealthQuickButton(context: Context, backToHome: Boolean) {
        val svcIntent = Intent().apply {
            component = ComponentName(
                "com.samsung.android.wear.shealth",
                "com.samsung.android.wear.shealth.service.ActionButtonService"
            )
            putExtra("ACTION_KEY_EVENT", 2)
            putExtra("ACTION_KEY_COUNT", 1)
        }
        try {
            val started = context.startService(svcIntent)
            if (started != null) return
        } catch (ignored: Exception) {
        }
        launchComponent(
            context,
            "com.samsung.android.wear.shealth",
            "com.samsung.android.wear.shealth.app.exercise.view.ExerciseActivity",
            backToHome
        )
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
            if (svc != null) {
                svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            } else {
                val offIntent = Intent("com.samsung.android.watch.flashlight.INTENT_BIXBY_FLASHLIGHT_TURN_OFF")
                    .setClassName("com.samsung.android.watch.flashlight", "com.samsung.android.watch.flashlight.main.FlashLightActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try {
                    appContext.startActivity(offIntent)
                } catch (ignored: Exception) {
                }
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
