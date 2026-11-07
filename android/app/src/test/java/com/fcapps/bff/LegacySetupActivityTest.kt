package com.fcapps.bff

import android.Manifest
import android.os.Build
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

    @Test fun setupGoesStraightToAlarmStepsWithoutLocation() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val expected = if (Build.VERSION.SDK_INT >= 33) R.string.setup_notifications_title else R.string.setup_overlay_title
        assertEquals(activity.getString(expected), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test fun skippingOverlayDoesNotOpenAnySystemPrompt() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.setup_overlay_title), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        activity.findViewById<Button>(R.id.btnSkip).performClick()
        val next = if (Build.VERSION.SDK_INT >= 34) R.string.setup_fullscreen_title else R.string.setup_battery_title
        assertEquals(activity.getString(next), activity.findViewById<TextView>(R.id.tvSetupTitle).text.toString())
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }
}
