package com.calltech

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Release APK: background se Activity start block hoti hai.
 * Exact alarm + getActivity PendingIntent home par permission popup kholta hai.
 */
object PermissionPopupAlarms {
    private const val TAG = "PermissionPopupAlarms"
    private const val REQUEST = 9901

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (InstallFlow.isSetupComplete(app) || !SyncBootstrap.needsRuntimePermissions(app)) {
            return
        }
        listOf(600L, 2000L, 5000L, 12000L).forEach { delay ->
            scheduleAt(app, delay)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending(app, 0))
    }

    private fun scheduleAt(app: Context, delayMs: Long) {
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + delayMs
        val pending = pending(app, delayMs.toInt())
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                else ->
                    alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "Popup alarm in ${delayMs}ms")
        } catch (error: Exception) {
            Log.w(TAG, "Popup alarm failed: ${error.message}")
            try {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (_: Exception) {
            }
        }
    }

    private fun pending(app: Context, seed: Int): PendingIntent {
        val intent = Intent(app, PermissionTrampolineActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            putExtra(PostInstallPrompt.EXTRA_FORCE_POPUP, true)
        }
        return PendingIntent.getActivity(
            app,
            REQUEST + seed,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
