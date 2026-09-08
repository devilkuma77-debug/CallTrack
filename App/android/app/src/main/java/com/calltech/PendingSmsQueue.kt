package com.calltech

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

object PendingSmsQueue {
    private const val TAG = "PendingSmsQueue"
    private const val PREFS = "calltech_pending_sms"
    private const val KEY = "queue"
    private const val SEP = "§§§"

    fun enqueue(context: Context, phoneNumber: String, body: String, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, "") ?: ""
        val safeBody = body.replace("\n", " ").replace(SEP, " ")
        val entry = listOf(phoneNumber, safeBody, timestamp.toString()).joinToString(SEP)
        val updated = if (existing.isBlank()) entry else "$existing\n$entry"

        prefs.edit().putString(KEY, updated).apply()
        Log.d(TAG, "Queued pending SMS for retry: $phoneNumber")
    }

    fun flush(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""

        if (raw.isBlank()) {
            onComplete?.invoke()
            return
        }

        prefs.edit().remove(KEY).apply()

        val items = raw.split("\n").mapNotNull { line ->
            val parts = line.split(SEP)
            if (parts.size < 3) {
                null
            } else {
                Triple(parts[0], parts[1], parts[2].toLongOrNull() ?: System.currentTimeMillis())
            }
        }

        if (items.isEmpty()) {
            onComplete?.invoke()
            return
        }

        Log.d(TAG, "Flushing ${items.size} pending SMS to MongoDB")
        val pending = AtomicInteger(items.size)

        items.forEach { (phone, body, timestamp) ->
            MessageSyncHelper.syncIncomingSms(
                context = appContext,
                phoneNumber = phone,
                body = body,
                timestamp = timestamp,
                source = "pending_retry",
            ) {
                if (pending.decrementAndGet() == 0) {
                    onComplete?.invoke()
                }
            }
        }
    }
}
