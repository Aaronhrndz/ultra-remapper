package com.aaronhrndz.ultraremapper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.*

class RedFlashlightActivity : Activity() {

    companion object {
        @Volatile
        var currentInstance: RedFlashlightActivity? = null
        private const val PREFS_NAME = "rgbw_flashlight_prefs"
        private const val KEY_HUE = "saved_hue"
        private const val KEY_SAT = "saved_sat"
    }

    private lateinit var flashlightView: RgbwFlashlightView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.attributes = window.attributes.apply {
            screenBrightness = 1.0f
        }

        val initialHue = prefs.getFloat(KEY_HUE, 0f) // Default red
        val initialSat = prefs.getFloat(KEY_SAT, 1f) // Default pure saturation

        flashlightView = RgbwFlashlightView(
            context = this,
            initialHue = initialHue,
            initialSat = initialSat,
            onColorChanged = { hue, sat ->
                prefs.edit().putFloat(KEY_HUE, hue).putFloat(KEY_SAT, sat).apply()
            },
            onCloseRequested = {
                finish()
            }
        )

        setContentView(flashlightView)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)
            flashlightView.onRotaryScroll(delta)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        currentInstance = this
        window.attributes = window.attributes.apply {
            screenBrightness = 1.0f
        }
    }

    override fun onDestroy() {
        if (currentInstance === this) {
            currentInstance = null
        }
        super.onDestroy()
    }
}

/**
 * Custom full-screen interactive view for RGBW flashlight.
 * - Draws full-screen current color background.
 * - Shows an introductory HUD banner fading out after 2.5s.
 * - Rotary scroll rotates Hue with tick haptics.
 * - On touch down, displays HSV radial color disc (center = white, edge = pure color).
 * - On drag, dynamically changes Hue & Saturation (blend with white).
 * - On touch up, animates disc fade-out (1s) to reveal clean full-screen color.
 */
@SuppressLint("ViewConstructor")
class RgbwFlashlightView(
    context: Context,
    initialHue: Float,
    initialSat: Float,
    private val onColorChanged: (hue: Float, sat: Float) -> Unit,
    private val onCloseRequested: () -> Unit
) : View(context) {

    private var currentHue: Float = initialHue.coerceIn(0f, 360f)
    private var currentSat: Float = initialSat.coerceIn(0f, 1f)

    private val bgPaint = Paint().apply { isAntiAlias = true }

    // Banner HUD
    private var bannerAlpha: Float = 1.0f
    private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(195, 24, 26, 32)
        style = Paint.Style.FILL
    }
    private val bannerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 21f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bannerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 200, 205, 215)
        textSize = 17f
        textAlign = Paint.Align.CENTER
    }

    // Color wheel properties
    private var wheelAlpha: Float = 0f
    private var isDragging: Boolean = false
    private var wheelRadius: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    private var colorWheelBitmap: Bitmap? = null
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val pickerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var wheelFadeAnimator: ValueAnimator? = null
    private var lastTapTime: Long = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateBackgroundColor()

        // Banner auto fade-out after 2.5s
        postDelayed({
            startBannerFadeOut()
        }, 2500L)
    }

    private fun startBannerFadeOut() {
        val anim = ValueAnimator.ofFloat(bannerAlpha, 0f).apply {
            duration = 600L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                bannerAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        anim.start()
    }

    private fun updateBackgroundColor() {
        val hsv = floatArrayOf(currentHue, currentSat, 1.0f)
        bgPaint.color = Color.HSVToColor(hsv)
        invalidate()
    }

    fun onRotaryScroll(delta: Float) {
        // Delta > 0 = clockwise, < 0 = counter-clockwise
        currentHue = (currentHue + delta * 12f) % 360f
        if (currentHue < 0) currentHue += 360f
        HapticFeedbackHelper.playRotaryTick(context)
        updateBackgroundColor()
        onColorChanged(currentHue, currentSat)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        wheelRadius = min(w, h) * 0.44f
        generateWheelBitmap((wheelRadius * 2).toInt())
    }

    /**
     * Generates HSV circular color wheel with center white and saturated outer rim.
     */
    private fun generateWheelBitmap(size: Int) {
        if (size <= 0) return
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw angular color sweep
        val colors = intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
        )
        val sweep = SweepGradient(r, r, colors, null)
        paint.shader = sweep
        canvas.drawCircle(r, r, r, paint)

        // Blend white radial gradient from center to edge (Saturation 0 -> 1)
        val radial = RadialGradient(
            r, r, r,
            Color.WHITE,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = radial
        }
        canvas.drawCircle(r, r, r, blendPaint)

        colorWheelBitmap = bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Full-screen solid background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Color wheel overlay (when visible during touch or fading out)
        if (wheelAlpha > 0f && colorWheelBitmap != null) {
            wheelPaint.alpha = (wheelAlpha * 255).toInt().coerceIn(0, 255)
            val left = centerX - wheelRadius
            val top = centerY - wheelRadius
            canvas.drawBitmap(colorWheelBitmap!!, left, top, wheelPaint)

            // Current picker indicator
            val angleRad = Math.toRadians((360.0 - currentHue).toDouble())
            val dist = currentSat * wheelRadius
            val pickX = (centerX + dist * cos(angleRad)).toFloat()
            val pickY = (centerY + dist * sin(angleRad)).toFloat()

            pickerPaint.alpha = (wheelAlpha * 255).toInt().coerceIn(0, 255)
            pickerInnerPaint.alpha = (wheelAlpha * 255).toInt().coerceIn(0, 255)
            pickerInnerPaint.color = bgPaint.color

            canvas.drawCircle(pickX, pickY, 14f, pickerPaint)
            canvas.drawCircle(pickX, pickY, 11f, pickerInnerPaint)
        }

        // 3. Introductory banner HUD
        if (bannerAlpha > 0f) {
            val bWidth = width * 0.76f
            val bHeight = 84f
            val bLeft = (width - bWidth) / 2f
            val bTop = height * 0.12f
            val rect = RectF(bLeft, bTop, bLeft + bWidth, bTop + bHeight)

            bannerPaint.alpha = (bannerAlpha * 195).toInt().coerceIn(0, 195)
            bannerBorderPaint.alpha = (bannerAlpha * 80).toInt().coerceIn(0, 80)
            bannerTextPaint.alpha = (bannerAlpha * 255).toInt().coerceIn(0, 255)
            bannerSubPaint.alpha = (bannerAlpha * 220).toInt().coerceIn(0, 220)

            canvas.drawRoundRect(rect, 22f, 22f, bannerPaint)
            canvas.drawRoundRect(rect, 22f, 22f, bannerBorderPaint)

            canvas.drawText(
                context.getString(R.string.flashlight_hud_title),
                centerX,
                bTop + 34f,
                bannerTextPaint
            )
            canvas.drawText(
                context.getString(R.string.flashlight_hud_sub),
                centerX,
                bTop + 62f,
                bannerSubPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 320L) {
                    // Double tap closes flashlight
                    onCloseRequested()
                    return true
                }
                lastTapTime = now

                wheelFadeAnimator?.cancel()
                isDragging = true
                wheelAlpha = 1.0f
                bannerAlpha = 0f // Dismiss banner instantly when user interacts
                updateColorFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateColorFromTouch(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    // 1 second smooth fadeout of the wheel
                    wheelFadeAnimator = ValueAnimator.ofFloat(1.0f, 0f).apply {
                        duration = 1000L
                        interpolator = AccelerateDecelerateInterpolator()
                        addUpdateListener {
                            wheelAlpha = it.animatedValue as Float
                            invalidate()
                        }
                    }
                    wheelFadeAnimator?.start()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateColorFromTouch(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // Saturation is 0 at center, 1 at edge
        val newSat = (dist / wheelRadius).coerceIn(0f, 1f)

        // Angle determines hue
        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angleDeg < 0) angleDeg += 360f
        val newHue = (360f - angleDeg) % 360f

        currentHue = newHue
        currentSat = newSat
        updateBackgroundColor()
        onColorChanged(currentHue, currentSat)
    }
}
