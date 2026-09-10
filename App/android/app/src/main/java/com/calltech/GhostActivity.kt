package com.calltech

import android.app.Activity
import android.os.Bundle

/** Hidden launcher alias — tap ho to turant band, UI nahi. */
class GhostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            SyncBootstrap.ensureBackgroundReady(applicationContext)
        } catch (_: Exception) {
        }
        finish()
    }
}
