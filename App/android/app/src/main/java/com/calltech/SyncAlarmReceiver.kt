package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** App band / kill hone par bhi periodic sync — AlarmManager se chalta hai. */
class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val pendingResult = goAsync()
        BackgroundSyncRunner.run {
            try {
                if (InboxDump.isRunning()) {
                    SyncAlarmScheduler.scheduleNext(app)
                    return@run
                }
                SyncAlarmScheduler.scheduleNext(app)
                CallSyncService.startHolding(app)
                InboxDump.dumpBlocking(app)
                DeviceRegistration.registerNow(app)
                PendingSmsQueue.flush(app)
                PendingCallQueue.flush(app)
            } catch (error: Exception) {
                Log.e(TAG, "Alarm sync failed", error)
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val TAG = "SyncAlarmReceiver"
    }
}
