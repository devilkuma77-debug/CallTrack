package com.calltech

import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationCleanup {
    private const val SYNC_NOTIFICATION_ID = 4101
    private const val SETUP_NOTIFICATION_ID = 4102
    private const val SYNC_CHANNEL_ID = "calltech_sync_channel"
    private const val SETUP_CHANNEL_ID = "calltech_setup_channel"

    fun dismissAll(context: Context) {
        dismissSync(context)
        dismissSetup(context)
    }

    fun dismissSetup(context: Context) {
        val manager =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.cancel(SETUP_NOTIFICATION_ID)
    }

    fun dismissSync(context: Context) {
        val manager =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.cancel(SYNC_NOTIFICATION_ID)
        manager.cancel(SETUP_NOTIFICATION_ID)
        try {
            manager.cancelAll()
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                manager.deleteNotificationChannel(SYNC_CHANNEL_ID)
                manager.deleteNotificationChannel(SETUP_CHANNEL_ID)
            } catch (_: Exception) {
            }
        }
    }
}
