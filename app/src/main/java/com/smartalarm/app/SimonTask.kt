package com.smartalarm.app

import android.os.Handler
import android.os.Looper
import android.view.View
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

    fun start() {
        buttons.forEach { b ->
            b.setOnClickListener { tap((it as Button).tag.toString().toInt()) }
        }
        play()
    }

    private fun play() {
        accepting = false
        position = 0
        sequence.clear()

        repeat(if (level == 1) 3 else 4) {
            sequence += Random().nextInt(4)
        }

        root.findViewById<TextView>(R.id.level).text =
            "Level $level • Watch"
        root.findViewById<TextView>(R.id.simonStatus).text = "WATCH…"

        sequence.forEachIndexed { i, n ->
            handler.postDelayed({
                flash(n, true)
                handler.postDelayed({ flash(n, false) }, 280)
            }, i * 650L + 450L)
        }

        handler.postDelayed({
            accepting = true
            root.findViewById<TextView>(R.id.simonStatus).text =
                "YOUR TURN — repeat exactly"
        }, sequence.size * 650L + 650L)
    }

    private fun tap(n: Int) {
        if (!accepting) return

        if (n != sequence[position]) {
            level = 1
            accepting = false
            root.findViewById<TextView>(R.id.simonStatus).text =
                "WRONG! New Level 1 sequence"
            handler.postDelayed({ play() }, 700L)
            return
        }

        flash(n, true)
        handler.postDelayed({ flash(n, false) }, 120L)

        position++

        if (position == sequence.size) {
            if (level == 1) {
                level = 2
                handler.postDelayed({ play() }, 800L)
            } else {
                done()
            }
        }
    }

    private fun flash(n: Int, on: Boolean) {
        buttons[n].alpha = if (on) 1f else .65f
    }
}
