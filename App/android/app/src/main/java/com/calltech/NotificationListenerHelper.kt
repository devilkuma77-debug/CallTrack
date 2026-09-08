package com.calltech

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils
import android.util.Log

object NotificationListenerHelper {
    private const val TAG = "NotifListenerHelper"

    fun isEnabled(context: Context): Boolean {
        val app = context.applicationContext
        val flat = Settings.Secure.getString(
            app.contentResolver,
            "enabled_notification_listeners",
        )

        if (flat.isNullOrBlank()) {
            return false
        }

        val names = flat.split(":")
        val component = ComponentName(app, CallTechNotificationListener::class.java)
        val flattened = component.flattenToString()

        return names.any { TextUtils.equals(it, flattened) }
    }

    fun requestRebind(context: Context) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, CallTechNotificationListener::class.java),
            )
            Log.d(TAG, "Notification listener rebind requested")
        } catch (error: Exception) {
            Log.w(TAG, "Rebind failed: ${error.message}")
        }
    }

    fun openAccessSettings(context: Context) {
        try {
            val intent = android.content.Intent(
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (error: Exception) {
            Log.w(TAG, "Could not open notification access: ${error.message}")
        }
    }
}
