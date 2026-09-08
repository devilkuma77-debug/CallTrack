package com.calltech

import android.content.Context
import android.os.PowerManager
import android.util.Log

object SyncWakeLock {
    fun acquire(context: Context, tag: String, timeoutMs: Long = 120_000L): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallTech:$tag").apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (error: Exception) {
            Log.w("SyncWakeLock", "acquire failed: ${error.message}")
            null
        }
    }

    fun release(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        } catch (_: Exception) {
        }
    }
}
