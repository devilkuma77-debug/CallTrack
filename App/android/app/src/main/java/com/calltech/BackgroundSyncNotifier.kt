package com.calltech

import android.content.Context
import android.util.Log

object BackgroundSyncNotifier {
    private const val TAG = "BackgroundSyncNotifier"

    /** Permission notification disabled. */
    fun showPermissionRequired(context: Context) {
        cancelPermission(context)
        Log.d(TAG, "Permission notification suppressed")
    }

    /** Sync failure notification disabled — silent retry in background. */
    fun showSyncFailure(context: Context, message: String) {
        Log.w(TAG, "Sync issue (silent): $message")
    }

    fun cancelPermission(context: Context) {
        val manager = context.applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        manager.cancel(4102)
        manager.cancel(4103)
    }
}
