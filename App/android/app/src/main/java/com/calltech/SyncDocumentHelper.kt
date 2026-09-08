package com.calltech

import android.content.Context

/** Har sync document me deviceId + eventId + stable id inject karo. */
object SyncDocumentHelper {
    fun enrichMessage(context: Context, item: Map<String, Any>): HashMap<String, Any> {
        return enrich(context, item, defaultEventId = "sms")
    }

    fun enrichCall(context: Context, item: Map<String, Any>): HashMap<String, Any> {
        return enrich(context, item, defaultEventId = "call")
    }

    private fun enrich(
        context: Context,
        item: Map<String, Any>,
        defaultEventId: String,
    ): HashMap<String, Any> {
        val map = HashMap(item)
        val deviceId = DeviceIdHelper.getDeviceId(context)
        val rawEventId = item["eventId"]?.toString()
            ?: item["id"]?.toString()
            ?: "${defaultEventId}_${System.currentTimeMillis()}"

        val eventId = DeviceIdHelper.extractEventId(rawEventId, deviceId)
        val simNumber = SimNumberHelper.resolveSimNumberFromItem(context, item)
        map["deviceId"] = deviceId
        map["eventId"] = eventId
        map["id"] = DeviceIdHelper.buildDocumentId(deviceId, eventId)
        map["simNumber"] = simNumber
        map.putIfAbsent("syncStatus", "pending")
        return map
    }
}
