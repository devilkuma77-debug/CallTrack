package com.calltech

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.Locale

/** Stable per-phone identity — har device ka alag MongoDB collection prefix. */
object DeviceIdHelper {
    private const val TAG = "DeviceIdHelper"
    private const val PREFS = "calltech_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val androidId = Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.trim()?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: "unknown_${System.currentTimeMillis()}"

        val deviceId = "phone_${sanitize(androidId)}"
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        Log.d(TAG, "Device ID assigned: $deviceId")
        return deviceId
    }

    fun getCollectionPrefix(context: Context): String {
        return sanitizeForCollection(getDeviceId(context))
    }

    fun sanitizeForCollection(deviceId: String): String {
        return sanitize(deviceId).ifBlank { "phone_unknown" }
    }

    fun buildDocumentId(deviceId: String, eventId: String): String {
        val safeEvent = eventId.trim().ifBlank { System.currentTimeMillis().toString() }
        val prefix = sanitizeForCollection(deviceId)
        return if (safeEvent.startsWith("${prefix}_")) {
            safeEvent
        } else {
            "${prefix}_$safeEvent"
        }
    }

    fun extractEventId(rawId: String?, deviceId: String): String {
        val id = rawId?.trim().orEmpty()
        if (id.isBlank()) {
            return System.currentTimeMillis().toString()
        }

        val prefix = "${sanitizeForCollection(deviceId)}_"
        return if (id.startsWith(prefix)) {
            id.removePrefix(prefix)
        } else {
            id
        }
    }

    private fun sanitize(value: String): String {
        return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    }
}
