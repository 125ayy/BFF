package com.fcapps.bff

import com.fcapps.bff.PermissionFlow.Step
import org.junit.Assert.*
import org.junit.Test

class PermissionFlowTest {
    @Test fun launchIsGuardedUntilCallbackAndDenialDoesNotAdvance() {
        val flow = PermissionFlow(36)
        assertTrue(flow.begin())
        assertFalse(flow.begin())
        assertFalse(flow.skip())
        flow.result(false)
        assertEquals(Step.RECEIVE_SMS, flow.step)
        assertFalse(flow.skip())
        assertTrue(flow.begin())
        flow.result(true)
        assertEquals(Step.SEND_SMS, flow.step)
    }

    @Test fun optionalDenialWaitsForUserChoice() {
        val flow = PermissionFlow(36, Step.CONTACTS)
        flow.begin(); flow.result(false)
        assertEquals(Step.CONTACTS, flow.step)
        assertTrue(flow.skip())
        assertEquals(Step.NOTIFICATIONS, flow.step)
    }

    @Test fun skippingSmsSendingSkipsLocationPermissions() {
        val flow = PermissionFlow(36, Step.SEND_SMS)
        flow.skip()
        assertFalse(flow.locationWanted)
        flow.advance() // contacts -> notifications
        flow.advance() // skips both location cards
        assertEquals(Step.OVERLAY, flow.step)
    }

    @Test fun oldAndroidOmitsUnsupportedPermissions() {
        val flow = PermissionFlow(26, Step.CONTACTS)
        flow.advance()
        assertEquals(Step.LOCATION, flow.step)
        flow.advance()
        assertEquals(Step.OVERLAY, flow.step)
        flow.advance()
        assertEquals(Step.BATTERY, flow.step)
    }

    @Test fun restoredInFlightStateDoesNotLaunchAgain() {
        val restored = PermissionFlow(34, Step.BACKGROUND_LOCATION, true, true)
        assertFalse(restored.begin())
        restored.result(true)
        assertEquals(Step.OVERLAY, restored.step)
        assertFalse(restored.waiting)
    }
}
