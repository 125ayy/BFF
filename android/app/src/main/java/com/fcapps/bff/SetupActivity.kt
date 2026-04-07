package com.fcapps.bff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fcapps.bff.Prefs.setupDone

class SetupActivity : AppCompatActivity() {

    companion object {
        private const val REQ_STANDARD = 100
        private const val REQ_OVERLAY = 200
        private const val REQ_FULL_SCREEN = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        updatePermissionIndicators()

        // "Continue" triggers permission requests then proceeds to welcome screen
        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            requestStandardPermissions()
        }

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            setupDone = true
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
        }
    }

    private fun updatePermissionIndicators() {
        // SMS
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        setIndicator(R.id.imgSmsStatus, smsGranted)

        // Contacts
        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        setIndicator(R.id.imgContactsStatus, contactsGranted)

        // Notifications (only relevant on Android 13+)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        setIndicator(R.id.imgNotifStatus, notifGranted)

        // Audio Control — MODIFY_AUDIO_SETTINGS is a normal permission, always granted
        setIndicator(R.id.imgAudioStatus, true)
    }

    private fun setIndicator(viewId: Int, granted: Boolean) {
        val view = findViewById<ImageView>(viewId) ?: return
        if (granted) {
            view.setImageResource(R.drawable.ic_check_circle)
            view.setColorFilter(0xFF00C853.toInt())
        } else {
            view.setImageResource(R.drawable.ic_circle_empty)
            view.setColorFilter(0xFF666666.toInt())
        }
    }

    // ── Permission chain ──────────────────────────────────────────────────────

    private fun requestStandardPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_STANDARD)
        } else {
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' for the alarm screen to work", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_OVERLAY)
        } else {
            requestFullScreenIntentPermission()
        }
    }

    private fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                Toast.makeText(this, "Please allow 'Full-screen notifications' so the alarm opens on your lock screen", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_FULL_SCREEN)
            }
        }
        updatePermissionIndicators()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STANDARD) {
            updatePermissionIndicators()
            requestOverlayPermission()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> requestFullScreenIntentPermission()
        }
        updatePermissionIndicators()
    }
}
