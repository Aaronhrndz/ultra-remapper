package com.aaronhrndz.ultraremapper

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class TocTocTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        try {
            if (Mapping.isEnabled(appContext)) {
                val action = Mapping.get(appContext, "toctoc")
                if (action != "none") {
                    // Dispatch on main handler of applicationContext to ensure delivery before finish
                    Handler(Looper.getMainLooper()).post {
                        try {
                            Mapping.perform(appContext, "toctoc", backToHome = true)
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            finish()
        }
    }
}
