package com.smartalarm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("alarm_id", -1L)
        val alarm = AlarmStore.load(c).firstOrNull { it.id == id && it.enabled } ?: return

        ContextCompat.startForegroundService(
            c,
            Intent(c, AlarmService::class.java).putExtra("alarm_id", id)
        )

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("alarm_full", "Smart Alarm", NotificationManager.IMPORTANCE_HIGH)
        )

        val full = PendingIntent.getActivity(
            c, id.toInt(),
            Intent(c, AlarmActivity::class.java).apply {
                putExtra("alarm_id", id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(c, "alarm_full")
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("🚨 Smart Alarm")
            .setContentText("Complete the wake-up tasks to dismiss the alarm.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .build()

        nm.notify(id.toInt(), n)
        AlarmScheduler.schedule(c, alarm)
    }
}
