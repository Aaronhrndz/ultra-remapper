package com.bennybar.ultraremapper

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.view.KeyEvent

/**
 * Target for Samsung's `setting_action_button_press`; the system starts it on every Action button event.
 * ACTION_KEY_EVENT: 0=down, 1=up, 2=single click, 3=long press, 5=multi press (ACTION_KEY_COUNT).
 */
class ActionButtonService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gesture = when (intent?.getIntExtra("ACTION_KEY_EVENT", -1)) {
            2 -> "single"
            3 -> "long"
            5 -> if (intent.getIntExtra("ACTION_KEY_COUNT", 0) == 2) "double" else null
            else -> null
        }
        if (gesture != null) perform(Mapping.get(this, gesture))
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun perform(action: String) {
        val audio = getSystemService(AudioManager::class.java)
        when (action) {
            "play_pause" -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            "previous" -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "vol_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "vol_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            else -> if (action.startsWith("app:")) {
                packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { startActivity(it) }
            }
        }
    }

    private fun sendMediaKey(audio: AudioManager, code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
