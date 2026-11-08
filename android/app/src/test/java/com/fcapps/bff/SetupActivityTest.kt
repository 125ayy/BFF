package com.fcapps.bff

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.fcapps.bff.Prefs.permissionSetupVersion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

/** Delivers a permission result the way the framework does: the in-flight flag is cleared first. */
private fun SetupActivity.deliver(request: org.robolectric.shadows.ShadowActivity.PermissionsRequest, result: Int) {
    ReflectionHelpers.setField(android.app.Activity::class.java, this, "mHasCurrentPermissionsRequest", false)
    onRequestPermissionsResult(request.requestCode, request.requestedPermissions, IntArray(request.requestedPermissions.size) { result })
}
private fun SetupActivity.title() = findViewById<TextView>(R.id.tvSetupTitle).text.toString()
private fun SetupActivity.btn() = findViewById<Button>(R.id.btnContinue)
private fun SetupActivity.rowStatus(i: Int) =
    (findViewById<LinearLayout>(R.id.rowsContainer).getChildAt(i)).findViewById<TextView>(R.id.tvRowStatus).text.toString()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SetupActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun cleanState() {
        app.getSharedPreferences("permission_requests", 0).edit().clear().commit()
        app.getSharedPreferences("bff_prefs", 0).edit().clear().commit()
        shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "unknown")
    }

    private fun grantGroupB() {
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
    }

    @Test fun stepOneRunsItsPromptsOneAfterTheOtherThenWaitsForContinue() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.setup_group_a_title), activity.title())
        assertEquals(activity.getString(R.string.setup_progress, 1, 3), activity.findViewById<TextView>(R.id.tvSetupProgress).text.toString())
        assertEquals(2, activity.findViewById<LinearLayout>(R.id.rowsContainer).childCount)
        activity.btn().performClick()
        val first = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.READ_CONTACTS), first.requestedPermissions)
        assertFalse(activity.btn().isEnabled)
        activity.btn().performClick()
        assertSame(first, shadowOf(activity).lastRequestedPermission)             // double tap ignored
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        activity.deliver(first, PackageManager.PERMISSION_GRANTED)
        val second = shadowOf(activity).lastRequestedPermission
        assertNotSame(first, second)                                              // next prompt of the group follows at once
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), second.requestedPermissions)
        assertEquals("✓", activity.rowStatus(0))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        activity.deliver(second, PackageManager.PERMISSION_GRANTED)
        assertEquals("✓", activity.rowStatus(1))
        assertEquals(activity.getString(R.string.setup_group_a_title), activity.title())   // stays until the user continues
        assertTrue(activity.btn().isEnabled)
        assertEquals(activity.getString(R.string.setup_continue), activity.btn().text.toString())
        activity.btn().performClick()
        assertEquals(activity.getString(R.string.setup_sms_info_title), activity.title())
        assertSame(second, shadowOf(activity).lastRequestedPermission)            // no prompt opened by moving on
    }

    @Test fun denialPaintsTheRowRedAndRetryPromptsAgain() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, true)
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        activity.btn().performClick()
        val request = shadowOf(activity).lastRequestedPermission
        activity.deliver(request, PackageManager.PERMISSION_DENIED)
        assertEquals(activity.getString(R.string.setup_row_denied), activity.rowStatus(0))
        assertEquals(activity.getString(R.string.setup_group_a_title), activity.title())
        assertEquals(activity.getString(R.string.setup_retry), activity.btn().text.toString())
        assertTrue(activity.btn().isEnabled)
        activity.btn().performClick()
        assertNotSame(request, shadowOf(activity).lastRequestedPermission)
    }

    @Test fun recreationPreservesPendingRequestWithoutLaunchingAgain() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        activity.btn().performClick()
        val request = shadowOf(activity).lastRequestedPermission
        val saved = Bundle()
        controller.pause().stop().saveInstanceState(saved).destroy()
        val restored = Robolectric.buildActivity(SetupActivity::class.java).setup(saved).get()
        assertFalse(restored.btn().isEnabled)
        assertNull(shadowOf(restored).lastRequestedPermission)
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        restored.deliver(request, PackageManager.PERMISSION_GRANTED)
        assertTrue(restored.btn().isEnabled)
        assertEquals(restored.getString(R.string.setup_group_a_title), restored.title())
    }

    @Test fun grantedGroupsAreSkippedAndStepThreeOpensSettingsToggles() {
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.setup_group_b_title), activity.title())
        assertEquals(activity.getString(R.string.setup_progress, 3, 3), activity.findViewById<TextView>(R.id.tvSetupProgress).text.toString())
        assertEquals(2, activity.findViewById<LinearLayout>(R.id.rowsContainer).childCount)   // overlay + battery on 33
        activity.btn().performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, shadowOf(activity).nextStartedActivityForResult.intent.action)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test fun smsStepWarnsWithBrandHelpFailsToAnInstructionScreenAndEndsAtReady() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        grantGroupB()
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS, true)   // first denial: Android still prompts
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.setup_sms_info_title), activity.title())
        assertEquals(activity.getString(R.string.setup_progress, 2, 3), activity.findViewById<TextView>(R.id.tvSetupProgress).text.toString())
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.tvSetupBrand).visibility)
        assertEquals(View.GONE, activity.findViewById<Button>(R.id.btnSecondary).visibility)
        assertNull(shadowOf(activity).lastRequestedPermission)                    // nothing before CONTINUE
        activity.btn().performClick()
        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.RECEIVE_SMS), request.requestedPermissions)
        activity.deliver(request, PackageManager.PERMISSION_DENIED)
        assertEquals(activity.getString(R.string.setup_sms_fail_title), activity.title())
        assertEquals(activity.getString(R.string.setup_sms_fail_body), activity.findViewById<TextView>(R.id.tvSetupDescription).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.btnSecondary).visibility)
        activity.findViewById<Button>(R.id.btnSecondary).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        activity.btn().performClick()                                              // TRY AGAIN
        val again = shadowOf(activity).lastRequestedPermission
        assertNotSame(request, again)
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        activity.deliver(again, PackageManager.PERMISSION_GRANTED)
        assertEquals(ReadyActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertEquals(Prefs.SETUP_VERSION, app.permissionSetupVersion)
        assertTrue(activity.isFinishing)
    }

    @Test fun brandHelpFallsBackToThePlainLine() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Positivo")
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        grantGroupB()
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        activity.btn().performClick()
        activity.deliver(shadowOf(activity).lastRequestedPermission, PackageManager.PERMISSION_DENIED)
        assertEquals(activity.getString(R.string.setup_sms_brand_other), activity.findViewById<TextView>(R.id.tvSetupBrand).text.toString())
    }

    @Test fun everythingAlreadyGrantedGoesStraightToReady() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.RECEIVE_SMS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        grantGroupB()
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertEquals(ReadyActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertTrue(activity.isFinishing)
    }
    @Test fun smsApprovalLeadsToOptionalChecksAndCanContinueWithoutThem() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        activity.btn().performClick()
        val request = shadowOf(activity).lastRequestedPermission
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        activity.deliver(request, PackageManager.PERMISSION_GRANTED)
        assertEquals(activity.getString(R.string.setup_group_b_title), activity.title())
        assertFalse(activity.isFinishing)
        activity.findViewById<Button>(R.id.btnSecondary).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(ReadyActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test fun helpOpensBrowserWithoutChangingPermissions() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.btnHelp).performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
        assertEquals(Help.URL, intent.data.toString())
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertFalse(activity.isFinishing)
    }

}
