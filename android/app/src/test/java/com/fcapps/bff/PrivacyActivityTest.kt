package com.fcapps.bff

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

    @Test fun unfinishedGateChangesDoNotAffectLiveAuthorization() {
        val controller = Robolectric.buildActivity(PrivacyActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.badgeAllLoc).performClick() // NO -> YES, draft only
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
        controller.pause().stop().destroy()
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
    }

    @Test fun gatesAndPinCommitTogetherAfterValidation() {
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        activity.findViewById<TextView>(R.id.badgeAllLoc).performClick() // NO -> YES
        activity.findViewById<TextView>(R.id.badgeAllLoc).performClick() // YES -> PIN
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
        activity.findViewById<TextView>(R.id.badgeAllAlarm).performClick() // YES -> PIN
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertFalse(activity.isFinishing)
        assertEquals("", app.pin)
    }
    @Test fun freshInstallAllowsAlarmWithoutPinAndKeepsLocationOff() {
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        assertEquals("YES", activity.findViewById<TextView>(R.id.badgeAllAlarm).text.toString())
        activity.findViewById<Button>(R.id.btnDone).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(Prefs.MATRIX_NO, app.matrixAllLoc)
    }

    @Test fun explicitlySavedPinRuleIsPreserved() {
        app.getSharedPreferences("bff_prefs", 0).edit().putString("matrix_all_alarm", Prefs.MATRIX_PIN).commit()
        val activity = Robolectric.buildActivity(PrivacyActivity::class.java).setup().get()
        assertEquals("PIN", activity.findViewById<TextView>(R.id.badgeAllAlarm).text.toString())
    }

}
