package com.aaronhrndz.ultraremapper

import android.content.Intent
import android.speech.RecognitionService

/**
 * Stub RecognitionService required by Android's VoiceInteractionServiceInfo parser.
 * Without this, VoiceInteractionService fails framework validation and is silently rejected.
 */
class AppRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
