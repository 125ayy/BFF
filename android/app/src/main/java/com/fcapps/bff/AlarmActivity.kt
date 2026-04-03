package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm)

        val btnFound = findViewById<Button>(R.id.btnFound)
        btnFound.setOnClickListener {
            stopAlarm()
            finish()
        }
    }

    private fun stopAlarm() {
        val intent = Intent(this, AlarmService::class.java)
        intent.action = AlarmService.ACTION_STOP
        startService(intent)
    }

    override fun onBackPressed() {
        // Prevent back button dismissing alarm
    }
}
