package com.fcapps.bff

import android.Manifest
import android.content.pm.PackageManager
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
}
