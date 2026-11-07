package com.fcapps.bff

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.finderMessage

class AlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_SENDER_NUMBER = "sender_number"
    }

    private var shakeAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and wake the display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm)

        // Show who triggered
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        val senderNumber = intent.getStringExtra(EXTRA_SENDER_NUMBER) ?: ""
        val triggeredBy = when {
            senderName.isNotEmpty() -> senderName
            senderNumber.isNotEmpty() -> senderNumber
            else -> "Unknown"
        }
        val msg = finderMessage.trim()
        findViewById<TextView>(R.id.tvTriggeredBy).text = if (msg.isNotEmpty()) msg else "Triggered by $triggeredBy"

        // The receiver may not have been allowed to start the siren from the background;
        // now that a screen is visible, make sure it runs.
        if (!AlarmService.running) {
            startForegroundService(Intent(this, AlarmService::class.java).apply {
                putExtra(AlarmService.EXTRA_SENDER_NAME, senderName)
                putExtra(AlarmService.EXTRA_SENDER_NUMBER, senderNumber)
            })
        }

        // Bell shake animation — translate left/right repeatedly
        val bellView = findViewById<View>(R.id.ivBell)
        shakeAnimator = ObjectAnimator.ofFloat(bellView, "translationX", -20f, 20f).apply {
            duration = 400
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        val btnFound = findViewById<Button>(R.id.btnFound)
        btnFound.setOnClickListener {
            stopAlarm()
            startActivity(Intent(this, FoundActivity::class.java))
            finish()
        }
    }

    private fun stopAlarm() {
        val intent = Intent(this, AlarmService::class.java)
        intent.action = AlarmService.ACTION_STOP
        startService(intent)
    }

    override fun onDestroy() {
        shakeAnimator?.cancel()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Prevent back button dismissing alarm
    }
}
