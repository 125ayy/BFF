package com.fcapps.bff

import android.Manifest
import android.view.View
import org.robolectric.Shadows.shadowOf
import com.fcapps.bff.Prefs.locationEnabled
import com.fcapps.bff.Prefs.smsEnabled
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.fcapps.bff.Prefs.pin
import com.fcapps.bff.Prefs.matrixAllLoc
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clear() { app.getSharedPreferences("bff_prefs", 0).edit().clear().commit() }

    private fun enableLocation() {
        app.locationEnabled = true
        app.smsEnabled = true
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    @Test fun unfinishedGateChangesDoNotAffectLiveAuthorization() {
        enableLocation()
        val controller = Robolectric.buildActivity(PrivacyActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.badgeAllLoc).performClick() // NO -> YES, draft only
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
        controller.pause().stop().destroy()
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
    }

    @Test fun gatesAndPinCommitTogetherAfterValidation() {
        enableLocation()
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        activity.findViewById<TextView>(R.id.badgeAllLoc).performClick() // NO -> YES
        activity.findViewById<TextView>(R.id.badgeAllLoc).performClick() // ON -> OFF
        activity.findViewById<TextView>(R.id.badgeAllAlarm).performClick() // OFF -> PIN
        activity.findViewById<EditText>(R.id.etPin).setText("1234")
        activity.findViewById<EditText>(R.id.etPin2).setText("9999")
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
        assertEquals("", app.pin)
        activity.findViewById<EditText>(R.id.etPin2).setText("1234")
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertEquals(Prefs.MATRIX_PIN, app.matrixAllLoc)
        assertEquals("1234", app.pin)
        assertTrue(activity.isFinishing)
    }

    @Test fun existingPinCanBeSavedWithoutReentry() {
        app.pin = "1234"
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertTrue(activity.isFinishing)
        assertEquals("1234", app.pin)
    }

    @Test fun emptyPinCannotEnablePinRules() {
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        activity.findViewById<TextView>(R.id.badgeAllAlarm).performClick() // ON -> OFF
        activity.findViewById<TextView>(R.id.badgeAllAlarm).performClick() // OFF -> PIN
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertFalse(activity.isFinishing)
        assertEquals("", app.pin)
    }
    @Test fun freshInstallAllowsAlarmWithoutPinAndKeepsLocationOff() {
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        assertEquals("ON", activity.findViewById<TextView>(R.id.badgeAllAlarm).text.toString())
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
    }

    @Test fun unavailableLocationStaysVisibleAndPreservesRules() {
        val controller = Robolectric.buildActivity(PrivacyActivity::class.java).setup()
        val activity = controller.get()
        for (id in listOf(R.id.badgeAllLoc, R.id.badgeContactsLoc, R.id.badgeTrustedLoc)) {
            val badge = activity.findViewById<TextView>(id)
            assertEquals(View.VISIBLE, badge.visibility)
            assertEquals("DISABLE", badge.text.toString())
            assertFalse(badge.isEnabled)
            badge.performClick()
        }
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
        enableLocation()
        controller.pause().resume()
        assertEquals("OFF", activity.findViewById<TextView>(R.id.badgeAllLoc).text.toString())
        assertEquals("PIN", activity.findViewById<TextView>(R.id.badgeContactsLoc).text.toString())
        assertEquals("ON", activity.findViewById<TextView>(R.id.badgeTrustedLoc).text.toString())
    }

    @Test fun alarmCyclesOffPinOnAndLocationCyclesOnPinOff() {
        enableLocation()
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        val alarm = activity.findViewById<TextView>(R.id.badgeAllAlarm)
        for (label in listOf("OFF", "PIN", "ON")) { alarm.performClick(); assertEquals(label, alarm.text.toString()) }
        val location = activity.findViewById<TextView>(R.id.badgeTrustedLoc)
        for (label in listOf("PIN", "OFF", "ON")) { location.performClick(); assertEquals(label, location.text.toString()) }
    }

    @Test fun explicitlySavedPinRuleIsPreserved() {
        app.getSharedPreferences("bff_prefs", 0).edit().putString("matrix_all_alarm", Prefs.MATRIX_PIN).commit()
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        assertEquals("PIN", activity.findViewById<TextView>(R.id.badgeAllAlarm).text.toString())
    }

}
