package com.aaronhrndz.ultraremapper

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Entry point when user long-presses Home button and Ultra Remapper is selected as Default Digital Assistant.
 * If 'top_long' is configured with an action, executes it.
 * If unmapped or 'none', opens Google Assistant / Bixby default voice command.
 */
class AssistEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val appEnabled = Mapping.isEnabled(this)
            val action = Mapping.get(this, "top_long")

            if (appEnabled && action != "none") {
                Mapping.perform(this, "top_long", backToHome = false)
            } else {
                // Forward to system voice assistant
                forwardToDefaultAssistant()
            }
        } catch (_: Exception) {
        } finally {
            finish()
        }
    }

    private fun forwardToDefaultAssistant() {
        val targets = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent("com.google.android.wearable.action.GOOGLE_ASSIST"),
            Intent("com.samsung.android.bixby.agent.START")
        )
        for (intent in targets) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }
}
