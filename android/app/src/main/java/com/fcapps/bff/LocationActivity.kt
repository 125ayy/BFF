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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fcapps.bff.PermissionFlow.Step
import com.fcapps.bff.Prefs.locationEnabled

/**
 * Settings › Location: one switch. Turning it on shows the Google Play prominent disclosure,
 * then walks SEND_SMS → location → background location, one prompt at a time. Anything short
 * of a full grant leaves the switch off with a reason. The switch never lies: it is re-read
 * from the live grants on every resume, and the SMS receiver re-checks them at command time.
 */
class LocationActivity : BffActivity() {

    private lateinit var flow: PermissionFlow
    private var message: Int = 0
    private val requestHistory by lazy { getSharedPreferences("permission_requests", MODE_PRIVATE) }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishStep() }
    private val settingsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishStep() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        flow = PermissionFlow(Build.VERSION.SDK_INT, PermissionFlow.LOCATION,
            savedInstanceState?.getString("step")?.let { runCatching { Step.valueOf(it) }.getOrNull() }?.takeIf { it in PermissionFlow.LOCATION } ?: Step.COMPLETE,
            savedInstanceState?.getBoolean("waiting", false) ?: false)
        message = savedInstanceState?.getInt("message", 0) ?: 0
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<SwitchCompat>(R.id.swLocation).setOnClickListener { onSwitchTapped() }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::flow.isInitialized && !flow.waiting) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("step", flow.step.name)
        outState.putBoolean("waiting", flow.waiting)
        outState.putInt("message", message)
        super.onSaveInstanceState(outState)
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun permissions(step: Step): Array<String> = when (step) {
        Step.SEND_SMS -> arrayOf(Manifest.permission.SEND_SMS)
        // Android 12+ needs both to offer the precise/approximate choice.
        Step.LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        Step.BACKGROUND_LOCATION -> arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else -> emptyArray()
    }

    private fun satisfied(step: Step): Boolean = when (step) {
        Step.LOCATION -> granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
        Step.COMPLETE -> false
        else -> permissions(step).all { granted(it) }
    }

    /** The displayed state is derived, never trusted from the preference alone. */
    private fun render() {
        val on = locationEnabled && LocationPermissions.canReply(this)
        if (locationEnabled && !on) locationEnabled = false
        val sw = findViewById<SwitchCompat>(R.id.swLocation)
        sw.isChecked = on
        sw.isEnabled = !flow.waiting
        findViewById<TextView>(R.id.tvLocationState).setText(when {
            flow.waiting -> R.string.setup_waiting
            on -> R.string.location_state_on
            else -> R.string.location_state_off
        })
        findViewById<TextView>(R.id.tvLocationMessage).apply {
            visibility = if (message != 0 && !on) View.VISIBLE else View.GONE
            if (message != 0) setText(message)
        }
    }

    private fun onSwitchTapped() {
        val sw = findViewById<SwitchCompat>(R.id.swLocation)
        if (!sw.isChecked) {              // user turned it off
            locationEnabled = false; message = 0; render(); return
        }
        sw.isChecked = false              // stays off until every grant is in
        if (flow.waiting) return
        message = 0
        if (LocationPermissions.canReply(this)) { locationEnabled = true; render(); return }
        // Google Play prominent disclosure, before the first location prompt.
        AlertDialog.Builder(this)
            .setTitle(R.string.location_settings)
            .setMessage(R.string.location_disclosure)
            .setPositiveButton(R.string.setup_finish) { _, _ -> startWalk() }
            .setNegativeButton(R.string.location_not_now) { _, _ -> render() }
            .setOnCancelListener { render() }
            .show()
    }

    private fun startWalk() {
        flow.step = PermissionFlow.LOCATION.first()
        flow.waiting = false
        skipSatisfied()
        launchCurrent()
    }

    private fun skipSatisfied() {
        while (flow.step != Step.COMPLETE && (!flow.relevant(flow.step) || satisfied(flow.step))) flow.advance()
    }

    private fun needsSettings(): Boolean {
        val p = permissions(flow.step).firstOrNull() ?: return false
        if (flow.step == Step.BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= 30) return true
        return requestHistory.getBoolean(p, false) && !ActivityCompat.shouldShowRequestPermissionRationale(this, p) && !satisfied(flow.step)
    }

    private fun launchCurrent() {
        if (flow.step == Step.COMPLETE) { complete(); return }
        if (!flow.begin()) return
        render()
        if (flow.step == Step.BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= 30) {
            // Android 11+ only grants "all the time" from the app's settings page: say so first.
            AlertDialog.Builder(this)
                .setTitle(R.string.setup_background_title)
                .setMessage(getString(R.string.setup_background_description, packageManager.backgroundPermissionOptionLabel))
                .setPositiveButton(R.string.setup_open_settings) { _, _ -> openSettings() }
                .setNegativeButton(R.string.location_not_now) { _, _ -> flow.result(false); abort(R.string.location_needs_background) }
                .setCancelable(false)
                .show()
            return
        }
        try {
            if (needsSettings()) openSettings()
            else {
                val requested = permissions(flow.step)
                requestHistory.edit().apply { requested.forEach { putBoolean(it, true) } }.apply()
                permissionRequest.launch(requested)
            }
        } catch (_: RuntimeException) {
            flow.result(false); abort(reasonFor(flow.step))
        }
    }

    private fun openSettings() {
        try { settingsRequest.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: RuntimeException) { flow.result(false); abort(reasonFor(flow.step)) }
    }

    private fun finishStep() {
        if (!::flow.isInitialized || !flow.waiting) return
        val ok = satisfied(flow.step)
        val step = flow.step
        flow.result(ok)
        if (!ok) { abort(reasonFor(step)); return }
        skipSatisfied()
        launchCurrent()
    }

    private fun reasonFor(step: Step) = when (step) {
        Step.SEND_SMS -> R.string.location_needs_sms
        Step.BACKGROUND_LOCATION -> R.string.location_needs_background
        else -> R.string.location_needs_location
    }

    private fun complete() {
        locationEnabled = LocationPermissions.canReply(this)
        message = if (locationEnabled) 0 else R.string.location_needs_background
        render()
    }

    private fun abort(reason: Int) {
        locationEnabled = false
        message = reason
        flow.step = Step.COMPLETE
        render()
    }
}
