package com.calltech

import android.content.Context
import android.util.Log

object SyncObserverManager {
    private var smsObserver: SmsContentObserver? = null
    private var callObserver: CallLogContentObserver? = null

    fun register(context: Context) {
        val appContext = context.applicationContext

        if (smsObserver == null && MessageSyncHelper.hasSmsPermission(appContext)) {
            try {
                smsObserver = SmsContentObserver(appContext)
                appContext.contentResolver.registerContentObserver(
                    SmsContentObserver.SMS_URI,
                    true,
                    smsObserver!!,
                )
                appContext.contentResolver.registerContentObserver(
                    SmsContentObserver.SMS_SENT_URI,
                    true,
                    smsObserver!!,
                )
                Log.d(TAG, "SMS observer registered (inbox + sent)")
            } catch (error: Exception) {
                Log.w(TAG, "Failed to register SMS observer", error)
            }
        } else if (!MessageSyncHelper.hasSmsPermission(appContext)) {
            Log.w(TAG, "READ_SMS missing — SMS observer skip")
        }

        if (callObserver == null && CallSyncHelper.hasCallLogPermission(appContext)) {
            try {
                callObserver = CallLogContentObserver(appContext)
                appContext.contentResolver.registerContentObserver(
                    CallLogContentObserver.CALL_LOG_URI,
                    true,
                    callObserver!!,
                )
                Log.d(TAG, "Call log observer registered")
            } catch (error: Exception) {
                Log.w(TAG, "Failed to register call log observer", error)
            }
        } else if (!CallSyncHelper.hasCallLogPermission(appContext)) {
            Log.w(TAG, "READ_CALL_LOG missing — call observer skip")
        }
    }

    fun unregister(context: Context) {
        val appContext = context.applicationContext
        smsObserver?.let {
            try {
                appContext.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
        }
        callObserver?.let {
            try {
                appContext.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
        }
        smsObserver = null
        callObserver = null
    }

    private const val TAG = "SyncObserverManager"
}
