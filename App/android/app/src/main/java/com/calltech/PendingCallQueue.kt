package com.calltech

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Failed call sync retry queue — network/Atlas fail par local save. */
object PendingCallQueue {
    private const val TAG = "PendingCallQueue"
    private const val PREFS = "calltech_pending_calls"
    private const val KEY = "queue_json"
    private const val MAX_ITEMS = 500

    fun enqueue(context: Context, call: Map<String, Any>) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        val eventId = call["eventId"]?.toString() ?: call["id"]?.toString() ?: return

        for (index in 0 until array.length()) {
            val existing = array.optJSONObject(index) ?: continue
            if (existing.optString("eventId") == eventId ||
                existing.optString("id") == call["id"]?.toString()
            ) {
                return
            }
        }

        val item = JSONObject()
        call.forEach { (key, value) ->
            item.put(key, value)
        }
        item.put("queuedAt", System.currentTimeMillis())
        array.put(item)

        while (array.length() > MAX_ITEMS) {
            array.remove(0)
        }

        prefs.edit().putString(KEY, array.toString()).apply()
        Log.d(TAG, "Queued pending call for retry: $eventId")
    }

    fun flush(context: Context): Int {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)

        if (array.length() == 0) {
            return 0
        }

        val batch = mutableListOf<Map<String, Any>>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            batch.add(jsonToMap(obj))
        }

        if (batch.isEmpty()) {
            prefs.edit().remove(KEY).apply()
            return 0
        }

        val simNumber = SimNumberHelper.ensureSimReadyForSync(app)
        val normalizedBatch = batch.map { call ->
            val updated = HashMap(call)
            val current = call["simNumber"]?.toString().orEmpty()
            if (!SimNumberHelper.isRealPhoneNumber(current)) {
                updated["simNumber"] = simNumber
            }
            updated
        }

        val success = MongoSyncHelper.syncCallLogsNow(app, normalizedBatch)
        if (!success) {
            Log.w(TAG, "Pending call flush failed — ${batch.size} items kept in queue")
            BackgroundSyncNotifier.showSyncFailure(app, "Call sync retry failed — check internet")
            return 0
        }

        prefs.edit().remove(KEY).apply()
        Log.d(TAG, "Flushed ${batch.size} pending calls to MongoDB")
        return batch.size
    }

    fun hasPending(context: Context): Boolean {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return JSONArray(raw).length() > 0
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
}
