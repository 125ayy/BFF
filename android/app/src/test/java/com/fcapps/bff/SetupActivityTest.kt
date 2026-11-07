package com.fcapps.bff

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun cleanState() {
        app.getSharedPreferences("permission_requests", 0).edit().clear().commit()
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
        shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    @Test fun smsRequestsAreSeparateAndDoubleTapIsIgnored() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val allow = activity.findViewById<Button>(R.id.btnContinue)
        allow.performClick()
        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.RECEIVE_SMS), request.requestedPermissions)
        assertFalse(allow.isEnabled)
        allow.performClick()
        assertSame(request, shadowOf(activity).lastRequestedPermission)
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertEquals(activity.getString(R.string.setup_contacts_title), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        assertTrue(allow.isEnabled)
        // Completing one request shows the next explanation; it does not open another prompt.
        assertSame(request, shadowOf(activity).lastRequestedPermission)
    }

    @Test fun denialDoesNotAdvanceAndRequiredSmsCannotBeSkipped() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.btnContinue).performClick()
        val request = shadowOf(activity).lastRequestedPermission
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
        assertEquals(activity.getString(R.string.setup_sms_title), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        assertEquals(android.view.View.GONE, activity.findViewById<Button>(R.id.btnSkip).visibility)
        assertTrue(activity.findViewById<Button>(R.id.btnContinue).isEnabled)
    }

    @Test fun recreationPreservesPendingRequestWithoutLaunchingAgain() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<Button>(R.id.btnContinue).performClick()
        val request = shadowOf(activity).lastRequestedPermission
        val saved = Bundle()
        controller.pause().stop().saveInstanceState(saved).destroy()
        val restored = Robolectric.buildActivity(SetupActivity::class.java).setup(saved).get()
        assertFalse(restored.findViewById<Button>(R.id.btnContinue).isEnabled)
        assertNull(shadowOf(restored).lastRequestedPermission)
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        restored.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertTrue(restored.findViewById<Button>(R.id.btnContinue).isEnabled)
        assertEquals(restored.getString(R.string.setup_contacts_title), restored.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
    }


    @Test fun setupNeverAsksForSmsSendingOrLocation() {
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        // SMS receive, contacts and notifications are satisfied: the first card is the overlay step.
        assertEquals(activity.getString(R.string.setup_overlay_title), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        assertNull(shadowOf(activity).lastRequestedPermission)
        activity.findViewById<Button>(R.id.btnContinue).performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, shadowOf(activity).nextStartedActivityForResult.intent.action)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }
}
