package com.aaronhrndz.ultraremapper

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TopDoubleTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (Mapping.isEnabled(this)) {
                val action = Mapping.get(this, "top_double")
                if (action != "none") {
                    Mapping.perform(this, "top_double", backToHome = true)
                } else {
                    fallbackToRecentApps()
                }
            } else {
                fallbackToRecentApps()
            }
        } catch (_: Throwable) {
        } finally {
            finish()
        }
    }

    private fun fallbackToRecentApps() {
        try {
            val intent = Intent().apply {
                setClassName(
                    "com.samsung.android.apps.wearable.recent",
                    "com.samsung.android.apps.wearable.recent.RecentActivityAlias"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Throwable) {}
    }
}
