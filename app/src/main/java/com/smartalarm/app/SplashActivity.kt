package com.smartalarm.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.smartalarm.app.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var b: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Initial setup for entrance animation
        b.logoContainer.scaleX = 0.5f
        b.logoContainer.scaleY = 0.5f
        b.logoContainer.alpha = 0f

        b.splashTitle.alpha = 0f
        b.splashTitle.translationY = 30f

        b.splashSubtitle.alpha = 0f
        b.splashSubtitle.translationY = 20f

        // Animate Logo Pop
        b.logoContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        // Animate Glow Pulse Ring
        b.glowRing.animate()
            .scaleX(1.35f)
            .scaleY(1.35f)
            .alpha(0f)
            .setDuration(850)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate Title & Subtitle Fade-in
        b.splashTitle.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setStartDelay(250)
            .setDuration(500)
            .start()

        b.splashSubtitle.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setStartDelay(400)
            .setDuration(500)
            .start()

        // Smooth transition to MainActivity after 1100ms
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1100)
    }
}
