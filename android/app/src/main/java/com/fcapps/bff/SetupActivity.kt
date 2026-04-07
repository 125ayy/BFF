package com.fcapps.bff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            hideError()
            requestStandardPermissions()
        }
    }

    // ── Indicators ────────────────────────────────────────────────────────────

    private fun updatePermissionIndicators() {
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        setIndicator(R.id.imgSmsStatus, smsGranted)

        val contactsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        setIndicator(R.id.imgContactsStatus, contactsGranted)

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        setIndicator(R.id.imgNotifStatus, notifGranted)

        // MODIFY_AUDIO_SETTINGS is a normal permission — always granted at install
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

    // ── Error banner ──────────────────────────────────────────────────────────

    private fun showError(message: String) {
        val v = findViewById<TextView>(R.id.tvPermissionError)
        v.text = message
        v.visibility = View.VISIBLE
    }

    private fun hideError() {
        findViewById<TextView>(R.id.tvPermissionError).visibility = View.GONE
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
            Toast.makeText(
                this,
                "Please allow 'Display over other apps' — this lets the alarm appear on your lock screen",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }
        requestFullScreenIntentPermission()
    }

    private fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                Toast.makeText(
                    this,
                    "Please allow 'Full-screen notifications' so the alarm opens on your lock screen",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName")
                )
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_FULL_SCREEN)
                return  // wait for user to return from Settings before proceeding
            }
        }
        navigateToReady()
    }

    private fun navigateToReady() {
        updatePermissionIndicators()
        startActivity(Intent(this, ReadyActivity::class.java))
        finish()
    }

    // ── Results ───────────────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_STANDARD) return

        updatePermissionIndicators()

        // SMS is required — block progress if denied
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!smsGranted) {
            showError(
                "⚠  SMS permission is required. Without it the alarm cannot be triggered.\n\n" +
                "Tap GRANT PERMISSIONS again and allow SMS access."
            )
            return
        }

        hideError()
        requestOverlayPermission()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        updatePermissionIndicators()
        when (requestCode) {
            REQ_OVERLAY -> requestFullScreenIntentPermission()
            REQ_FULL_SCREEN -> navigateToReady()
        }
    }
}
