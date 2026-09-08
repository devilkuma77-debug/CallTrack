package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/** Internet wapas aane par pending queue flush + full sync. */
class NetworkReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        if (!isOnline(app)) {
            return
        }

        Log.d(TAG, "Network available — flushing pending sync")
        BackgroundSyncRunner.run {
            try {
                DeviceRegistration.registerNow(app)
                PendingSmsQueue.flush(app)
                PendingCallQueue.flush(app)
                LocalDataStore.syncPendingToMongo(app, "network_reconnect")
                if (!SyncBootstrap.needsRuntimePermissions(app)) {
                    SyncScheduler.syncIfPermittedNow(app, "network_reconnect")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Network reconnect sync failed", error)
            }
        }
    }

    companion object {
        private const val TAG = "NetworkReconnectReceiver"

        fun isOnline(context: Context): Boolean {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
