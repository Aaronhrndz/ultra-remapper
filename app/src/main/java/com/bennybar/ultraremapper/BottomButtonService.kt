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
        var currentActivePackage: String? = null

        @Volatile
        var currentActiveClass: String? = null

        @Volatile
        var pendingBoostFromAction: Boolean = false

        @Volatile
        var pendingWirelessAdbAutoToggle: Boolean = false
        @Volatile
        var pendingWalletDismissAction: Boolean = false
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
                }
            }
            3 -> {
                if (Mapping.get(this, "bottom_triple_tap") != "none") {
                    vibrateTick(45)
                    Mapping.perform(this, "bottom_triple_tap", backToHome = true)
                    UpdateChecker.checkOpportunistic(applicationContext)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            else -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private val walletFallbackAction = Runnable {
        if (pendingWalletDismissAction) {
            pendingWalletDismissAction = false
            Mapping.perform(this, "bottom_long", backToHome = true)
            UpdateChecker.checkOpportunistic(applicationContext)
        }
    }

    private val onHoldTimeout = Runnable {
        actionFired = true
        android.util.Log.d("BottomButton", "onHoldTimeout: arming for Samsung Wallet launch")
        vibrateTick(45)
        // Set flag to wait for Samsung Wallet/Pay window to appear
        pendingWalletDismissAction = true
        handler.removeCallbacks(walletFallbackAction)
        // Fallback: if Samsung Wallet doesn't open within 650ms (e.g. not configured), execute mapped action anyway
        handler.postDelayed(walletFallbackAction, 650L)
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
        val info = serviceInfo
        if (info != null) {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        pendingWalletDismissAction = false
        resetGestureState()
        tapHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(walletFallbackAction)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        pendingWirelessAdbAutoToggle = false
        pendingWalletDismissAction = false
        resetGestureState()
        tapHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(walletFallbackAction)
        super.onDestroy()
    }

    private fun setWindowTrackingEnabled(enabled: Boolean) {
        // Kept for backward compatibility with tapBoostOnOpen / autoToggleWirelessAdb callers.
        // Window tracking remains enabled so dynamic Samsung Health handover runs whenever foreground changes.
    }

    fun isSamsungFlashlightOnScreen(): Boolean {
        val rootPkg = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        return rootPkg == "com.samsung.android.watch.flashlight" || isFlashlightWindowActive
    }

    fun isSamsungHealthWorkoutActive(): Boolean {
        // 1. Ongoing Samsung Health workout notification (works even when workout screen not in foreground)
        if (MediaControlService.isShealthWorkoutOngoing()) return true

        // 2. Strict on-screen check (workout "during" screen only, never exercise list / favorites)
        return try {
            val root = rootInActiveWindow ?: return false
            if (root.packageName?.toString() != "com.samsung.android.wear.shealth") return false
            root.findAccessibilityNodeInfosByViewId("com.samsung.android.wear.shealth:id/exercise_during_layout").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.samsung.android.wear.shealth:id/lap_count").isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Mapping.isEnabled(this)) return false
        if (event.keyCode != KeyEvent.KEYCODE_STEM_PRIMARY) return false

        // Drop delayed / stale key events (> 800ms old) to prevent queued execution burst
        val eventUptime = event.eventTime
        if (eventUptime > 0L) {
            val nowUptime = android.os.SystemClock.uptimeMillis()
            if (nowUptime - eventUptime > 800L) {
                return true
            }
        }

        // In active Samsung Health workout (e.g. pause/stop workout), yield completely to stock OS
        if (isSamsungHealthWorkoutActive()) {
            return false
        }

        val hasDoubleTap = Mapping.get(this, "bottom_double_tap") != "none"
        val hasTripleTap = Mapping.get(this, "bottom_triple_tap") != "none"
        val hasLongPress = Mapping.get(this, "bottom_long") != "none"
        val anyMultiGesture = hasDoubleTap || hasTripleTap

        val now = System.currentTimeMillis()

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            handler.removeCallbacks(firePendingTaps)
            handler.removeCallbacks(onHoldTimeout)
            actionFired = false

            if (now - lastUpTime > 360L) {
                tapCount = 0
            }

            if (tapCount == 0 && hasLongPress) {
                handler.postDelayed(onHoldTimeout, 400L)
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(onHoldTimeout)
            lastUpTime = now

            if (!actionFired && !pendingWalletDismissAction) {
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
                // If action fired (or hold was triggered), do NOT emit BACK on key up!
                handler.removeCallbacks(firePendingTaps)
                tapCount = 0
                actionFired = false
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            currentActivePackage = pkg
            currentActiveClass = event.className?.toString()

            // Dynamic handover of Orange Action Button to Samsung Health during workout/exercise
            if (UtilityActions.hasHardwareActionButton(this) &&
                checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                if (pkg == "com.samsung.android.wear.shealth") {
                    try {
                        android.provider.Settings.Global.putString(
                            contentResolver,
                            "setting_action_button_press",
                            "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"
                        )
                    } catch (_: Throwable) {}
                } else if (pkg != "android" && pkg != "com.bennybar.ultraremapper") {
                    val isMasterOn = Mapping.isEnabled(this)
                    val target = if (!isMasterOn) {
                        "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"
                    } else {
                        "$packageName/${ActionButtonService::class.java.name}"
                    }
                    try {
                        val cur = android.provider.Settings.Global.getString(contentResolver, "setting_action_button_press")
                        if (cur != target) {
                            android.provider.Settings.Global.putString(contentResolver, "setting_action_button_press", target)
                        }
                    } catch (_: Throwable) {}
                }
            }

            // Clean interception: When Samsung Wallet opens upon bottom button hold,
            // dismiss Wallet and immediately bring our mapped action to the top!
            val isWalletOrPay = pkg.contains("wallet", ignoreCase = true) || pkg.contains("pay", ignoreCase = true)
            if (isWalletOrPay && pendingWalletDismissAction) {
                pendingWalletDismissAction = false
                handler.removeCallbacks(walletFallbackAction)
                Mapping.perform(this, "bottom_long", backToHome = false)
                UpdateChecker.checkOpportunistic(applicationContext)
                // Wait slightly for mapped activity to start before sending BACK so we never kill our own new activity
                handler.postDelayed({
                    if (currentActivePackage?.contains("wallet", ignoreCase = true) == true ||
                        currentActivePackage?.contains("pay", ignoreCase = true) == true
                    ) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }, 140L)
                return
            }

            if (pkg == "com.samsung.android.watch.flashlight") {
                isFlashlightWindowActive = true
                if (pendingBoostFromAction) {
                    pendingBoostFromAction = false
                    scheduleBoostTaps(1150L, 1290L)
                }
            } else if (pendingWirelessAdbAutoToggle) {
                tapHandler.removeCallbacksAndMessages(null)
                tapHandler.postDelayed({
                    if (pendingWirelessAdbAutoToggle) {
                        performWirelessAdbClick()
                    }
                }, 1200L)
            } else if (pkg != "android" && pkg != "com.bennybar.ultraremapper") {
                isFlashlightWindowActive = false
                pendingBoostFromAction = false
                pendingWirelessAdbAutoToggle = false
                tapHandler.removeCallbacksAndMessages(null)
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

    /**
     * Mirrors Samsung FlashLight `isBoostModeSupportedDevice()`:
     * Build.DEVICE contains "projectx2" (Ultra) or matches (wise|fresh)N with N >= 8.
     * Tap cycle: normal → max → boost (supported) | blink strobe (unsupported).
     */
    private fun isFlashlightBoostSupported(): Boolean {
        val device = android.os.Build.DEVICE ?: return false
        if (device.contains("projectx2")) return true
        val m = Regex("(?i)(wise|fresh)(\\d+)").find(device) ?: return false
        return (m.groupValues[2].toIntOrNull() ?: 0) >= 8
    }

    private fun scheduleBoostTaps(firstDelayMs: Long, secondDelayMs: Long) {
        tapHandler.removeCallbacksAndMessages(null)
        tapHandler.postDelayed({
            if (isSamsungFlashlightOnScreen()) tapCenter()
        }, firstDelayMs)
        if (!isFlashlightBoostSupported()) return
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

    fun autoToggleWirelessAdb() {
        setWindowTrackingEnabled(true)
        pendingWirelessAdbAutoToggle = true
        // Solid delay fallback (1200ms) to ensure activity layout and animations complete
        tapHandler.postDelayed({
            if (pendingWirelessAdbAutoToggle) {
                performWirelessAdbClick()
            }
        }, 1200L)
    }

    private fun performWirelessAdbClick() {
        try {
            val root = rootInActiveWindow ?: return

            // 1. Check if checkable switch exists on current screen (Wireless Debugging page)
            val checkableNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            findCheckableNodes(root, checkableNodes)

            for (node in checkableNodes) {
                if (!node.isChecked) {
                    node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    vibrateTick(50)
                    finishWirelessAdbAutoToggle()
                    return
                } else {
                    vibrateTick(30)
                    finishWirelessAdbAutoToggle()
                    return
                }
            }

            // 2. If no checkable node found, we may be on root Developer Options screen
            val keywords = listOf("inalámbrica", "wireless", "drahtlos", "sans fil", "debugging")
            for (kw in keywords) {
                val matches = root.findAccessibilityNodeInfosByText(kw)
                for (item in matches) {
                    var clickable: android.view.accessibility.AccessibilityNodeInfo? = item
                    while (clickable != null && !clickable.isClickable) {
                        clickable = clickable.parent
                    }
                    if (clickable != null && clickable.isClickable) {
                        clickable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        // Wait for sub-screen to load (1200ms) and toggle the switch
                        tapHandler.postDelayed({
                            performWirelessAdbClick()
                        }, 1200L)
                        return
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            tapHandler.postDelayed({
                finishWirelessAdbAutoToggle()
            }, 3000L)
        }
    }

    private fun finishWirelessAdbAutoToggle() {
        pendingWirelessAdbAutoToggle = false
        setWindowTrackingEnabled(false)
    }

    private fun findCheckableNodes(node: android.view.accessibility.AccessibilityNodeInfo?, list: MutableList<android.view.accessibility.AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isCheckable) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            findCheckableNodes(node.getChild(i), list)
        }
    }

    fun cancelFlashlightTaps() {
        isFlashlightWindowActive = false
        pendingBoostFromAction = false
        pendingWirelessAdbAutoToggle = false
        tapHandler.removeCallbacksAndMessages(null)
        setWindowTrackingEnabled(false)
    }
}
