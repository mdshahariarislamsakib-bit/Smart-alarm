package com.smartalarm.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.smartalarm.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val alarms = mutableListOf<AlarmData>()
    private var customUri: Uri? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let {
            customUri = it
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            b.customSoundName.text = "Custom: ${it.lastPathSegment ?: "audio"}"
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b)

        alarms.addAll(AlarmStore.load(this))

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= 31 &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } catch (_: Exception) {}
        }

        b.soundSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Extreme Siren", "Loud Digital Beep", "Cyberpunk Alarm")
        )

        b.customSound.setOnClickListener {
            picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/*"))
        }

        b.testAlarm.setOnClickListener {
            val id = System.currentTimeMillis()
            val a = AlarmData(
                id, 0, 0, true,
                b.soundSpinner.selectedItem.toString(),
                customUri?.toString(),
                b.mathToggle.isChecked,
                b.cameraToggle.isChecked,
                b.simonToggle.isChecked
            )
            AlarmStore.save(this, listOf(a))
            startForegroundService(
                Intent(this, AlarmService::class.java).putExtra("alarm_id", id)
            )
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("alarm_id", id))
        }

        b.addAlarm.setOnClickListener {
            val a = AlarmData(
                System.currentTimeMillis(),
                b.timePicker.hour,
                b.timePicker.minute,
                true,
                b.soundSpinner.selectedItem.toString(),
                customUri?.toString(),
                b.mathToggle.isChecked,
                b.cameraToggle.isChecked,
                b.simonToggle.isChecked
            )

            if (!a.math && !a.camera && !a.simon) {
                Toast.makeText(this, "Enable at least one verification task.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            alarms.add(a)
            AlarmStore.save(this, alarms)
            AlarmScheduler.schedule(this, a)
            render()
        }

        val handler = android.os.Handler(mainLooper)
        val clock = object : Runnable {
            override fun run() {
                val d = Date()
                b.clockText.text =
                    SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(d)
                b.dateText.text =
                    SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(d)
                handler.postDelayed(this, 500)
            }
        }
        handler.post(clock)

        render()
    }

    private fun render() {
        b.alarmList.removeAllViews()

        alarms.forEach { a ->
            val v = layoutInflater.inflate(R.layout.alarm_item, b.alarmList, false)
            v.findViewById<android.widget.TextView>(R.id.time).text =
                String.format(Locale.getDefault(), "%02d:%02d", a.hour, a.minute)

            v.findViewById<android.widget.TextView>(R.id.tasks).text =
                listOfNotNull(
                    if (a.math) "Math" else null,
                    if (a.camera) "Camera" else null,
                    if (a.simon) "Simon" else null
                ).joinToString(" • ")

            val sw = v.findViewById<android.widget.Switch>(R.id.enabled)
            sw.isChecked = a.enabled
            sw.setOnCheckedChangeListener { _, x ->
                a.enabled = x
                AlarmStore.save(this, alarms)
                if (x) AlarmScheduler.schedule(this, a)
                else AlarmScheduler.cancel(this, a)
            }

            v.findViewById<android.widget.ImageButton>(R.id.delete).setOnClickListener {
                AlarmScheduler.cancel(this, a)
                alarms.remove(a)
                AlarmStore.save(this, alarms)
                render()
            }

            b.alarmList.addView(v)
        }
    }
}
