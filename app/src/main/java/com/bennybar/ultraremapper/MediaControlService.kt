package com.bennybar.ultraremapper

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/**
 * Provides access to all active MediaSessions on Wear OS (including bridged phone sessions
 * such as KDE Connect, Spotify, YouTube, etc., which the native watch Media Player controls).
 */
class MediaControlService : NotificationListenerService() {

    companion object {
        fun dispatchMediaAction(context: Context, keyCode: Int): Boolean {
            val msm = context.getSystemService(MediaSessionManager::class.java) ?: return false
            val component = ComponentName(context, MediaControlService::class.java)
            val controllers: List<MediaController> = try {
                msm.getActiveSessions(component)
            } catch (e: SecurityException) {
                emptyList()
            }
            if (controllers.isEmpty()) return false

            // Prioritize actively playing/buffering sessions, or KDE Connect / bridged phone media sessions
            val target = controllers.firstOrNull { ctrl ->
                val state = ctrl.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            } ?: controllers.firstOrNull { ctrl ->
                val pkg = ctrl.packageName.lowercase()
                pkg.contains("kde") || pkg.contains("media") || pkg.contains("clockwork")
            } ?: controllers.first()

            val tc = target.transportControls
            val state = target.playbackState?.state
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (state == PlaybackState.STATE_PLAYING) {
                        tc.pause()
                    } else if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED) {
                        tc.play()
                    } else {
                        target.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                        target.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                    }
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
                else -> {
                    target.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    target.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }
            return true
        }
    }
}
