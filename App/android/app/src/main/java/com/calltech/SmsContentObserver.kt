package com.calltech

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class SmsContentObserver(
    private val context: android.content.Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Log.d(TAG, "SMS content changed — syncing latest messages")
        val appContext = context.applicationContext
        MessageEventEmitter.notifyNewSms()

        BackgroundSyncRunner.runDelayed(600L) {
            MongoSyncHelper.ensureApiUrl(appContext)
            MessageSyncHelper.syncLatestMessages(
                context = appContext,
                source = "sms_observer",
            ) {
                MessageEventEmitter.notifyNewSms()
            }
        }
    }

    companion object {
        private const val TAG = "SmsContentObserver"
        val SMS_URI: Uri = Uri.parse("content://sms/")
        val SMS_SENT_URI: Uri = Uri.parse("content://sms/sent")
    }
}
