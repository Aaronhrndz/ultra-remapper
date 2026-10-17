package com.bennybar.ultraremapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Takes over the bottom button (KEYCODE_STEM_PRIMARY): a short press still goes Back,
 * a 500 ms hold runs the "bottom_long" mapping instead of Samsung's default.
 * Also provides gesture dispatch and foreground tracking for Samsung Flashlight control.
 */
class BottomButtonService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BottomButtonService? = null
            private set

        @Volatile
        var isFlashlightWindowActive: Boolean = false

        @Volatile
        var pendingBoostFromAction: Boolean = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tapHandler = Handler(Looper.getMainLooper())
    private var longFired = false
    private val longPress = Runnable {
        longFired = true
        // Samsung Wallet still opens underneath (the system launches it before accessibility sees the key),
        // so make Back from our app go home rather than into Samsung Wallet.
        Mapping.perform(this, "bottom_long", backToHome = true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        tapHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == "com.samsung.android.watch.flashlight") {
                isFlashlightWindowActive = true
                if (pendingBoostFromAction) {
                    pendingBoostFromAction = false
                    // Schedule Boost brightness taps relative to when the Flashlight window actually appears on screen!
                    // Samsung's onResume() schedules a 1000ms timer that resets brightness to level 10 (255).
                    // Tapping twice right after 1000ms (at 1050ms and 1170ms) steps Mode 0 -> Mode 1 (314) -> Mode 2 Boost (369).
                    tapHandler.removeCallbacksAndMessages(null)
                    tapHandler.postDelayed({
                        if (isFlashlightWindowActive) tapCenter()
                    }, 1050L)
                    tapHandler.postDelayed({
                        if (isFlashlightWindowActive) tapCenter()
                    }, 1170L)
                }
            } else if (pkg != "android" && pkg != "com.bennybar.ultraremapper") {
                isFlashlightWindowActive = false
                pendingBoostFromAction = false
                tapHandler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun onInterrupt() {}

    fun tapCenter() {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 35L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Fallback scheduler in case TYPE_WINDOW_STATE_CHANGED arrives before or after startActivity:
     * schedules two taps after Samsung's 1000ms initBrightness timer completes (~1250ms & ~1370ms from startActivity).
     */
    fun tapBoostOnOpen() {
        pendingBoostFromAction = true
        tapHandler.removeCallbacksAndMessages(null)
        tapHandler.postDelayed({
            pendingBoostFromAction = false
            if (isFlashlightWindowActive) tapCenter()
        }, 1250L)
        tapHandler.postDelayed({
            if (isFlashlightWindowActive) tapCenter()
        }, 1370L)
    }

    fun cancelFlashlightTaps() {
        pendingBoostFromAction = false
        tapHandler.removeCallbacksAndMessages(null)
    }
}
