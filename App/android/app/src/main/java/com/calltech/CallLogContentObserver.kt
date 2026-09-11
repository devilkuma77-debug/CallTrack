package com.calltech

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log

class CallLogContentObserver(
    private val context: android.content.Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Log.d(TAG, "Call log changed — syncing latest calls")
        val appContext = context.applicationContext
        MessageEventEmitter.notifyNewCallSafe()

        BackgroundSyncRunner.runDelayed(400L) {
            MongoSyncHelper.ensureApiUrl(appContext)
            CallSyncHelper.syncLatestCalls(
                context = appContext,
                source = "call_observer",
                retryCount = 2,
            ) {
                MessageEventEmitter.notifyNewCallSafe()
            }
        }
    }

    companion object {
        private const val TAG = "CallLogContentObserver"
        val CALL_LOG_URI: Uri = CallLog.Calls.CONTENT_URI
    }
}
