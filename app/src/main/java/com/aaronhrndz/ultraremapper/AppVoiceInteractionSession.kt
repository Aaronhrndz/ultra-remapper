package com.aaronhrndz.ultraremapper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

class AppVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val appEnabled = Mapping.isEnabled(context)
            val action = Mapping.get(context, "top_long")

            if (appEnabled && action != "none") {
                Mapping.perform(context, "top_long", backToHome = false)
            } else {
                forwardToDefaultAssistant()
            }
        } catch (_: Exception) {
        } finally {
            hide()
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
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }
}
