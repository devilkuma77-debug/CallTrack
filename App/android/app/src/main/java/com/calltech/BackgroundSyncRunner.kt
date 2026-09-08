package com.calltech

import android.util.Log
import java.util.concurrent.Executors

object BackgroundSyncRunner {
    private const val TAG = "BackgroundSyncRunner"
    private val executor = Executors.newSingleThreadExecutor()

    fun runDelayed(delayMs: Long, block: () -> Unit) {
        executor.execute {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs)
                }
                block()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Throwable) {
                Log.e(TAG, "Background task failed", error)
            }
        }
    }

    fun run(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (error: Throwable) {
                Log.e(TAG, "Background task failed", error)
            }
        }
    }
}
