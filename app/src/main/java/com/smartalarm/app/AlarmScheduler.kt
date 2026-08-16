package com.smartalarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    private fun pending(c: Context, id: Long, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, id.toInt(),
            Intent(c, AlarmReceiver::class.java).putExtra("alarm_id", id),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(c: Context, a: AlarmData) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val pi = pending(c, a.id, PendingIntent.FLAG_UPDATE_CURRENT)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun cancel(c: Context, a: AlarmData) {
        val p = pending(c, a.id, PendingIntent.FLAG_NO_CREATE)
        if (p != null) {
            c.getSystemService(AlarmManager::class.java)?.cancel(p)
            p.cancel()
        }
    }

    fun rescheduleAll(c: Context) {
        AlarmStore.load(c).filter { it.enabled }.forEach { schedule(c, it) }
    }
}
