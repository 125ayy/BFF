package com.fcapps.bff

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class DiagnosticsActivity : BffActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        findViewById<android.view.View>(R.id.diagContent).requestFocus()
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        button(R.id.diagRecord) {
            if (Diagnostics.active(this)) Diagnostics.stop(this) else Diagnostics.start(this)
            refresh()
        }
        button(R.id.diagRefresh) { refresh() }
        button(R.id.diagClear) { Diagnostics.clear(this); refresh() }
        button(R.id.diagCopy) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("BFF diagnostics", Diagnostics.report(this)))
            Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
        }
        button(R.id.diagShare) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Diagnostics.report(this))
            try { startActivity(Intent.createChooser(send, getString(R.string.diag_share))) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.diag_share_unavailable, Toast.LENGTH_LONG).show() }
        }
        button(R.id.diagTest) {
            AlertDialog.Builder(this).setTitle(R.string.diag_test)
                .setMessage(R.string.diag_test_warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.diag_test_start) { _, _ ->
                    Diagnostics.record(this, Diagnostics.Event.LOCAL_TEST_REQUESTED)
                    try {
                        startForegroundService(Intent(this, AlarmService::class.java).putExtra(AlarmService.EXTRA_DIAGNOSTIC_TEST, true))
                    } catch (e: RuntimeException) { Diagnostics.record(this, Diagnostics.Event.SERVICE_REQUEST_FAILED, e) }
                    refresh()
                }.show()
        }
        button(R.id.diagStopAlarm) { stopService(Intent(this, AlarmService::class.java)); refresh() }
        button(R.id.diagHeard) { Diagnostics.record(this, Diagnostics.Event.LOCAL_TEST_HEARD); refresh() }
        button(R.id.diagNotHeard) { Diagnostics.record(this, Diagnostics.Event.LOCAL_TEST_NOT_HEARD); refresh() }
        button(R.id.diagPermissions) { startActivity(Intent(this, SetupActivity::class.java)) }
    }
    override fun onResume() { super.onResume(); refresh() }
    private fun button(id: Int, action: () -> Unit) { findViewById<Button>(id).setOnClickListener { action() } }
    private fun refresh() {
        findViewById<TextView>(R.id.diagReport).text = Diagnostics.report(this)
        findViewById<Button>(R.id.diagRecord).setText(if (Diagnostics.active(this)) R.string.diag_record_stop else R.string.diag_record_start)
        findViewById<Button>(R.id.diagTest).isEnabled = Diagnostics.active(this)
        findViewById<Button>(R.id.diagHeard).isEnabled = Diagnostics.active(this)
        findViewById<Button>(R.id.diagNotHeard).isEnabled = Diagnostics.active(this)
    }
}
