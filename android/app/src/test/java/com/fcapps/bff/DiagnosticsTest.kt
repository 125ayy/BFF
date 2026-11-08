package com.fcapps.bff

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.fcapps.bff.Prefs.matrixAllAlarm
import com.fcapps.bff.Prefs.pin
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class DiagnosticsTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    @Before fun reset() {
        Diagnostics.clear(app)
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
    }

    @Test fun offByDefaultAndExpiredSessionsDoNotCapture() {
        Diagnostics.record(app, Diagnostics.Event.SMS_RECEIVED)
        assertFalse(Diagnostics.report(app).contains("SMS_RECEIVED NONE"))
        Diagnostics.start(app)
        app.getSharedPreferences("bff_diagnostics", 0).edit().putLong("until", 1).commit()
        Diagnostics.record(app, Diagnostics.Event.SMS_RECEIVED)
        assertFalse(Diagnostics.report(app).contains("SMS_RECEIVED NONE"))
    }

    @Test fun boundedPersistentTraceNeverRecordsExceptionMessages() {
        Diagnostics.start(app)
        repeat(200) { Diagnostics.record(app, Diagnostics.Event.SERVICE_REQUEST_FAILED, SecurityException("secret SMS PIN 6789 +15551234567")) }
        val report = Diagnostics.report(app.createPackageContext(app.packageName, 0))
        assertEquals(Diagnostics.MAX_EVENTS, report.lineSequence().count { it.contains("SERVICE_REQUEST_FAILED SECURITY") })
        assertFalse(report.contains("6789")); assertFalse(report.contains("15551234567"))
        Diagnostics.stop(app)
        Diagnostics.record(app, Diagnostics.Event.SMS_RECEIVED)
        assertFalse(Diagnostics.report(app).contains("SMS_RECEIVED NONE"))
        Diagnostics.clear(app)
        assertFalse(Diagnostics.active(app))
        assertFalse(Diagnostics.report(app).contains("SERVICE_REQUEST_FAILED SECURITY"))
    }

    // Real SMS-DELIVER PDU with UCS-2 text: exercises Telephony parsing and the actual receiver.
    private fun sms(body: String): Intent {
        val prefix = byteArrayOf(0, 0, 10, 0x91.toByte(), 0x21, 0x43, 0x65, 0x87.toByte(), 0x09,
            0, 8, 0x62, 0x11, 0x90.toByte(), 0, 0, 0, 0)
        val text = body.toByteArray(Charsets.UTF_16BE)
        val pdu = prefix + byteArrayOf(text.size.toByte()) + text
        return Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            .putExtra("pdus", arrayOf(pdu)).putExtra("format", "3gpp")
    }

    @Test fun receiverShowsPinRejectionWithoutLeakingTextOrStartingAlarm() {
        Diagnostics.start(app)
        app.pin = "6789"
        app.matrixAllAlarm = Prefs.MATRIX_PIN
        SmsReceiver().onReceive(app, sms("BFF"))
        val report = Diagnostics.report(app)
        assertTrue(report, report.contains("SMS_RECEIVED NONE"))
        assertTrue(report, report.contains("ACTIVATION_MATCHED NONE"))
        assertTrue(report, report.contains("ALARM_BLOCKED_PIN NONE"))
        assertFalse(report.contains("SERVICE_REQUESTED NONE"))
        assertNull(shadowOf(app).nextStartedService)
        assertFalse(report.contains("6789"))
    }

    @Test fun receiverTracesAllowedCommandThroughServiceRequest() {
        Diagnostics.start(app)
        app.matrixAllAlarm = Prefs.MATRIX_YES
        SmsReceiver().onReceive(app, sms("BFF"))
        val report = Diagnostics.report(app)
        assertTrue(report, report.contains("ALARM_ALLOWED NONE"))
        assertTrue(report, report.contains("SERVICE_REQUEST_ACCEPTED NONE"))
        assertEquals(AlarmService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test fun unmatchedAndEmptyMessagesHaveDistinctEvidence() {
        Diagnostics.start(app)
        SmsReceiver().onReceive(app, sms("unrelated private text"))
        assertTrue(Diagnostics.report(app).contains("ACTIVATION_NOT_MATCHED NONE"))
        assertFalse(Diagnostics.report(app).contains("unrelated private text"))
        SmsReceiver().onReceive(app, Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
        assertTrue(Diagnostics.report(app).contains("SMS_EMPTY NONE"))
    }

    @Test fun testRequiresRecordingAndExplicitConfirmation() {
        val activity = Robolectric.buildActivity(DiagnosticsActivity::class.java).setup().get()
        assertFalse(activity.findViewById<Button>(R.id.diagTest).isEnabled)
        activity.findViewById<Button>(R.id.diagRecord).performClick()
        activity.findViewById<Button>(R.id.diagTest).performClick()
        assertNull(shadowOf(app).nextStartedService)
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val intent = shadowOf(app).nextStartedService
        assertTrue(intent.getBooleanExtra(AlarmService.EXTRA_DIAGNOSTIC_TEST, false))
        assertEquals(AlarmService::class.java.name, intent.component?.className)
    }
    @Test fun backgroundLaunchFailureIsCapturedWithoutExceptionText() {
        Diagnostics.start(app)
        val context = object : android.content.ContextWrapper(app) {
            override fun startForegroundService(service: Intent): android.content.ComponentName? {
                throw IllegalStateException("sensitive message text 6789")
            }
        }
        AlarmLauncher.launch(context, "private-number", "private-name")
        val report = Diagnostics.report(app)
        assertTrue(report, report.contains("SERVICE_REQUEST_FAILED ILLEGAL_STATE"))
        assertFalse(report.contains("sensitive message"))
        assertFalse(report.contains("private-number"))
        assertFalse(report.contains("private-name"))
    }

    @Test fun localTestStopsAfterFiveSecondsAndRestoresVolume() {
        Diagnostics.start(app)
        val audio = app.getSystemService(android.media.AudioManager::class.java)
        audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 3, 0)
        val controller = Robolectric.buildService(AlarmService::class.java).create()
        try {
            controller.get().onStartCommand(Intent(app, AlarmService::class.java)
                .putExtra(AlarmService.EXTRA_DIAGNOSTIC_TEST, true), 0, 1)
            assertTrue(Diagnostics.report(app), AlarmService.running)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
            assertFalse(AlarmService.running)
            assertEquals(3, audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM))
        } finally { controller.destroy() }
    }

}
