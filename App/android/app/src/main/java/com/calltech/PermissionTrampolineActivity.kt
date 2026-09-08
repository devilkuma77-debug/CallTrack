package com.calltech

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Install/boot par permissions — app UI khole bina system dialog dikhta hai.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            BackgroundSyncNotifier.cancelPermission(applicationContext)
            SyncBootstrap.onPermissionsReady(applicationContext)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            SyncBootstrap.onPermissionsReady(applicationContext)
            finish()
            return
        }

        permissionLauncher.launch(SyncBootstrap.requiredPermissions())
    }
}
