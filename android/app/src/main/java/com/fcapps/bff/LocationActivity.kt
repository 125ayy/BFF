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
import com.fcapps.bff.Prefs.smsEnabled

/**
 * Settings › Location: two switches, "Location" and "Send SMS" (v3.11.1). Each asks only for its
 * own grants, one prompt at a time; tap and slide land in the same handler. Location shows the
 * Google Play prominent disclosure first, then location → background location. Anything short of
 * a full grant leaves that switch off with a reason. The switches never lie: they are re-read from
 * the live grants on every resume, and the SMS receiver re-checks them at command time. Replies
 * to "BFF where" happen only when both are on.
 */
class LocationActivity : BffActivity() {

    private enum class Walk { LOCATION, SMS }

    private var walk = Walk.LOCATION
    private lateinit var flow: PermissionFlow
    private var message: Int = 0
    /** The switch whose walk the user started, until it is on or turned off. */
    private var armed: Walk? = null
    /** Set while the code itself moves a switch, so the change listeners only see the user. */
    private var moving = false
    private val requestHistory by lazy { getSharedPreferences("permission_requests", MODE_PRIVATE) }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishStep() }
    private val settingsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishStep() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        walk = savedInstanceState?.getString("walk")?.let { runCatching { Walk.valueOf(it) }.getOrNull() } ?: Walk.LOCATION
        flow = PermissionFlow(Build.VERSION.SDK_INT, stepsOf(walk),
            savedInstanceState?.getString("step")?.let { runCatching { Step.valueOf(it) }.getOrNull() }?.takeIf { it in stepsOf(walk) } ?: Step.COMPLETE,
            savedInstanceState?.getBoolean("waiting", false) ?: false)
        message = savedInstanceState?.getInt("message", 0) ?: 0
        armed = savedInstanceState?.getString("armed")?.let { runCatching { Walk.valueOf(it) }.getOrNull() }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        // Checked-change listeners, not click listeners: sliding the thumb never produces a click.
        for (w in Walk.values()) switchOf(w).setOnCheckedChangeListener { _, checked -> if (!moving) onSwitchChanged(w, checked) }
        render()
        // Recreated (rotation, process death) while a Settings dialog was up: show it again.
        if (armed == walk && !flow.waiting && flow.step != Step.COMPLETE) launchCurrent()
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
        armed?.let { w -> if (!enabled(w) && granted(w)) { setEnabled(w, true); message = 0; armed = null } }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("walk", walk.name)
        outState.putString("step", flow.step.name)
        outState.putBoolean("waiting", flow.waiting)
        outState.putInt("message", message)
        outState.putString("armed", armed?.name)
        super.onSaveInstanceState(outState)
    }

    // ---- per-switch plumbing -------------------------------------------------------------

    private fun stepsOf(w: Walk) = if (w == Walk.LOCATION) PermissionFlow.LOCATION else PermissionFlow.SMS
    private fun switchOf(w: Walk) = findViewById<SwitchCompat>(if (w == Walk.LOCATION) R.id.swLocation else R.id.swSms)
    private fun stateOf(w: Walk) = findViewById<TextView>(if (w == Walk.LOCATION) R.id.tvLocationState else R.id.tvSmsState)
    private fun enabled(w: Walk) = if (w == Walk.LOCATION) locationEnabled else smsEnabled
    private fun setEnabled(w: Walk, value: Boolean) { if (w == Walk.LOCATION) locationEnabled = value else smsEnabled = value }
    /** Every relevant step of the walk is granted right now. */
    private fun granted(w: Walk) = stepsOf(w).none { it != Step.COMPLETE && flow.relevant(it) && !satisfied(it) }

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

    private fun setSwitch(w: Walk, checked: Boolean) {
        val sw = switchOf(w)
        if (sw.isChecked == checked) return
        moving = true
        try { sw.isChecked = checked } finally { moving = false }
    }

    private fun backgroundLabel(): CharSequence =
        if (Build.VERSION.SDK_INT >= 30) packageManager.backgroundPermissionOptionLabel else getString(R.string.location_all_the_time)

    /** Location granted while in use but not "all the time": say so under the switch. */
    private fun missingText(w: Walk): String? =
        if (w == Walk.LOCATION && satisfied(Step.LOCATION) && flow.relevant(Step.BACKGROUND_LOCATION) && !satisfied(Step.BACKGROUND_LOCATION))
            getString(R.string.location_missing_background, backgroundLabel()) else null

    private fun groupLabel(step: Step) = getString(if (step == Step.SEND_SMS) R.string.perm_group_sms else R.string.perm_group_location)

    /** The displayed state is derived, never trusted from the preferences alone. */
    private fun render() {
        for (w in Walk.values()) {
            val on = enabled(w) && granted(w)
            if (enabled(w) && !on) setEnabled(w, false)
            if (on && armed == w) armed = null
            setSwitch(w, on)
            switchOf(w).isEnabled = !flow.waiting
            stateOf(w).text = when {
                flow.waiting && walk == w -> getString(R.string.setup_waiting)
                on -> getString(R.string.location_state_on_short)
                else -> missingText(w) ?: getString(R.string.location_state_off)
            }
        }
        findViewById<TextView>(R.id.tvLocationMessage).apply {
            visibility = if (message != 0) View.VISIBLE else View.GONE
            if (message != 0) setText(message)
        }
    }

    /** Reached by a tap and by a slide alike. */
    private fun onSwitchChanged(w: Walk, checked: Boolean) {
        if (!checked) {                   // user turned it off
            setEnabled(w, false); if (armed == w) armed = null; message = 0; render(); return
        }
        setSwitch(w, false)               // stays off until every grant is in
        if (flow.waiting) return
        message = 0
        if (granted(w)) { setEnabled(w, true); render(); return }
        // Support sheet first: the Google Play prominent disclosure for location, a one-line objective for SMS.
        AlertDialog.Builder(this)
            .setTitle(if (w == Walk.LOCATION) R.string.location_switch_location else R.string.location_switch_sms)
            .setMessage(if (w == Walk.LOCATION) R.string.location_disclosure else R.string.location_sms_sheet)
            .setPositiveButton(R.string.setup_finish) { _, _ -> startWalk(w) }
            .setNegativeButton(R.string.location_not_now) { _, _ -> render() }
            .setOnCancelListener { render() }
            .show()
    }

    // ---- the walk -------------------------------------------------------------------------

    private fun startWalk(w: Walk) {
        walk = w
        armed = w
        flow = PermissionFlow(Build.VERSION.SDK_INT, stepsOf(w))
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
        val background = flow.step == Step.BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= 30
        if (background || needsSettings()) {
            // Android 11+ only grants "all the time" from the app's settings page, and a prompt
            // refused twice is gone for good: say where to go first. Not yet waiting: the dialog
            // is not a result we could lose.
            val step = flow.step
            render()
            AlertDialog.Builder(this)
                .setTitle(if (background) R.string.setup_background_title else R.string.location_settings)
                .setMessage(if (background) getString(R.string.setup_background_description, backgroundLabel())
                            else getString(R.string.location_open_settings_description, groupLabel(step)))
                .setPositiveButton(R.string.setup_open_settings) { _, _ -> if (flow.begin()) { render(); openSettings() } }
                .setNegativeButton(R.string.location_not_now) { _, _ -> abort(reasonFor(step)) }
                .setCancelable(false)
                .show()
            return
        }
        if (!flow.begin()) return
        render()
        try {
            val requested = permissions(flow.step)
            requestHistory.edit().apply { requested.forEach { putBoolean(it, true) } }.apply()
            permissionRequest.launch(requested)
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
        val ok = granted(walk)
        setEnabled(walk, ok)
        message = if (ok) 0 else reasonFor(stepsOf(walk).first { it != Step.COMPLETE && flow.relevant(it) && !satisfied(it) })
        render()
    }

    /** Leaves `armed` on: if the user finishes the grant in Settings and comes back, onResume turns the switch on. */
    private fun abort(reason: Int) {
        setEnabled(walk, false)
        message = reason
        flow.step = Step.COMPLETE
        render()
    }
}
