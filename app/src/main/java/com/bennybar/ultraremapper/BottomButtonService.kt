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

    private var tapCount = 0
    private var actionFired = false
    private var lastUpTime = 0L

    private fun vibrateTick(durationMs: Long) {
        try {
            val vibrator = getSystemService(android.os.Vibrator::class.java)
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private val firePendingTaps = Runnable {
        val count = tapCount
        resetGestureState()
        when (count) {
            2 -> {
                if (Mapping.get(this, "bottom_double_tap") != "none") {
                    vibrateTick(35)
                    Mapping.perform(this, "bottom_double_tap", backToHome = true)
                    UpdateChecker.checkOpportunistic(applicationContext)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            3 -> {
                if (Mapping.get(this, "bottom_triple_tap") != "none") {
                    vibrateTick(45)
                    Mapping.perform(this, "bottom_triple_tap", backToHome = true)
                    UpdateChecker.checkOpportunistic(applicationContext)
                } else {
                    for (i in 0 until 3) performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            else -> {
                for (i in 0 until count) performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private val onHoldTimeout = Runnable {
        actionFired = true
        android.util.Log.d("BottomButton", "onHoldTimeout: firing bottom_long")
        vibrateTick(45)
        Mapping.perform(this, "bottom_long", backToHome = true)
        UpdateChecker.checkOpportunistic(applicationContext)
    }

    private fun resetGestureState() {
        handler.removeCallbacks(firePendingTaps)
        handler.removeCallbacks(onHoldTimeout)
        tapCount = 0
        actionFired = false
        lastUpTime = 0L
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
        resetGestureState()
        tapHandler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        resetGestureState()
        tapHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun setWindowTrackingEnabled(enabled: Boolean) {
        try {
            val info = serviceInfo ?: return
            val targetTypes = if (enabled) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
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

        val hasDoubleTap = Mapping.get(this, "bottom_double_tap") != "none"
        val hasTripleTap = Mapping.get(this, "bottom_triple_tap") != "none"
        val anyMultiGesture = hasDoubleTap || hasTripleTap

        val now = System.currentTimeMillis()

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            handler.removeCallbacks(firePendingTaps)
            handler.removeCallbacks(onHoldTimeout)
            actionFired = false

            if (now - lastUpTime > 360L) {
                tapCount = 0
            }

            if (tapCount == 0) {
                handler.postDelayed(onHoldTimeout, 400L)
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(onHoldTimeout)
            lastUpTime = now

            if (!actionFired) {
                tapCount++
                if (!anyMultiGesture) {
                    resetGestureState()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    if (tapCount >= 3) {
                        handler.removeCallbacks(firePendingTaps)
                        firePendingTaps.run()
                    } else if (tapCount == 2 && !hasTripleTap) {
                        handler.removeCallbacks(firePendingTaps)
                        firePendingTaps.run()
                    } else {
                        handler.postDelayed(firePendingTaps, 260L)
                    }
                }
            } else {
                resetGestureState()
            }
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
                    scheduleBoostTaps(1150L, 1290L)
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
        val stroke = GestureDescription.StrokeDescription(path, 0L, 40L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun scheduleBoostTaps(firstDelayMs: Long, secondDelayMs: Long) {
        tapHandler.removeCallbacksAndMessages(null)
        tapHandler.postDelayed({
            if (isSamsungFlashlightOnScreen()) tapCenter()
        }, firstDelayMs)
        tapHandler.postDelayed({
            if (isSamsungFlashlightOnScreen()) tapCenter()
        }, secondDelayMs)
    }

    fun tapBoostOnOpen() {
        setWindowTrackingEnabled(true)
        pendingBoostFromAction = true
        // Fallback timer if TYPE_WINDOW_STATE_CHANGED arrived before serviceInfo updated
        scheduleBoostTaps(1400L, 1540L)
    }

    fun cancelFlashlightTaps() {
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        tapHandler.removeCallbacksAndMessages(null)
        setWindowTrackingEnabled(false)
    }
}
