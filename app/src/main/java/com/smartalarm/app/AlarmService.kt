package com.smartalarm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "alarm_service"
        val channel = NotificationChannel(
            channelId,
            "Smart Alarm Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Active alarm ringing channel"
            setSound(null, null)
        }

        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Smart Alarm Ringing")
            .setContentText("Complete the wake-up verification tasks.")
            .setSmallIcon(R.drawable.ic_alarm)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                500,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
        } else {
            startForeground(500, notification)
        }
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val id = i?.getLongExtra("alarm_id", -1L) ?: -1L
        val a = AlarmStore.load(this).firstOrNull { it.id == id }
        play(a)
        return START_STICKY
    }

    private fun play(a: AlarmData?) {
        player?.release()
        player = null

        val uri = if (!a?.customUri.isNullOrBlank()) {
            try {
                Uri.parse(a?.customUri)
            } catch (_: Exception) {
                Uri.parse("android.resource://$packageName/${R.raw.extreme_siren}")
            }
        } else {
            when (a?.sound) {
                "Loud Digital Beep" -> Uri.parse("android.resource://$packageName/${R.raw.digital_beep}")
                "Cyberpunk Alarm" -> Uri.parse("android.resource://$packageName/${R.raw.cyberpunk_alarm}")
                else -> Uri.parse("android.resource://$packageName/${R.raw.extreme_siren}")
            }
        }

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                setVolume(1f, 1f)
                start()
            }
        } catch (_: Exception) {
            try {
                player = MediaPlayer.create(
                    this,
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                )?.apply {
                    isLooping = true
                    setVolume(1f, 1f)
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
