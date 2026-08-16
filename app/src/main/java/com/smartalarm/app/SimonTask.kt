package com.smartalarm.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import java.util.Random

class SimonTask(
    private val root: View,
    private val done: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val buttons = listOf(
        root.findViewById<Button>(R.id.red),
        root.findViewById<Button>(R.id.blue),
        root.findViewById<Button>(R.id.yellow),
        root.findViewById<Button>(R.id.green)
    )

    private val sequence = mutableListOf<Int>()
    private var position = 0
    private var level = 1
    private var accepting = false

    // Hardware-accelerated Tone Generator for instant zero-latency musical sound effects
    private var toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (_: Exception) {
        null
    }

    // Distinct DTMF tones for each color:
    // Red: High pitch, Blue: Mid-high, Yellow: Mid, Green: Deep bass
    private val tones = intArrayOf(
        ToneGenerator.TONE_DTMF_1,
        ToneGenerator.TONE_DTMF_4,
        ToneGenerator.TONE_DTMF_7,
        ToneGenerator.TONE_DTMF_0
    )

    fun start() {
        buttons.forEach { b ->
            b.setOnClickListener {
                val tag = (it as Button).tag.toString().toIntOrNull() ?: 0
                tap(tag)
            }
        }
        play()
    }

    private fun play() {
        accepting = false
        position = 0
        sequence.clear()

        val length = if (level == 1) 3 else 4
        repeat(length) {
            sequence += Random().nextInt(4)
        }

        root.findViewById<TextView>(R.id.level)?.text =
            "Level $level of 2 • Watch Sequence"
        root.findViewById<TextView>(R.id.simonStatus)?.text = "👀 WATCH THE PATTERN…"

        sequence.forEachIndexed { i, n ->
            handler.postDelayed({
                flash(n, true)
                playTone(n, 300)
                handler.postDelayed({ flash(n, false) }, 320)
            }, i * 650L + 450L)
        }

        handler.postDelayed({
            accepting = true
            root.findViewById<TextView>(R.id.simonStatus)?.text =
                "👉 YOUR TURN — Tap the exact sequence"
        }, sequence.size * 650L + 650L)
    }

    private fun tap(n: Int) {
        if (!accepting) return

        flash(n, true)
        playTone(n, 150)
        handler.postDelayed({ flash(n, false) }, 150)

        if (n != sequence[position]) {
            // Wrong tone buzz
            playTone(ToneGenerator.TONE_PROP_NACK, 350)
            level = 1
            accepting = false
            root.findViewById<TextView>(R.id.simonStatus)?.text =
                "❌ WRONG! Repeating Level 1"
            handler.postDelayed({ play() }, 850L)
            return
        }

        position++

        if (position == sequence.size) {
            if (level == 1) {
                // Success chime
                playTone(ToneGenerator.TONE_PROP_ACK, 200)
                level = 2
                root.findViewById<TextView>(R.id.simonStatus)?.text =
                    "✓ Great! Level 2 incoming…"
                handler.postDelayed({ play() }, 900L)
            } else {
                playTone(ToneGenerator.TONE_PROP_ACK, 400)
                root.findViewById<TextView>(R.id.simonStatus)?.text =
                    "🎉 Simon Sequence COMPLETE!"
                toneGen?.release()
                toneGen = null
                handler.postDelayed({ done() }, 400L)
            }
        }
    }

    private fun flash(n: Int, on: Boolean) {
        val b = buttons.getOrNull(n) ?: return
        if (on) {
            b.animate()
                .scaleX(1.10f)
                .scaleY(1.10f)
                .alpha(1.0f)
                .setDuration(120)
                .setInterpolator(OvershootInterpolator())
                .start()
        } else {
            b.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(0.85f)
                .setDuration(150)
                .start()
        }
    }

    private fun playTone(toneIndexOrType: Int, durationMs: Int) {
        try {
            val tone = if (toneIndexOrType in 0..3) tones[toneIndexOrType] else toneIndexOrType
            toneGen?.startTone(tone, durationMs)
        } catch (_: Exception) {}
    }
}
