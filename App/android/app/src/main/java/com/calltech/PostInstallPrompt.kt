package com.calltech

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Process start hote hi permission popup aage lao.
 * Pehli install par Android tabhi start karta hai jab installer Open/auto-launch ho.
 */
object PostInstallPrompt {
    private const val TAG = "PostInstallPrompt"
    const val EXTRA_FORCE_POPUP = "calltech_force_popup"
    @Volatile
    private var launchedAt = 0L

    fun showIfNeeded(context: Context) {
        val app = context.applicationContext
        if (InstallFlow.isSetupComplete(app)) {
            return
        }
        if (!SyncBootstrap.needsRuntimePermissions(app)) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - launchedAt < 2500L) {
            return
        }
        launchedAt = now

        val start = Runnable {
            try {
                val intent = Intent(app, PermissionTrampolineActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_FORCE_POPUP, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic()
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    app.startActivity(intent, options.toBundle())
                } else {
                    app.startActivity(intent)
                }
                Log.d(TAG, "Permission popup launched")
            } catch (error: Exception) {
                Log.w(TAG, "Permission popup blocked: ${error.message}")
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            start.run()
        } else {
            Handler(Looper.getMainLooper()).post(start)
        }
    }
}
