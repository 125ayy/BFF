package com.fcapps.bff

import org.junit.Assert.*
import org.junit.Test

class LocationRequestsTest {
    @Test fun overlappingSendersKeepRecipientsAndDeadlines() {
        val requests = LocationRequests()
        requests.add("Alice", 0)
        requests.add("Bob", 5_000)
        assertTrue(requests.due(9_999).isEmpty())
        assertEquals(listOf(LocationRequests.Reply("Alice", false)), requests.due(10_000))
        assertEquals(listOf(LocationRequests.Reply("Bob", false)), requests.due(15_000))
        assertEquals(listOf(LocationRequests.Reply("Alice", true)), requests.due(60_000))
        assertFalse(requests.isEmpty)
        assertEquals(listOf(LocationRequests.Reply("Bob", true)), requests.due(65_000))
        assertTrue(requests.isEmpty)
        assertTrue(requests.due(90_000).isEmpty())
    }

    @Test fun duplicateSenderDoesNotCreateExtraReplies() {
        val requests = LocationRequests()
        assertTrue(requests.add("Alice", 0))
        assertFalse(requests.add("Alice", 5_000))
        assertEquals(1, requests.due(10_000).size)
        assertFalse(requests.add("Alice", 20_000))
        assertEquals(1, requests.due(60_000).size)
        assertTrue(requests.add("Alice", 61_000))
    }

    @Test fun cancellationPreventsAllReplies() {
        val requests = LocationRequests()
        requests.add("Alice", 0)
        requests.clear()
        assertTrue(requests.due(60_000).isEmpty())
    }

    @Test fun staleAndFutureFixesAreRejected() {
        val stale = LocationFix(0.0, 0.0, 1f, 0)
        val future = stale.copy(elapsedMs = 200_000)
        assertNull(LocationFix.best(listOf(stale, future), 150_000))
    }

    @Test fun qualityWinsAmongFreshFixes() {
        val gps = LocationFix(0.0, 0.0, 5f, 100_000)
        val network = gps.copy(accuracy = 1000f, elapsedMs = 101_000)
        assertEquals(gps, LocationFix.best(listOf(gps, network), 110_000))
        assertEquals(network, LocationFix.best(listOf(gps, network), 220_001))
    }
}
