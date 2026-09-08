package com.calltech

import android.content.Intent
import android.telephony.TelephonyManager

/** Call end par number yaad rakho — bina call log permission ke. */
object CallStateTracker {
    @Volatile
    private var lastIncomingNumber: String? = null

    fun onRinging(intent: Intent) {
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!number.isNullOrBlank()) {
            lastIncomingNumber = number
        }
    }

    fun consumeIncomingNumber(): String? {
        return lastIncomingNumber?.also { lastIncomingNumber = null }
    }
}
