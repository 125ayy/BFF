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
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fcapps.bff.PermissionFlow.Step
import com.fcapps.bff.Prefs.permissionSetupVersion

/** Basic grants → receive SMS → optional ringing checks. Location stays in Settings. */
class SetupActivity : BffActivity() {

    enum class Screen { GROUP_A, GROUP_B, SMS_INFO, SMS_FAIL }

    private var screen = Screen.GROUP_A
    private lateinit var flow: PermissionFlow
    private var deniedStep: Step? = null
    private val rows = linkedMapOf<Step, View>()
    private val requestHistory by lazy { getSharedPreferences("permission_requests", MODE_PRIVATE) }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishRequest() }
    private val settingsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishRequest() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        screen = savedInstanceState?.getString("screen")?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.GROUP_A
        flow = PermissionFlow(Build.VERSION.SDK_INT, stepsOf(screen),
            savedInstanceState?.getString("step")?.let { runCatching { Step.valueOf(it) }.getOrNull() }?.takeIf { it in stepsOf(screen) } ?: stepsOf(screen).first(),
            savedInstanceState?.getBoolean("waiting", false) ?: false)
        deniedStep = savedInstanceState?.getString("denied")?.let { runCatching { Step.valueOf(it) }.getOrNull() }
        findViewById<Button>(R.id.btnContinue).setOnClickListener { onContinue() }
        findViewById<Button>(R.id.btnSecondary).setOnClickListener { if (screen == Screen.GROUP_B) complete() else openAppSettings() }
        findViewById<Button>(R.id.btnHelp).setOnClickListener { Help.open(this) }
        if (savedInstanceState == null && skipGrantedGroups()) return
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!::flow.isInitialized) return
        if (flow.waiting) { if (satisfied(flow.step)) finishRequest(); return }   // a result that never arrived
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name)
        outState.putString("step", flow.step.name)
        outState.putBoolean("waiting", flow.waiting)
        outState.putString("denied", deniedStep?.name)
        super.onSaveInstanceState(outState)
    }

    // ---- what each step is ---------------------------------------------------------------

    private fun stepsOf(s: Screen) = when (s) {
        Screen.GROUP_A -> PermissionFlow.GROUP_A
        Screen.GROUP_B -> PermissionFlow.GROUP_B
        else -> PermissionFlow.GROUP_C
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

    private fun relevantSteps(s: Screen) = stepsOf(s).filter { it != Step.COMPLETE && flow.relevant(it) }
    private fun groupGranted(s: Screen) = relevantSteps(s).all { satisfied(it) }

    private fun rowLabel(step: Step) = when (step) {
        Step.CONTACTS -> R.string.setup_row_contacts to R.string.setup_row_contacts_sub
        Step.NOTIFICATIONS -> R.string.setup_row_notifications to R.string.setup_row_notifications_sub
        Step.OVERLAY -> R.string.setup_row_overlay to R.string.setup_row_overlay_sub
        Step.FULL_SCREEN -> R.string.setup_row_fullscreen to R.string.setup_row_fullscreen_sub
        else -> R.string.setup_row_battery to R.string.setup_row_battery_sub
    }

    /** Five makers that top Brazil and the US together; everyone else gets the plain line. */
    private fun brandText(): String {
        val m = Build.MANUFACTURER.lowercase()
        return getString(when {
            "google" in m -> R.string.setup_sms_brand_google
            "samsung" in m -> R.string.setup_sms_brand_samsung
            "motorola" in m -> R.string.setup_sms_brand_motorola
            "xiaomi" in m || "redmi" in m || "poco" in m -> R.string.setup_sms_brand_xiaomi
            "oneplus" in m || "realme" in m || "oppo" in m -> R.string.setup_sms_brand_oneplus
            else -> R.string.setup_sms_brand_other
        })
    }

    // ---- screens -------------------------------------------------------------------------

    /** On first entry only: groups already granted are not shown. Returns true when setup is already complete. */
    private fun skipGrantedGroups(): Boolean {
        if (screen == Screen.GROUP_A && groupGranted(screen)) show(Screen.SMS_INFO)
        if (screen == Screen.SMS_INFO && granted(Manifest.permission.RECEIVE_SMS)) show(Screen.GROUP_B)
        if (screen == Screen.GROUP_B && groupGranted(screen)) { complete(); return true }
        return false
    }

    private fun show(s: Screen) {
        screen = s
        flow = PermissionFlow(Build.VERSION.SDK_INT, stepsOf(s))
        deniedStep = null
    }

    private fun render() {
        val stepNo = when (screen) { Screen.GROUP_A -> 1; Screen.GROUP_B -> 3; else -> 2 }
        findViewById<TextView>(R.id.tvSetupProgress).text = getString(R.string.setup_progress, stepNo, 3)
        findViewById<TextView>(R.id.tvSetupTitle).setText(when (screen) {
            Screen.GROUP_A -> R.string.setup_group_a_title
            Screen.GROUP_B -> R.string.setup_group_b_title
            Screen.SMS_INFO -> R.string.setup_sms_info_title
            Screen.SMS_FAIL -> R.string.setup_sms_fail_title
        })
        val isSms = screen == Screen.SMS_INFO || screen == Screen.SMS_FAIL
        findViewById<TextView>(R.id.tvSetupDescription).apply {
            visibility = if (isSms || screen == Screen.GROUP_B) View.VISIBLE else View.GONE
            if (screen == Screen.GROUP_B) setText(R.string.setup_ringing_optional)
            else if (isSms) setText(if (screen == Screen.SMS_INFO) R.string.setup_sms_info_body else R.string.setup_sms_fail_body)
        }
        findViewById<TextView>(R.id.tvSetupBrand).apply {
            visibility = if (screen == Screen.SMS_FAIL) View.VISIBLE else View.GONE
            if (screen == Screen.SMS_FAIL) text = brandText()
        }
        renderRows()
        val allGranted = !isSms && groupGranted(screen)
        findViewById<Button>(R.id.btnContinue).apply {
            isEnabled = !flow.waiting
            setText(when {
                flow.waiting -> R.string.setup_waiting
                allGranted -> R.string.setup_continue
                deniedStep != null && needsSettings(deniedStep!!) -> R.string.setup_open_settings
                deniedStep != null || screen == Screen.SMS_FAIL -> R.string.setup_retry
                else -> R.string.setup_continue
            })
        }
        findViewById<Button>(R.id.btnSecondary).apply {
            visibility = if (screen == Screen.SMS_FAIL || screen == Screen.GROUP_B) View.VISIBLE else View.GONE
            setText(if (screen == Screen.GROUP_B) R.string.setup_later else R.string.setup_open_settings)
            isEnabled = !flow.waiting
        }
    }

    private fun renderRows() {
        val container = findViewById<LinearLayout>(R.id.rowsContainer)
        val wanted = if (screen == Screen.GROUP_A || screen == Screen.GROUP_B) relevantSteps(screen) else emptyList()
        if (rows.keys.toList() != wanted) {
            container.removeAllViews(); rows.clear()
            for (step in wanted) {
                val row = LayoutInflater.from(this).inflate(R.layout.row_setup_permission, container, false)
                val (label, sub) = rowLabel(step)
                row.findViewById<TextView>(R.id.tvRowLabel).setText(label)
                row.findViewById<TextView>(R.id.tvRowSub).setText(sub)
                container.addView(row); rows[step] = row
            }
        }
        container.visibility = if (wanted.isEmpty()) View.GONE else View.VISIBLE
        for ((step, row) in rows) {
            row.findViewById<TextView>(R.id.tvRowStatus).apply {
                when {
                    satisfied(step) -> { text = getString(R.string.setup_row_done); setTextColor(0xFF00C853.toInt()) }
                    flow.waiting && flow.step == step -> { text = "…"; setTextColor(0xFF9E9E9E.toInt()) }
                    deniedStep == step -> { text = getString(R.string.setup_row_denied); setTextColor(0xFFFF8A80.toInt()) }
                    else -> text = ""
                }
            }
        }
    }

    // ---- the walk ------------------------------------------------------------------------

    private fun onContinue() {
        if (flow.waiting) return
        when (screen) {
            Screen.GROUP_A, Screen.GROUP_B -> {
                if (groupGranted(screen)) {
                    if (screen == Screen.GROUP_B) { complete(); return }
                    show(Screen.SMS_INFO); skipGrantedGroups(); render(); return
                }
                flow = PermissionFlow(Build.VERSION.SDK_INT, stepsOf(screen))
                skipSatisfied()
                launchCurrent()
            }
            Screen.SMS_INFO, Screen.SMS_FAIL -> {
                if (granted(Manifest.permission.RECEIVE_SMS)) { show(Screen.GROUP_B); skipGrantedGroups(); render(); return }
                flow = PermissionFlow(Build.VERSION.SDK_INT, PermissionFlow.GROUP_C)
                launchCurrent()
            }
        }
    }

    private fun skipSatisfied() {
        while (flow.step != Step.COMPLETE && (!flow.relevant(flow.step) || satisfied(flow.step))) flow.advance()
    }

    private fun needsSettings(step: Step): Boolean {
        val p = permissions(step).firstOrNull() ?: return false
        if (step == Step.NOTIFICATIONS && granted(p) && !satisfied(step)) return true
        return requestHistory.getBoolean(p, false) && !ActivityCompat.shouldShowRequestPermissionRationale(this, p) && !satisfied(step)
    }

    private fun launchCurrent() {
        if (flow.step == Step.COMPLETE) { render(); return }
        if (!flow.begin()) return
        deniedStep = null
        val settings = when {
            needsSettings(flow.step) -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
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
        } catch (_: RuntimeException) {
            val step = flow.step
            flow.result(false); failed(step)
        }
    }

    private fun openAppSettings() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: RuntimeException) { }
    }

    private fun finishRequest() {
        if (!::flow.isInitialized || !flow.waiting) return
        val step = flow.step
        val ok = satisfied(step)
        flow.result(ok)
        if (!ok) { failed(step); return }
        deniedStep = null
        if (screen == Screen.SMS_INFO || screen == Screen.SMS_FAIL) { show(Screen.GROUP_B); skipGrantedGroups(); render(); return }
        skipSatisfied()
        if (flow.step == Step.COMPLETE) render() else launchCurrent()   // next prompt of the group follows at once
    }

    private fun failed(step: Step) {
        deniedStep = step
        if (screen == Screen.SMS_INFO) screen = Screen.SMS_FAIL
        render()
    }

    private fun complete() {
        permissionSetupVersion = Prefs.SETUP_VERSION
        startActivity(Intent(this, ReadyActivity::class.java))
        finish()
    }
}
