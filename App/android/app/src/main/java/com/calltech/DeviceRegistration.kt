package com.calltech

import android.content.Context
import android.os.Build
import android.util.Log

/** Device / SIM ko live Render API par register karo — Admin list yahi se aati hai. */
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

        var deviceOk = false
        repeat(3) { attempt ->
            deviceOk = MongoSyncHelper.postApi(app, "/devices/register", payload)
            if (deviceOk) {
                Log.d(TAG, "Device registered via HTTP: $deviceId / $simNumber")
                return@repeat
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(1500L * (attempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        if (deviceOk) {
            // already logged
        } else if (AtlasDirectSync.isConfigured() && !AtlasDirectSync.isUnreachable()) {
            deviceOk = AtlasDirectSync.registerDevice(
                deviceId,
                simNumber,
                Build.MODEL ?: "unknown",
                Build.MANUFACTURER ?: "unknown",
            )
            if (deviceOk) {
                Log.d(TAG, "Device registered via Atlas Direct: $deviceId / $simNumber")
            } else {
                Log.e(TAG, "Device/SIM HTTP+Atlas register failed: $deviceId / $simNumber")
            }
        } else {
            Log.e(TAG, "HTTP /devices/register failed: $deviceId / $simNumber")
        }

        val simOk = SimNumberHelper.registerAllSimsInMongo(app)
        val ok = deviceOk || simOk
        if (!ok) {
            Log.e(TAG, "Device/SIM registration failed for $deviceId")
        }
        return ok
    }
}
