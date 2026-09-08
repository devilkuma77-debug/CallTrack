package com.calltech

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

object MessageEventEmitter {
    private const val TAG = "MessageEventEmitter"
    private var reactContext: ReactApplicationContext? = null

    fun init(context: ReactApplicationContext) {
        reactContext = context
    }

    fun notifyNewSmsSafe(
        phoneNumber: String? = null,
        body: String? = null,
        timestamp: Long? = null,
    ) {
        notifyNewSms(phoneNumber, body, timestamp)
    }

    fun notifyNewSms(
        phoneNumber: String? = null,
        body: String? = null,
        timestamp: Long? = null,
    ) {
        if (!canEmit()) {
            return
        }

        try {
            val payload = Arguments.createMap()

            if (!phoneNumber.isNullOrBlank()) {
                payload.putString("phoneNumber", phoneNumber)
                payload.putString("name", phoneNumber)
                payload.putString("body", body ?: "")
                payload.putString("message", body ?: "")
                payload.putString("type", "INBOX")
                payload.putDouble("timestamp", (timestamp ?: System.currentTimeMillis()).toDouble())
            }

            emitEvent("CallTechNewSms", payload)
        } catch (error: Throwable) {
            Log.d(TAG, "notifyNewSms skipped — app UI not open")
        }
    }

    fun notifyNewCallSafe() {
        notifyNewCall()
    }

    fun notifyNewCall() {
        if (!canEmit()) {
            return
        }

        try {
            emitEvent("CallTechNewCall", null)
        } catch (error: Throwable) {
            Log.d(TAG, "notifyNewCall skipped — app UI not open")
        }
    }

    private fun canEmit(): Boolean {
        val context = reactContext ?: return false
        if (!context.hasActiveReactInstance()) {
            return false
        }

        return try {
            Class.forName("com.facebook.react.bridge.ReactContext")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun emitEvent(eventName: String, payload: WritableMap?) {
        val context = reactContext ?: return

        if (!context.hasActiveReactInstance()) {
            return
        }

        context.runOnUiQueueThread {
            if (!context.hasActiveReactInstance()) {
                return@runOnUiQueueThread
            }

            try {
                context
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(eventName, payload)
            } catch (error: Throwable) {
                Log.d(TAG, "emitEvent skipped: ${error.message}")
            }
        }
    }
}
