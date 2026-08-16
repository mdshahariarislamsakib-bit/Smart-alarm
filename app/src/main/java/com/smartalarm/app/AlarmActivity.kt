package com.smartalarm.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.smartalarm.app.databinding.ActivityAlarmBinding
import java.util.Random

class AlarmActivity : AppCompatActivity() {
    private lateinit var b: ActivityAlarmBinding
    private lateinit var alarm: AlarmData
    private val tasks = mutableListOf<Int>()
    private var index = 0
    private var mathCorrect = 0
    private var currentCameraTask: CameraTask? = null

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showCamera()
            } else {
                Toast.makeText(this, "Camera permission is required for this task.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        b = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Block back button intentionally to prevent escaping alarm
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally blocked
            }
        })

        val id = intent.getLongExtra("alarm_id", -1L)
        alarm = AlarmStore.load(this).firstOrNull { it.id == id }
            ?: AlarmData(id, 0, 0, true)

        getSystemService(AudioManager::class.java)?.let {
            try {
                it.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    it.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
                )
            } catch (_: Exception) {}
        }

        if (alarm.math) tasks += 1
        if (alarm.camera) tasks += 2
        if (alarm.simon) tasks += 3

        if (tasks.isEmpty()) {
            stopAlarm()
            return
        }

        startPulse()
        render()
    }

    private fun startPulse() {
        val h = Handler(Looper.getMainLooper())
        var on = false
        h.post(object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                on = !on
                b.redPulse.alpha = if (on) .18f else .03f
                h.postDelayed(this, 450)
            }
        })
    }

    private fun render() {
        currentCameraTask?.stop()
        currentCameraTask = null
        b.taskContainer.removeAllViews()
        b.stepText.text = "Step ${index + 1} of ${tasks.size}"
        b.overallProgress.progress = index * 100 / tasks.size

        when (tasks[index]) {
            1 -> showMath()
            2 -> {
                if (checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    showCamera()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            3 -> showSimon()
        }
    }

    private fun next() {
        index++
        if (index >= tasks.size) stopAlarm() else render()
    }

    private fun showMath() {
        val v = layoutInflater.inflate(R.layout.task_math, b.taskContainer, false)
        b.taskContainer.addView(v)

        val q = v.findViewById<TextView>(R.id.question)
        val ans = v.findViewById<EditText>(R.id.answer)
        val count = v.findViewById<TextView>(R.id.mathCount)
        val fb = v.findViewById<TextView>(R.id.feedback)

        fun make(): Int {
            val r = Random()
            return when (r.nextInt(4)) {
                0 -> {
                    val a = r.nextInt(30) + 10
                    val c = r.nextInt(30) + 5
                    q.text = "$a + $c = ?"
                    a + c
                }
                1 -> {
                    val a = r.nextInt(50) + 30
                    val c = r.nextInt(25) + 5
                    q.text = "$a − $c = ?"
                    a - c
                }
                2 -> {
                    val a = r.nextInt(9) + 4
                    val c = r.nextInt(8) + 2
                    q.text = "$a × $c = ?"
                    a * c
                }
                else -> {
                    val c = r.nextInt(7) + 2
                    val a = c * (r.nextInt(8) + 2)
                    q.text = "$a ÷ $c = ?"
                    a / c
                }
            }
        }

        var expected = make()

        v.findViewById<Button>(R.id.submit).setOnClickListener {
            if (ans.text.toString().toIntOrNull() == expected) {
                mathCorrect++
                count.text = "Correct: $mathCorrect / 3"
                fb.text = "✓ Correct"
                ans.text.clear()
                if (mathCorrect == 3) next() else expected = make()
            } else {
                fb.text = "✕ Wrong — new question"
                ans.text.clear()
                expected = make()
            }
        }
    }

    private fun showCamera() {
        val v = layoutInflater.inflate(R.layout.task_camera, b.taskContainer, false)
        b.taskContainer.addView(v)
        val task = CameraTask(this, v) { next() }
        currentCameraTask = task
        task.start()
    }

    private fun showSimon() {
        val v = layoutInflater.inflate(R.layout.task_simon, b.taskContainer, false)
        b.taskContainer.addView(v)
        SimonTask(v) { next() }.start()
    }

    private fun stopAlarm() {
        currentCameraTask?.stop()
        currentCameraTask = null
        stopService(Intent(this, AlarmService::class.java))
        getSystemService(android.app.NotificationManager::class.java)?.cancelAll()
        b.overallProgress.progress = 100
        Toast.makeText(this, "Alarm dismissed — you are awake!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        currentCameraTask?.stop()
        currentCameraTask = null
        super.onDestroy()
    }
}
