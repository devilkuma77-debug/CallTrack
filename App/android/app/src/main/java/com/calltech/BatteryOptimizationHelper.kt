package com.calltech

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimizationHelper"

    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val app = context.applicationContext
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(app.packageName)
    }

    /** Activity se system Allow dialog — Realme/ColorOS kill rokne ke liye. */
    fun requestFromActivity(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoring(activity)) {
            return false
        }
        return try {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                },
            )
            Log.d(TAG, "Battery optimization exemption requested")
            true
        } catch (error: Exception) {
            Log.w(TAG, "Battery opt request failed: ${error.message}")
            false
        }
    }

    fun requestIgnoreIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val app = context.applicationContext
        if (isIgnoring(app)) {
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            Log.d(TAG, "Battery optimization exemption requested")
        } catch (error: Exception) {
            Log.w(TAG, "Battery opt request failed: ${error.message}")
        }
    }

    /** Oppo / Realme / ColorOS autostart settings */
    fun openAutostartSettings(context: Context): Boolean {
        val app = context.applicationContext
        val intents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                ),
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            },
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                Log.d(TAG, "Opened autostart/settings screen")
                return true
            } catch (_: Exception) {
                // try next
            }
        }

        return false
    }
}
