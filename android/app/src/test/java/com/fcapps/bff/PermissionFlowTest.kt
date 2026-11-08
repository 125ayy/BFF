package com.fcapps.bff

import com.fcapps.bff.PermissionFlow.Step
import org.junit.Assert.*
import org.junit.Test

class PermissionFlowTest {
    @Test fun setupGroupsCoverTheSirenAndNothingElse() {
        val all = PermissionFlow.SETUP_GROUPS.flatten().filter { it != Step.COMPLETE }
        assertEquals(listOf(Step.CONTACTS, Step.NOTIFICATIONS, Step.RECEIVE_SMS, Step.OVERLAY, Step.FULL_SCREEN, Step.BATTERY), all)
        assertFalse(all.any { it in listOf(Step.SEND_SMS, Step.LOCATION, Step.BACKGROUND_LOCATION) })
    }

    @Test fun launchIsGuardedUntilCallbackAndDenialDoesNotAdvance() {
        val flow = PermissionFlow(36, PermissionFlow.GROUP_C)
        assertTrue(flow.begin())
        assertFalse(flow.begin())
        flow.result(false)
        assertEquals(Step.RECEIVE_SMS, flow.step)
        assertTrue(flow.begin())
        flow.result(true)
        assertEquals(Step.COMPLETE, flow.step)
    }

    @Test fun oldAndroidOmitsUnsupportedSetupSteps() {
        val a = PermissionFlow(26, PermissionFlow.GROUP_A)
        a.advance()
        assertEquals(Step.COMPLETE, a.step)      // no notifications step below 33
        val b = PermissionFlow(26, PermissionFlow.GROUP_B)
        b.advance()
        assertEquals(Step.BATTERY, b.step)       // no full-screen step below 34
        b.advance()
        assertEquals(Step.COMPLETE, b.step)
    }

    @Test fun locationAndSmsWalks() {
        val flow = PermissionFlow(28, PermissionFlow.LOCATION)
        assertEquals(Step.LOCATION, flow.step)
        flow.begin(); flow.result(true)
        assertEquals(Step.COMPLETE, flow.step)   // background step irrelevant on 28
        val modern = PermissionFlow(34, PermissionFlow.LOCATION, Step.LOCATION)
        modern.begin(); modern.result(true)
        assertEquals(Step.BACKGROUND_LOCATION, modern.step)
        val sms = PermissionFlow(34, PermissionFlow.SMS)
        assertEquals(Step.SEND_SMS, sms.step)
        sms.begin(); sms.result(true)
        assertEquals(Step.COMPLETE, sms.step)
    }

    @Test fun restoredInFlightStateDoesNotLaunchAgain() {
        val restored = PermissionFlow(34, PermissionFlow.GROUP_B, Step.OVERLAY, true)
        assertFalse(restored.begin())
        restored.result(true)
        assertEquals(Step.FULL_SCREEN, restored.step)
        assertFalse(restored.waiting)
    }
}
