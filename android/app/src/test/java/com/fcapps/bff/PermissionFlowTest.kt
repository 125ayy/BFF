package com.fcapps.bff

import com.fcapps.bff.PermissionFlow.Step
import org.junit.Assert.*
import org.junit.Test

class PermissionFlowTest {
    @Test fun setupHasNoLocationSteps() {
        assertFalse(PermissionFlow.SETUP.any { it in listOf(Step.SEND_SMS, Step.LOCATION, Step.BACKGROUND_LOCATION) })
        assertEquals(Step.RECEIVE_SMS, PermissionFlow.SETUP.first())
    }

    @Test fun launchIsGuardedUntilCallbackAndDenialDoesNotAdvance() {
        val flow = PermissionFlow(36)
        assertTrue(flow.begin())
        assertFalse(flow.begin())
        assertFalse(flow.skip())
        flow.result(false)
        assertEquals(Step.RECEIVE_SMS, flow.step)
        assertTrue(flow.begin())
        flow.result(true)
        assertEquals(Step.CONTACTS, flow.step)
    }

    @Test fun optionalDenialWaitsForUserChoice() {
        val flow = PermissionFlow(36, PermissionFlow.SETUP, Step.CONTACTS)
        flow.begin(); flow.result(false)
        assertEquals(Step.CONTACTS, flow.step)
        assertTrue(flow.skip())
        assertEquals(Step.NOTIFICATIONS, flow.step)
    }

    @Test fun oldAndroidOmitsUnsupportedSetupSteps() {
        val flow = PermissionFlow(26, PermissionFlow.SETUP, Step.CONTACTS)
        flow.advance()
        assertEquals(Step.OVERLAY, flow.step)   // no notifications step below 33
        flow.advance()
        assertEquals(Step.BATTERY, flow.step)   // no full-screen step below 34
        flow.advance()
        assertEquals(Step.COMPLETE, flow.step)
    }

    @Test fun locationWalkIsAllRequiredAndSkipsBackgroundBelow29() {
        val flow = PermissionFlow(28, PermissionFlow.LOCATION)
        assertEquals(Step.LOCATION, flow.step)
        assertFalse(flow.skip())                 // nothing in the switch walks is skippable
        flow.begin(); flow.result(true)
        assertEquals(Step.COMPLETE, flow.step)   // background step irrelevant on 28
        val modern = PermissionFlow(34, PermissionFlow.LOCATION, Step.LOCATION)
        modern.begin(); modern.result(true)
        assertEquals(Step.BACKGROUND_LOCATION, modern.step)
        val sms = PermissionFlow(34, PermissionFlow.SMS)
        assertEquals(Step.SEND_SMS, sms.step)
        assertFalse(sms.skip())
        sms.begin(); sms.result(true)
        assertEquals(Step.COMPLETE, sms.step)
    }

    @Test fun restoredInFlightStateDoesNotLaunchAgain() {
        val restored = PermissionFlow(34, PermissionFlow.SETUP, Step.OVERLAY, true)
        assertFalse(restored.begin())
        restored.result(true)
        assertEquals(Step.FULL_SCREEN, restored.step)
        assertFalse(restored.waiting)
    }
}
