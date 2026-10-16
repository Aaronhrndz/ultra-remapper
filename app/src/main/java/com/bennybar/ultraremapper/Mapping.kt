package com.bennybar.ultraremapper

import android.content.Context

/** Gesture → action assignments. An action is a media/volume id from [actions] or "app:<package>". */
object Mapping {
    val gestures = listOf("single" to "Single press", "double" to "Double press", "long" to "Long press")

    val actions = listOf(
        "none" to "Nothing",
        "play_pause" to "Play / pause",
        "next" to "Next track",
        "previous" to "Previous track",
        "vol_up" to "Volume up",
        "vol_down" to "Volume down",
    )

    private val defaults = mapOf(
        "single" to "app:com.limegreenv.geartracker",
        "double" to "next",
        "long" to "play_pause",
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
}
