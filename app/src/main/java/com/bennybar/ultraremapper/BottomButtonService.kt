package com.bennybar.ultraremapper

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Takes over the bottom button (KEYCODE_STEM_PRIMARY): a short press still goes Back,
 * a 500 ms hold runs the "bottom_long" mapping instead of Samsung's default.
 */
class BottomButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var longFired = false
    private val longPress = Runnable {
        longFired = true
        // Samsung Wallet still opens underneath (the system launches it before accessibility sees the key),
        // so make Back from our app go home rather than into Samsung Wallet.
        Mapping.perform(this, "bottom_long", backToHome = true)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_STEM_PRIMARY) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            longFired = false
            handler.postDelayed(longPress, 500)
        } else if (event.action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress)
            if (!longFired) performGlobalAction(GLOBAL_ACTION_BACK)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}
}
