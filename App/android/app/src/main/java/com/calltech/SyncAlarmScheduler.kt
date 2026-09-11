package com.calltech

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object SyncAlarmScheduler {
    private const val TAG = "SyncAlarmScheduler"
    private const val REQUEST_CODE = 8801
    /** App kill hone par bhi sync — dump complete hone tak dheere. */
    private const val INTERVAL_MS = 120_000L

    fun start(context: Context) {
        scheduleNext(context.applicationContext)
        Log.d(TAG, "Background alarm sync armed (every 25s)")
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, SyncAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(app, REQUEST_CODE, intent, flags)
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not schedule alarm: ${error.message}")
        }
    }
}
