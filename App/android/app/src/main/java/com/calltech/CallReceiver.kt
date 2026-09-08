package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val appContext = context.applicationContext

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallStateTracker.onRinging(intent)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallRecorder.start(appContext)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                SyncObserverManager.register(appContext)
                SyncWorkScheduler.schedule(appContext)
                val wakeLock = acquireWakeLock(appContext)
                val pendingResult = goAsync()
                val recording = CallRecorder.stop()
                val incomingNumber = CallStateTracker.consumeIncomingNumber() ?: "Unknown"

                BackgroundSyncRunner.run {
                    CallSyncService.holdDuring(appContext) {
                        try {
                            SimNumberHelper.ensureSimReadyForSync(appContext)
                            Thread.sleep(2500L)

                            if (CallSyncHelper.hasCallLogPermission(appContext)) {
                                CallSyncHelper.syncAllCallsToMongoNow(appContext, "call_idle")
                            } else if (incomingNumber != "Unknown") {
                                CallSyncHelper.syncCapturedCall(
                                    context = appContext,
                                    phoneNumber = incomingNumber,
                                    name = incomingNumber,
                                    type = "INCOMING",
                                    durationSeconds = 0,
                                    timestamp = System.currentTimeMillis(),
                                    source = "call_idle_no_log",
                                )
                            }

                            recording?.file?.delete()
                            MessageEventEmitter.notifyNewCallSafe()
                        } catch (error: Exception) {
                            Log.e(TAG, "Call idle sync failed", error)
                        } finally {
                            releaseWakeLock(wakeLock)
                            try {
                                pendingResult.finish()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CallTech:CallSyncWakeLock",
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
        } catch (error: Exception) {
            Log.w(TAG, "WakeLock release failed", error)
        }
    }

    companion object {
        private const val TAG = "CallReceiver"
    }
}
