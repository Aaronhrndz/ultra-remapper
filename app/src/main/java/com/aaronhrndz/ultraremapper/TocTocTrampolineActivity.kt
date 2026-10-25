package com.aaronhrndz.ultraremapper

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TocTocTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (Mapping.isEnabled(this)) {
                val action = Mapping.get(this, "toctoc")
                if (action != "none") {
                    Mapping.perform(this, "toctoc", backToHome = true)
                }
            }
        } catch (_: Throwable) {
        } finally {
            finish()
        }
    }
}
