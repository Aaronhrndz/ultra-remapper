package com.aaronhrndz.ultraremapper

import com.aaronhrndz.ultraremapper.R

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class RedFlashlightActivity : Activity() {

    companion object {
        @Volatile
        var currentInstance: RedFlashlightActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.attributes = window.attributes.apply {
            screenBrightness = 1.0f
        }

        val root = View(this).apply {
            setBackgroundColor(0xFFFF0000.toInt())
            // Tap screen to close
            setOnClickListener { finish() }
        }
        setContentView(root)
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
