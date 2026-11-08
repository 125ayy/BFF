package com.fcapps.bff

import android.Manifest
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
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
@Config(sdk = [26, 29, 31])
class LegacySetupActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        app.getSharedPreferences("permission_requests", 0).edit().clear().commit()
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    @Test fun stepOneGrantedOpensOnStepTwoWithTheRightRows() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.setup_group_b_title), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        val rows = activity.findViewById<LinearLayout>(R.id.rowsContainer).childCount
        assertEquals(if (Build.VERSION.SDK_INT >= 34) 3 else 2, rows)    // overlay, (full-screen), battery
        assertNull(shadowOf(activity).lastRequestedPermission)
        activity.findViewById<Button>(R.id.btnContinue).performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, shadowOf(activity).nextStartedActivityForResult.intent.action)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test fun setupNeverAsksForSendingSmsOrLocation() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val all = PermissionFlow.SETUP_GROUPS.flatten()
        assertFalse(all.any { it in listOf(PermissionFlow.Step.SEND_SMS, PermissionFlow.Step.LOCATION, PermissionFlow.Step.BACKGROUND_LOCATION) })
        assertNull(shadowOf(activity).lastRequestedPermission)
    }
}
