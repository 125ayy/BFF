package com.fcapps.bff

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fcapps.bff.PermissionFlow.Step
import com.fcapps.bff.Prefs.permissionSetupVersion

/** One visible explanation and one system request at a time, including Settings returns. */
class SetupActivity : BffActivity() {
    private lateinit var flow: PermissionFlow
    private var denied = false
    private val requestHistory by lazy { getSharedPreferences("permission_requests", MODE_PRIVATE) }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishRequest() }
    private val settingsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishRequest() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        flow = PermissionFlow(Build.VERSION.SDK_INT, PermissionFlow.SETUP,
            savedInstanceState?.getString("step")?.let { runCatching { Step.valueOf(it) }.getOrNull() }?.takeIf { it in PermissionFlow.SETUP } ?: Step.RECEIVE_SMS,
            savedInstanceState?.getBoolean("waiting", false) ?: false)
        denied = savedInstanceState?.getBoolean("denied", false) ?: false
        findViewById<Button>(R.id.btnContinue).setOnClickListener { allowCurrent() }
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            if (flow.skip()) { denied = false; render() }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::flow.isInitialized && !flow.waiting) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("step", flow.step.name)
        outState.putBoolean("waiting", flow.waiting)
        outState.putBoolean("denied", denied)
        super.onSaveInstanceState(outState)
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun permissions(step: Step): Array<String> = when (step) {
        Step.RECEIVE_SMS -> arrayOf(Manifest.permission.RECEIVE_SMS)
        Step.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        else -> emptyArray()
    }

    private fun satisfied(step: Step): Boolean = when (step) {
        Step.NOTIFICATIONS -> NotificationManagerCompat.from(this).areNotificationsEnabled()
        Step.OVERLAY -> Settings.canDrawOverlays(this)
        Step.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Step.BATTERY -> getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        Step.COMPLETE -> false
        else -> permissions(step).all { granted(it) }
    }

    private fun render() {
        // Do not advance while a launcher is awaiting its result (including after rotation).
        if (!flow.waiting) {
            while (flow.step != Step.COMPLETE && (!flow.relevant(flow.step) || satisfied(flow.step))) {
                flow.advance()
                denied = false
            }
        }
        val (title, description) = when (flow.step) {
            Step.RECEIVE_SMS -> R.string.setup_sms_title to R.string.setup_sms_description
            Step.CONTACTS -> R.string.setup_contacts_title to R.string.setup_contacts_description
            Step.NOTIFICATIONS -> R.string.setup_notifications_title to R.string.setup_notifications_description
            Step.OVERLAY -> R.string.setup_overlay_title to R.string.setup_overlay_description
            Step.FULL_SCREEN -> R.string.setup_fullscreen_title to R.string.setup_fullscreen_description
            Step.BATTERY -> R.string.setup_battery_title to R.string.setup_battery_description
            Step.COMPLETE -> R.string.setup_complete_title to R.string.setup_complete_description
            else -> R.string.setup_complete_title to R.string.setup_complete_description   // location steps never run here
        }
        findViewById<TextView>(R.id.tvSetupProgress).text = getString(R.string.setup_progress, PermissionFlow.SETUP.indexOf(flow.step) + 1, PermissionFlow.SETUP.size)
        findViewById<TextView>(R.id.tvSetupTitle).setText(title)
        findViewById<TextView>(R.id.tvSetupDescription).text = when (flow.step) {
            Step.COMPLETE -> getString(description) + "\n\n" + capabilitySummary()
            else -> getString(description)
        }
        findViewById<TextView>(R.id.tvPermissionError).apply {
            visibility = if (denied) View.VISIBLE else View.GONE
            setText(if (flow.step == Step.RECEIVE_SMS) R.string.setup_required_denied else R.string.setup_optional_denied)
        }
        findViewById<Button>(R.id.btnContinue).apply {
            isEnabled = !flow.waiting
            setText(when {
                flow.waiting -> R.string.setup_waiting
                flow.step == Step.COMPLETE -> R.string.setup_finish
                needsSettings() -> R.string.setup_open_settings
                denied -> R.string.setup_retry
                else -> R.string.setup_allow
            })
        }
        findViewById<Button>(R.id.btnSkip).apply {
            visibility = if (flow.step in listOf(Step.RECEIVE_SMS, Step.COMPLETE)) View.GONE else View.VISIBLE
            isEnabled = !flow.waiting
        }
    }

    private fun needsSettings(): Boolean {
        val p = permissions(flow.step).firstOrNull() ?: return false
        if (flow.step == Step.NOTIFICATIONS && granted(p) && !satisfied(flow.step)) return true
        return requestHistory.getBoolean(p, false) && !ActivityCompat.shouldShowRequestPermissionRationale(this, p) && !satisfied(flow.step)
    }

    private fun allowCurrent() {
        if (flow.waiting) return
        if (flow.step == Step.COMPLETE) {
            // A grant may have been revoked while this summary was visible.
            if (!granted(Manifest.permission.RECEIVE_SMS)) {
                flow.step = Step.RECEIVE_SMS; denied = true; render(); return
            }
            permissionSetupVersion = Prefs.SETUP_VERSION
            startActivity(Intent(this, ReadyActivity::class.java))
            finish()
            return
        }
        if (!flow.begin()) return
        denied = false
        val settings = when {
            needsSettings() -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            flow.step == Step.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            flow.step == Step.FULL_SCREEN && Build.VERSION.SDK_INT >= 34 -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            flow.step == Step.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            else -> null
        }
        render()
        try {
            if (settings != null) settingsRequest.launch(settings)
            else {
                val requested = permissions(flow.step)
                requestHistory.edit().apply { requested.forEach { putBoolean(it, true) } }.apply()
                permissionRequest.launch(requested)
            }
        } catch (_: android.content.ActivityNotFoundException) {
            flow.result(false); denied = true; render()
        } catch (_: SecurityException) {
            flow.result(false); denied = true; render()
        }
    }

    private fun finishRequest() {
        if (!::flow.isInitialized || !flow.waiting) return
        val ok = satisfied(flow.step)
        flow.result(ok)
        denied = !ok
        render()
    }

    private fun capabilitySummary(): String {
        val unavailable = mutableListOf<String>()
        unavailable += getString(R.string.setup_location_unavailable)
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) unavailable += getString(R.string.setup_notifications_unavailable)
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) unavailable += getString(R.string.setup_fullscreen_unavailable)
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) unavailable += getString(R.string.setup_battery_unavailable)
        return if (unavailable.isEmpty()) getString(R.string.setup_all_enabled) else unavailable.joinToString("\n")
    }
}
