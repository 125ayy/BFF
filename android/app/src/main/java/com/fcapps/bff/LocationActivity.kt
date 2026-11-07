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
 * Settings › Location: one switch. Turning it on (tap or slide, both land in the same handler)
 * shows the Google Play prominent disclosure, then walks SEND_SMS → location → background
 * location, one prompt at a time. Anything short of a full grant leaves the switch off with a
 * reason. The switch never lies: it is re-read from the live grants on every resume, and the
 * SMS receiver re-checks them at command time.
 */
class LocationActivity : BffActivity() {

    private lateinit var flow: PermissionFlow
    private var message: Int = 0
    /** True from the moment the user accepted the disclosure until the switch is on or turned off. */
    private var armed: Boolean = false
    /** Set while the code itself moves the switch, so the change listener only sees the user. */
    private var moving = false
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
        armed = savedInstanceState?.getBoolean("armed", false) ?: false
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        // A checked-change listener, not a click listener: sliding the thumb never produces a click.
        findViewById<SwitchCompat>(R.id.swLocation).setOnCheckedChangeListener { _, checked -> if (!moving) onSwitchChanged(checked) }
        render()
        // Recreated (rotation, process death) while the background-location dialog was up: show it again.
        if (armed && !flow.waiting && flow.step != Step.COMPLETE) launchCurrent()
    }

    override fun onResume() {
        super.onResume()
        if (!::flow.isInitialized) return
        if (flow.waiting) {
            // A result that never arrived but the grant did (made from Settings): finish the step.
            if (satisfied(flow.step)) finishStep()
            return
        }
        // The walk gave up before the user finished in Settings; the grants are in now.
        if (armed && !locationEnabled && LocationPermissions.canReply(this)) { locationEnabled = true; message = 0; armed = false }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("step", flow.step.name)
        outState.putBoolean("waiting", flow.waiting)
        outState.putInt("message", message)
        outState.putBoolean("armed", armed)
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

    private fun setSwitch(checked: Boolean) {
        val sw = findViewById<SwitchCompat>(R.id.swLocation)
        if (sw.isChecked == checked) return
        moving = true
        try { sw.isChecked = checked } finally { moving = false }
    }

    /** The displayed state is derived, never trusted from the preference alone. */
    private fun render() {
        val on = locationEnabled && LocationPermissions.canReply(this)
        if (locationEnabled && !on) locationEnabled = false
        if (on) armed = false
        setSwitch(on)
        findViewById<SwitchCompat>(R.id.swLocation).isEnabled = !flow.waiting
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

    /** Reached by a tap and by a slide alike. */
    private fun onSwitchChanged(checked: Boolean) {
        if (!checked) {                   // user turned it off
            locationEnabled = false; armed = false; message = 0; render(); return
        }
        setSwitch(false)                  // stays off until every grant is in
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
        armed = true
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
        return requestHistory.getBoolean(p, false) && !ActivityCompat.shouldShowRequestPermissionRationale(this, p) && !satisfied(flow.step)
    }

    private fun launchCurrent() {
        if (flow.step == Step.COMPLETE) { complete(); return }
        if (flow.waiting) return
        if (flow.step == Step.BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= 30) {
            // Android 11+ only grants "all the time" from the app's settings page: say so first.
            // Not yet waiting: the dialog is not a result we could lose.
            render()
            AlertDialog.Builder(this)
                .setTitle(R.string.setup_background_title)
                .setMessage(getString(R.string.setup_background_description, packageManager.backgroundPermissionOptionLabel))
                .setPositiveButton(R.string.setup_open_settings) { _, _ -> if (flow.begin()) { render(); openSettings() } }
                .setNegativeButton(R.string.location_not_now) { _, _ -> abort(R.string.location_needs_background) }
                .setCancelable(false)
                .show()
            return
        }
        if (!flow.begin()) return
        render()
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

    /** Leaves `armed` on: if the user finishes the grant in Settings and comes back, onResume turns the switch on. */
    private fun abort(reason: Int) {
        locationEnabled = false
        message = reason
        flow.step = Step.COMPLETE
        render()
    }
}
