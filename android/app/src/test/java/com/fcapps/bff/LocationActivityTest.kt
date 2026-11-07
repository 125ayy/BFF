package com.fcapps.bff

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import com.fcapps.bff.Prefs.locationEnabled
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LocationActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        app.getSharedPreferences("permission_requests", 0).edit().clear().commit()
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
        shadowOf(app).denyPermissions(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    @Test fun offByDefaultAndDisclosureComesBeforeAnyPrompt() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val sw = activity.findViewById<SwitchCompat>(R.id.swLocation)
        assertFalse(sw.isChecked)
        assertFalse(app.locationEnabled)
        sw.performClick()
        assertFalse(sw.isChecked)                                   // stays off until every grant is in
        assertNull(shadowOf(activity).lastRequestedPermission)      // no prompt before the disclosure
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals(activity.getString(R.string.location_disclosure), shadowOf(dialog).message.toString())
    }

    @Test fun backgroundDeniedLeavesSwitchOffWithReason() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        activity.findViewById<SwitchCompat>(R.id.swLocation).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()   // dialog button clicks are posted
        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), request.requestedPermissions)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
        assertFalse(activity.findViewById<SwitchCompat>(R.id.swLocation).isChecked)
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_needs_background), activity.findViewById<TextView>(R.id.tvLocationMessage).text.toString())
    }

    @Test fun fullGrantTurnsOnAndRevocationTurnsOffOnResume() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val controller = Robolectric.buildActivity(LocationActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<SwitchCompat>(R.id.swLocation).performClick()
        assertTrue(activity.findViewById<SwitchCompat>(R.id.swLocation).isChecked)
        assertTrue(app.locationEnabled)
        // Android (or the user) revokes background location: the switch cannot keep saying On.
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        controller.pause().resume()
        assertFalse(activity.findViewById<SwitchCompat>(R.id.swLocation).isChecked)
        assertFalse(app.locationEnabled)
    }

    /** A slide ends in setChecked, never in a click: it must land in the same handler as a tap. */
    @Test fun slidingTheSwitchBehavesLikeATap() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val sw = activity.findViewById<SwitchCompat>(R.id.swLocation)
        sw.isChecked = true
        assertFalse(sw.isChecked)                                   // put back off until the grants are in
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_state_off), activity.findViewById<TextView>(R.id.tvLocationState).text.toString())
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals(activity.getString(R.string.location_disclosure), shadowOf(dialog).message.toString())
    }

    @Test fun slidingOffTurnsOff() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        app.locationEnabled = true
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val sw = activity.findViewById<SwitchCompat>(R.id.swLocation)
        assertTrue(sw.isChecked)
        sw.isChecked = false
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_state_off), activity.findViewById<TextView>(R.id.tvLocationState).text.toString())
    }
}

/** Android 11+: background location is only granted from the app's Settings page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationActivitySettingsWalkTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        app.getSharedPreferences("permission_requests", 0).edit().clear().commit()
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Tap → disclosure → Continue → background dialog → Open Settings; returns the Settings intent. */
    private fun walkToSettings(activity: LocationActivity): Intent {
        activity.findViewById<SwitchCompat>(R.id.swLocation).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        val background = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(activity.getString(R.string.setup_background_title), shadowOf(background).title.toString())
        assertTrue(activity.findViewById<SwitchCompat>(R.id.swLocation).isEnabled)   // dialog up, nothing pending yet
        background.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.intent.action)
        assertEquals(activity.getString(R.string.setup_waiting), activity.findViewById<TextView>(R.id.tvLocationState).text.toString())
        return started.intent
    }

    @Test fun allTheTimeGrantedInSettingsTurnsOn() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val intent = walkToSettings(activity)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null); idle()
        assertTrue(activity.findViewById<SwitchCompat>(R.id.swLocation).isChecked)
        assertTrue(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_state_on), activity.findViewById<TextView>(R.id.tvLocationState).text.toString())
    }

    @Test fun comingBackWithoutTheGrantLeavesOffWithReason() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val intent = walkToSettings(activity)
        shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null); idle()
        assertFalse(activity.findViewById<SwitchCompat>(R.id.swLocation).isChecked)
        assertTrue(activity.findViewById<SwitchCompat>(R.id.swLocation).isEnabled)
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_needs_background), activity.findViewById<TextView>(R.id.tvLocationMessage).text.toString())
    }

    /** The result came back early (or the app was restarted); the user then granted it in Settings and returned. */
    @Test fun grantMadeAfterTheWalkGaveUpIsPickedUpOnResume() {
        val controller = Robolectric.buildActivity(LocationActivity::class.java).setup()
        val activity = controller.get()
        val intent = walkToSettings(activity)
        shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null); idle()
        assertFalse(app.locationEnabled)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        controller.pause().resume()
        assertTrue(activity.findViewById<SwitchCompat>(R.id.swLocation).isChecked)
        assertTrue(app.locationEnabled)
        assertEquals(android.view.View.GONE, activity.findViewById<TextView>(R.id.tvLocationMessage).visibility)
    }

    /** Recreated while the background dialog was up: the dialog comes back, the switch is not stuck on "Waiting". */
    @Test fun recreationWhileTheBackgroundDialogIsUpShowsItAgain() {
        val controller = Robolectric.buildActivity(LocationActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<SwitchCompat>(R.id.swLocation).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        assertEquals(activity.getString(R.string.setup_background_title), shadowOf(ShadowAlertDialog.getLatestAlertDialog()).title.toString())
        val state = Bundle()
        controller.saveInstanceState(state)
        ShadowAlertDialog.reset()
        val again = Robolectric.buildActivity(LocationActivity::class.java).setup(state).get()
        assertTrue(again.findViewById<SwitchCompat>(R.id.swLocation).isEnabled)
        assertEquals(again.getString(R.string.setup_background_title), shadowOf(ShadowAlertDialog.getLatestAlertDialog()).title.toString())
    }
}
