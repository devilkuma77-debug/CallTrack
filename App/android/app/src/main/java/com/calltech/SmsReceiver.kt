package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
            return
        }

        val appContext = context.applicationContext
        SyncBootstrap.ensureBackgroundReady(appContext)
        CallSyncService.ensureRunning(appContext)
        val wakeLock = acquireWakeLock(appContext)
        val pendingResult = goAsync()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isNullOrEmpty()) {
            finishSmsSync(wakeLock, pendingResult)
            return
        }

        val subscriptionId = readSmsSubscriptionId(intent)

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(appContext) {
                try {
                    SimNumberHelper.discoverAndCacheNumbers(appContext)
                messages.forEach { sms ->
                    val address = sms.originatingAddress ?: sms.emailFrom ?: "Unknown"
                    val body = sms.messageBody ?: ""
                    val timestamp = if (sms.timestampMillis > 0L) {
                        sms.timestampMillis
                    } else {
                        System.currentTimeMillis()
                    }

                    SimNumberHelper.tryLearnNumberFromSmsBody(
                        appContext,
                        address,
                        body,
                        subscriptionId,
                    )

                    LocalDataStore.saveMessage(
                        context = appContext,
                        phoneNumber = address,
                        body = body,
                        timestamp = timestamp,
                        source = "kill_state_sms",
                    )

                    MessageSyncHelper.syncIncomingSms(
                        context = appContext,
                        phoneNumber = address,
                        body = body,
                        timestamp = timestamp,
                        source = "kill_state_sms",
                        subscriptionId = subscriptionId,
                    )
                }

                MessageSyncHelper.syncLatestMessagesNow(appContext, "kill_state_sms_push")
                CallSyncHelper.syncAllCallsToMongoNow(appContext, "kill_state_after_sms")
                } catch (error: Exception) {
                    Log.e(TAG, "Kill-state SMS sync failed", error)
                } finally {
                    finishSmsSync(wakeLock, pendingResult)
                }
            }
        }
    }

    private fun finishSmsSync(
        wakeLock: PowerManager.WakeLock?,
        pendingResult: PendingResult,
    ) {
        releaseWakeLock(wakeLock)
        try {
            pendingResult.finish()
        } catch (_: Exception) {
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CallTech:SmsSyncWakeLock",
            ).apply {
                setReferenceCounted(false)
                acquire(90_000L)
            }
        } catch (error: Exception) {
            Log.w(TAG, "WakeLock acquire failed", error)
            null
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"

        private fun readSmsSubscriptionId(intent: Intent): Int? {
            val direct = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            if (direct != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return direct
            }

            val indexed = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            if (indexed != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return indexed
            }

            return null
        }
    }
}
