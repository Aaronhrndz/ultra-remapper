package com.bennybar.ultraremapper

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

/** Gesture → action assignments. An action is a media/volume id from [actions] or "app:<package>". */
object Mapping {
    val actionGestures = listOf("single" to "Single press", "double" to "Double press", "long" to "Long press")
    val bottomGestures = listOf("bottom_long" to "Long press")
    val gestures = actionGestures + bottomGestures

    val actions = listOf(
        "none" to "Nothing",
        "flashlight" to "Flashlight",
        "play_pause" to "Play / pause",
        "next" to "Next track",
        "previous" to "Previous track",
        "vol_up" to "Volume up",
        "vol_down" to "Volume down",
    )

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

    @Volatile
    private var lastOpenTimestamp = 0L

    private fun isFlashlightCurrentlyOpen(): Boolean {
        if (BottomButtonService.isFlashlightWindowActive) return true
        // Also treat as open if we launched it within the last 1.2s and window event hasn't arrived yet
        if (System.currentTimeMillis() - lastOpenTimestamp < 1200L) return true
        return false
    }

    /** [backToHome]: Back from a launched app returns to the watch face instead of the previous task. */
    fun perform(context: Context, gesture: String, backToHome: Boolean = false) {
        val action = get(context, gesture)
        val audio = context.getSystemService(AudioManager::class.java)
        when (action) {
            "flashlight" -> toggleFlashlight(context, backToHome)
            "play_pause" -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            "previous" -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "vol_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "vol_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            else -> if (action.startsWith("app:")) {
                context.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
                    ?.let { context.startActivity(it) }
            }
        }
    }

    /**
     * Toggles Samsung's native Flashlight Activity:
     * - If active, closes it cleanly (via Accessibility GLOBAL_ACTION_BACK or Bixby turn-off intent).
     * - If inactive, launches FlashLightActivity and automatically switches to Max brightness.
     */
    private fun toggleFlashlight(context: Context, backToHome: Boolean) {
        val appContext = context.applicationContext
        if (isFlashlightCurrentlyOpen()) {
            BottomButtonService.isFlashlightWindowActive = false
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
                } catch (ignored: Exception) {}
            }
            return
        }

        val samsungIntent = Intent("turn_on_flashlight")
            .setClassName("com.samsung.android.watch.flashlight", "com.samsung.android.watch.flashlight.main.FlashLightActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            appContext.startActivity(samsungIntent)
            BottomButtonService.isFlashlightWindowActive = true
            lastOpenTimestamp = System.currentTimeMillis()
            BottomButtonService.instance?.tapBoostOnOpen()
        } catch (e: Exception) {
            val genericIntent = Intent("android.intent.action.MAIN")
                .addCategory("com.google.android.wearable.action.FLASHLIGHT")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
            try {
                appContext.startActivity(genericIntent)
            } catch (ignored: Exception) {}
        }
    }

    private fun sendMediaKey(audio: AudioManager, code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
