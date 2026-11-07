package com.fcapps.bff

import android.Manifest
import android.app.AlertDialog
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
import com.fcapps.bff.Prefs.locationEnabled

/**
 * Permissions, as a staged chain so every Google Play sensitive permission is requested
 * right after an in-app explanation:
 *   1. standard runtime set (SMS receive, send, contacts, notifications)
 *   2. location — only if the owner leaves the GPS row on. Foreground first, then a
 *      prominent disclosure dialog, then background ("Allow all the time").
 *   3. "display over other apps" + full-screen-intent so the alarm can open on the lock screen
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        private const val REQ_STANDARD = 100
        private const val REQ_LOCATION_FG = 101
        private const val REQ_LOCATION_BG = 102
        private const val REQ_OVERLAY = 200
        private const val REQ_FULL_SCREEN = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        updateIndicators()

        findViewById<View>(R.id.rowGps).setOnClickListener {
            locationEnabled = !locationEnabled
            Toast.makeText(this, if (locationEnabled) "GPS on" else "GPS off", Toast.LENGTH_SHORT).show()
            updateIndicators()
        }
        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            hideError(); requestStandard()
        }
    }

    // ── indicators ─────────────────────────────────────────────
    private fun updateIndicators() {
        setDot(R.id.imgSmsStatus, granted(Manifest.permission.RECEIVE_SMS))
        setDot(R.id.imgContactsStatus, granted(Manifest.permission.READ_CONTACTS))
        setDot(R.id.imgNotifStatus, if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.POST_NOTIFICATIONS) else true)
        setDot(R.id.imgAudioStatus, true)
        setDot(R.id.imgGpsStatus, locationEnabled && granted(Manifest.permission.ACCESS_FINE_LOCATION))
        findViewById<TextView>(R.id.tvGpsTitle)?.alpha = if (locationEnabled) 1f else 0.4f
    }

    private fun setDot(id: Int, on: Boolean) {
        findViewById<ImageView>(id)?.apply {
            setImageResource(if (on) R.drawable.ic_check_circle else R.drawable.ic_circle_empty)
            setColorFilter(if (on) 0xFF00C853.toInt() else 0xFF666666.toInt())
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun showError(m: String) { findViewById<TextView>(R.id.tvPermissionError).apply { text = m; visibility = View.VISIBLE } }
    private fun hideError() { findViewById<TextView>(R.id.tvPermissionError).visibility = View.GONE }

    // ── step 1: standard ───────────────────────────────────────
    private fun requestStandard() {
        val want = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) want.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = want.filterNot { granted(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_STANDARD)
        else afterStandard()
    }

    private fun afterStandard() {
        if (!granted(Manifest.permission.RECEIVE_SMS)) {
            showError("Without SMS, BFF cannot work. Tap ALLOW again and grant SMS."); return
        }
        hideError()
        if (locationEnabled) requestLocationForeground() else requestOverlay()
    }

    // ── step 2: location ───────────────────────────────────────
    private fun requestLocationForeground() {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) { requestLocationBackground(); return }
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION_FG)
    }

    private fun requestLocationBackground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { requestOverlay(); return }
        if (granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) { requestOverlay(); return }
        // Google Play prominent disclosure, shown before the background prompt.
        AlertDialog.Builder(this)
            .setTitle("Send location")
            .setMessage(getString(R.string.location_disclosure) + "\n\nOn the next screen choose \"Allow all the time\".")
            .setPositiveButton("Continue") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_LOCATION_BG)
            }
            .setNegativeButton("Skip") { _, _ -> locationEnabled = false; updateIndicators(); requestOverlay() }
            .setCancelable(false)
            .show()
    }

    // ── step 3: overlay + full screen ──────────────────────────
    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" so the alarm shows on the lock screen", Toast.LENGTH_LONG).show()
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), REQ_OVERLAY)
            return
        }
        requestFullScreen()
    }

    private fun requestFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                Toast.makeText(this, "Allow \"Full-screen notifications\" so the alarm opens on the lock screen", Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")), REQ_FULL_SCREEN)
                return
            }
        }
        offerBatteryExemption()
    }

    private fun offerBatteryExemption() {
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) { goReady(); return }
        AlertDialog.Builder(this)
            .setTitle("Keep BFF ready")
            .setMessage("Let BFF ignore battery optimisation so it can scream the moment your text arrives, even after days idle.")
            .setPositiveButton("Allow") { _, _ ->
                try {
                    @Suppress("BatteryLife")
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {}
                goReady()
            }
            .setNegativeButton("Later") { _, _ -> goReady() }
            .show()
    }

    private fun goReady() {
        startActivity(Intent(this, ReadyActivity::class.java)); finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateIndicators()
        when (requestCode) {
            REQ_STANDARD -> afterStandard()
            REQ_LOCATION_FG -> if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) requestLocationBackground() else { locationEnabled = false; updateIndicators(); requestOverlay() }
            REQ_LOCATION_BG -> requestOverlay()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        updateIndicators()
        when (requestCode) {
            REQ_OVERLAY -> requestFullScreen()
            REQ_FULL_SCREEN -> offerBatteryExemption()
        }
    }
}
