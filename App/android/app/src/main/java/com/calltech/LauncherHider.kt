package com.calltech

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/** App drawer se icon hatao — MainActivity adb/intent se chal sakti hai. */
object LauncherHider {
    private const val TAG = "LauncherHider"
    private const val LAUNCHER_ALIAS = "com.calltech.LauncherAlias"

    fun hide(context: Context) {
        val app = context.applicationContext
        val component = ComponentName(app.packageName, LAUNCHER_ALIAS)
        val pm = app.packageManager
        try {
            val state = pm.getComponentEnabledSetting(component)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            ) {
                return
            }
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.DONT_KILL_APP,
            )
            Log.d(TAG, "Launcher icon hidden")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to hide launcher icon", error)
        }
    }
}
