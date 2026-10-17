package com.bennybar.ultraremapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Intercepts KEYCODE_STEM_PRIMARY (bottom button): short press → Back, 500 ms hold → mapped action.
 * Also dynamically tracks the Samsung Flashlight window only when triggered, with zero idle wakeups.
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
    private var longPressFired = false
    private val onLongPress = Runnable {
        longPressFired = true
        Mapping.perform(this, "bottom_long", backToHome = true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        setWindowTrackingEnabled(false)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        tapHandler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        tapHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun setWindowTrackingEnabled(enabled: Boolean) {
        try {
            val info = serviceInfo ?: return
            val targetTypes = if (enabled) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
            if (info.eventTypes != targetTypes) {
                info.eventTypes = targetTypes
                serviceInfo = info
            }
        } catch (ignored: Exception) {
        }
    }

    fun isSamsungFlashlightOnScreen(): Boolean {
        val rootPkg = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        return rootPkg == "com.samsung.android.watch.flashlight" || isFlashlightWindowActive
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_STEM_PRIMARY) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            longPressFired = false
            handler.postDelayed(onLongPress, 500)
        } else if (event.action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(onLongPress)
            if (!longPressFired) performGlobalAction(GLOBAL_ACTION_BACK)
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
                    tapHandler.removeCallbacksAndMessages(null)
                    tapHandler.postDelayed({
                        if (isSamsungFlashlightOnScreen()) tapCenter()
                    }, 1050L)
                    tapHandler.postDelayed({
                        if (isSamsungFlashlightOnScreen()) tapCenter()
                    }, 1170L)
                }
            } else if (pkg != "android" && pkg != "com.bennybar.ultraremapper") {
                isFlashlightWindowActive = false
                pendingBoostFromAction = false
                tapHandler.removeCallbacksAndMessages(null)
                setWindowTrackingEnabled(false)
            }
        }
    }

    override fun onInterrupt() {}

    private fun tapCenter() {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 35L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun tapBoostOnOpen() {
        setWindowTrackingEnabled(true)
        pendingBoostFromAction = true
        tapHandler.removeCallbacksAndMessages(null)
        tapHandler.postDelayed({
            pendingBoostFromAction = false
            if (isSamsungFlashlightOnScreen()) tapCenter()
        }, 1250L)
        tapHandler.postDelayed({
            if (isSamsungFlashlightOnScreen()) tapCenter()
        }, 1370L)
    }

    fun cancelFlashlightTaps() {
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        tapHandler.removeCallbacksAndMessages(null)
        setWindowTrackingEnabled(false)
    }
}
