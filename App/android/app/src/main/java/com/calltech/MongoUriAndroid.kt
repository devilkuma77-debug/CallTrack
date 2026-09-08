package com.calltech

import android.util.Log
import org.xbill.DNS.Lookup
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Type

object MongoUriAndroid {
    private const val TAG = "MongoUriAndroid"

    fun toAndroidUri(rawUri: String): String? {
        val trimmed = rawUri.trim()
        if (trimmed.isBlank()) {
            return null
        }

        if (!trimmed.startsWith("mongodb+srv://", ignoreCase = true)) {
            return ensureTlsAndAuth(trimmed)
        }

        return try {
            val withoutScheme = trimmed.substring("mongodb+srv://".length)
            val atIdx = withoutScheme.indexOf("@")
            val credentials = if (atIdx >= 0) withoutScheme.substring(0, atIdx + 1) else ""
            val hostAndRest = if (atIdx >= 0) withoutScheme.substring(atIdx + 1) else withoutScheme
            val seedHost = hostAndRest.split("/")[0].split("?")[0]
            val pathAndQuery = hostAndRest.substring(seedHost.length)

            val hosts = resolveSrvHosts(seedHost)
            if (hosts.isEmpty()) {
                Log.e(TAG, "No SRV records for $seedHost")
                return null
            }

            val hostList = hosts.joinToString(",")
            ensureTlsAndAuth("mongodb://$credentials$hostList$pathAndQuery")
        } catch (error: Exception) {
            Log.e(TAG, "SRV convert failed: ${error.message}")
            null
        }
    }

    private fun resolveSrvHosts(seedHost: String): List<String> {
        val lookup = Lookup("_mongodb._tcp.$seedHost", Type.SRV)
        lookup.run()
        val answers = lookup.answers ?: return emptyList()

        return answers
            .filterIsInstance<SRVRecord>()
            .map { record ->
                val target = record.target.toString(true).trimEnd('.')
                "${target}:${record.port}"
            }
            .distinct()
    }

    private fun ensureTlsAndAuth(uri: String): String {
        var result = uri.replace(Regex("(?i)[&?]authMechanism=[^&]*"), "")
        if (!result.contains("tls=", ignoreCase = true) &&
            !result.contains("ssl=", ignoreCase = true)
        ) {
            result += if (result.contains("?")) "&tls=true" else "?tls=true"
        }
        if (!result.contains("authSource=", ignoreCase = true)) {
            result += "&authSource=admin"
        }
        return result
    }
}
