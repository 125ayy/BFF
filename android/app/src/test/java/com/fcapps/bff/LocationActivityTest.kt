package com.fcapps.bff

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import com.fcapps.bff.Prefs.locationEnabled
import com.fcapps.bff.Prefs.locationRepliesOn
import com.fcapps.bff.Prefs.smsEnabled
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

private const val SMS = Manifest.permission.SEND_SMS
private const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
private const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
private const val BACKGROUND = Manifest.permission.ACCESS_BACKGROUND_LOCATION

private fun idle() = shadowOf(Looper.getMainLooper()).idle()
private fun LocationActivity.sw(id: Int) = findViewById<SwitchCompat>(id)
private fun LocationActivity.text(id: Int) = findViewById<TextView>(id).text.toString()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LocationActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        app.getSharedPreferences("permission_requests", 0).edit().clear().commit()
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
        shadowOf(app).denyPermissions(SMS, FINE, COARSE, BACKGROUND)
    }

    @Test fun bothOffByDefaultAndDisclosureComesBeforeAnyLocationPrompt() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        assertFalse(activity.sw(R.id.swLocation).isChecked)
        assertFalse(activity.sw(R.id.swSms).isChecked)
        assertFalse(app.locationRepliesOn)
        activity.sw(R.id.swLocation).performClick()
        assertFalse(activity.sw(R.id.swLocation).isChecked)         // stays off until every grant is in
        assertNull(shadowOf(activity).lastRequestedPermission)      // no prompt before the disclosure
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals(activity.getString(R.string.location_disclosure), shadowOf(dialog).message.toString())
    }

    @Test fun backgroundDeniedLeavesLocationOffWithReason() {
        shadowOf(app).grantPermissions(FINE, COARSE)
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        activity.sw(R.id.swLocation).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(BACKGROUND), request.requestedPermissions)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
        assertFalse(activity.sw(R.id.swLocation).isChecked)
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_needs_background), activity.text(R.id.tvLocationMessage))
    }

    @Test fun fullGrantsTurnOnAndRevocationTurnsOffOnResume() {
        shadowOf(app).grantPermissions(SMS, FINE, COARSE, BACKGROUND)
        val controller = Robolectric.buildActivity(LocationActivity::class.java).setup()
        val activity = controller.get()
        activity.sw(R.id.swLocation).performClick()
        activity.sw(R.id.swSms).performClick()
        assertTrue(activity.sw(R.id.swLocation).isChecked)
        assertTrue(activity.sw(R.id.swSms).isChecked)
        assertTrue(app.locationRepliesOn)
        // Android (or the user) revokes background location: that switch cannot keep saying On.
        shadowOf(app).denyPermissions(BACKGROUND)
        controller.pause().resume()
        assertFalse(activity.sw(R.id.swLocation).isChecked)
        assertTrue(activity.sw(R.id.swSms).isChecked)
        assertFalse(app.locationEnabled)
        assertTrue(app.smsEnabled)
        assertFalse(app.locationRepliesOn)
    }

    /** A slide ends in setChecked, never in a click: it must land in the same handler as a tap. */
    @Test fun slidingTheSwitchBehavesLikeATap() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        activity.sw(R.id.swLocation).isChecked = true
        assertFalse(activity.sw(R.id.swLocation).isChecked)         // put back off until the grants are in
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_state_off), activity.text(R.id.tvLocationState))
        assertEquals(activity.getString(R.string.location_disclosure), shadowOf(ShadowAlertDialog.getLatestAlertDialog()).message.toString())
    }

    @Test fun slidingOffTurnsOff() {
        shadowOf(app).grantPermissions(SMS, FINE, COARSE, BACKGROUND)
        app.locationEnabled = true; app.smsEnabled = true
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        assertTrue(activity.sw(R.id.swSms).isChecked)
        activity.sw(R.id.swSms).isChecked = false
        assertFalse(app.smsEnabled)
        assertTrue(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_state_off), activity.text(R.id.tvSmsState))
        assertFalse(app.locationRepliesOn)
    }

    /** The SMS switch asks only for SMS, with no location disclosure. */
    @Test fun smsSwitchPromptsSmsOnly() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        activity.sw(R.id.swSms).isChecked = true
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(SMS), request.requestedPermissions)
        shadowOf(app).grantPermissions(SMS)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertTrue(activity.sw(R.id.swSms).isChecked)
        assertTrue(app.smsEnabled)
        assertFalse(app.locationEnabled)
        assertFalse(app.locationRepliesOn)                           // location still off
    }

    /** SMS prompt refused for good earlier: the walk explains the Settings route instead of opening it silently. */
    @Test fun smsRoutedToSettingsExplainsItselfFirst() {
        app.getSharedPreferences("permission_requests", 0).edit().putBoolean(SMS, true).commit()
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        activity.sw(R.id.swSms).performClick()
        assertNull(shadowOf(activity).lastRequestedPermission)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(activity.getString(R.string.location_open_settings_description, activity.getString(R.string.perm_group_sms)), shadowOf(dialog).message.toString())
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity).nextStartedActivityForResult.intent.action)
        assertEquals(activity.getString(R.string.setup_waiting), activity.text(R.id.tvSmsState))
    }

    @Test fun stateLineNamesTheMissingBackgroundGrant() {
        shadowOf(app).grantPermissions(FINE, COARSE)
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.location_missing_background, activity.getString(R.string.location_all_the_time)), activity.text(R.id.tvLocationState))
        assertEquals(activity.getString(R.string.location_state_off), activity.text(R.id.tvSmsState))
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
        shadowOf(app).denyPermissions(SMS, BACKGROUND)
        shadowOf(app).grantPermissions(FINE, COARSE)
    }

    /** Tap → disclosure → Continue → background dialog → Open Settings; returns the Settings intent. */
    private fun walkToSettings(activity: LocationActivity): Intent {
        activity.sw(R.id.swLocation).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        val background = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(activity.getString(R.string.setup_background_title), shadowOf(background).title.toString())
        assertTrue(activity.sw(R.id.swLocation).isEnabled)           // dialog up, nothing pending yet
        background.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.intent.action)
        assertEquals(activity.getString(R.string.setup_waiting), activity.text(R.id.tvLocationState))
        assertFalse(activity.sw(R.id.swSms).isEnabled)               // one walk at a time
        return started.intent
    }

    @Test fun allTheTimeGrantedInSettingsTurnsLocationOn() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val intent = walkToSettings(activity)
        shadowOf(app).grantPermissions(BACKGROUND)
        shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null); idle()
        assertTrue(activity.sw(R.id.swLocation).isChecked)
        assertTrue(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_state_on_short), activity.text(R.id.tvLocationState))
        assertTrue(activity.sw(R.id.swSms).isEnabled)
        assertFalse(app.locationRepliesOn)                           // SMS switch still off
    }

    @Test fun comingBackWithoutTheGrantLeavesOffWithReason() {
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        val intent = walkToSettings(activity)
        shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null); idle()
        assertFalse(activity.sw(R.id.swLocation).isChecked)
        assertTrue(activity.sw(R.id.swLocation).isEnabled)
        assertFalse(app.locationEnabled)
        assertEquals(activity.getString(R.string.location_needs_background), activity.text(R.id.tvLocationMessage))
    }

    /** The result came back early (or the app was restarted); the user then granted it in Settings and returned. */
    @Test fun grantMadeAfterTheWalkGaveUpIsPickedUpOnResume() {
        val controller = Robolectric.buildActivity(LocationActivity::class.java).setup()
        val activity = controller.get()
        val intent = walkToSettings(activity)
        shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null); idle()
        assertFalse(app.locationEnabled)
        shadowOf(app).grantPermissions(BACKGROUND)
        controller.pause().resume()
        assertTrue(activity.sw(R.id.swLocation).isChecked)
        assertTrue(app.locationEnabled)
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.tvLocationMessage).visibility)
    }

    /** Recreated while the background dialog was up: the dialog comes back, the switch is not stuck on "Waiting". */
    @Test fun recreationWhileTheBackgroundDialogIsUpShowsItAgain() {
        val controller = Robolectric.buildActivity(LocationActivity::class.java).setup()
        val activity = controller.get()
        activity.sw(R.id.swLocation).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        assertEquals(activity.getString(R.string.setup_background_title), shadowOf(ShadowAlertDialog.getLatestAlertDialog()).title.toString())
        val state = Bundle()
        controller.saveInstanceState(state)
        ShadowAlertDialog.reset()
        val again = Robolectric.buildActivity(LocationActivity::class.java).setup(state).get()
        assertTrue(again.sw(R.id.swLocation).isEnabled)
        assertEquals(again.getString(R.string.setup_background_title), shadowOf(ShadowAlertDialog.getLatestAlertDialog()).title.toString())
    }

    /** Both on: replies on. */
    @Test fun bothSwitchesOnMeansRepliesOn() {
        shadowOf(app).grantPermissions(SMS, BACKGROUND)
        val activity = Robolectric.buildActivity(LocationActivity::class.java).setup().get()
        activity.sw(R.id.swLocation).isChecked = true
        activity.sw(R.id.swSms).isChecked = true
        assertTrue(app.locationRepliesOn)
        assertEquals(activity.getString(R.string.location_state_on_short), activity.text(R.id.tvSmsState))
    }
}
