package com.calltech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/** App drawer se icon hatao — MainActivity adb/intent se chal sakti hai. */
object LauncherHider {
    private const val TAG = "LauncherHider"

    fun hide(context: Context) {
        val app = context.applicationContext
        val pm = app.packageManager
        val components = listOf(
            ComponentName(app.packageName, "com.calltech.LauncherAlias"),
            ComponentName(app.packageName, "${app.packageName}.LauncherAlias"),
        )

        for (component in components.distinctBy { it.className }) {
            try {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0,
                )
                Log.d(TAG, "Launcher disabled: ${component.className}")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to hide ${component.className}: ${error.message}")
            }
        }

        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(home)
        } catch (error: Exception) {
            Log.w(TAG, "Home launch after hide failed: ${error.message}")
        }
    }
}
