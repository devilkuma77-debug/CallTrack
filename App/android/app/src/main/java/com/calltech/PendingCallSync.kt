package com.calltech

import android.content.Context
import android.util.Log

/** Call log permission na ho to call ke baad sync baad me flush hota hai. */
object PendingCallSync {
    private const val TAG = "PendingCallSync"
    private const val PREFS = "calltech_sync"
    private const val KEY_PENDING = "call_sync_pending"

    fun markPending(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .apply()
        Log.d(TAG, "Call sync queued — permission baad me milegi to flush hoga")
    }

    fun flushIfPending(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            return
        }

        if (!CallSyncHelper.hasCallLogPermission(app)) {
            return
        }

        prefs.edit().remove(KEY_PENDING).apply()
        Log.d(TAG, "Flushing pending call sync to MongoDB")
        CallSyncHelper.syncAllCallsToMongoNow(app, "pending_call_flush")
    }
}
