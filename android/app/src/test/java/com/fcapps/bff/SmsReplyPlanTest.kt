package com.fcapps.bff

import org.junit.Assert.*
import org.junit.Test

class SmsReplyPlanTest {
    @Test fun deniedCarrierLookupFallsBackWithoutLosingUnicodeOrMapLink() {
        val text = "BFF: localização https://maps.google.com/?q=12.345678,-123.456789 (±10 m). " + "😀".repeat(40)
        val plan = SmsReplyPlan.prepare(text) { throw SecurityException("getGroupIdLevel1") }
        assertTrue(plan.individual)
        assertTrue(plan.parts.any { it.contains("https://maps.google.com/?q=12.345678,-123.456789") })
        assertEquals(text, plan.parts.joinToString(""))
        assertTrue(plan.parts.all { it.length <= 70 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
    }
    @Test fun normalPlatformDivisionIsPreserved() {
        val plan = SmsReplyPlan.prepare("hello") { arrayListOf("hello") }
        assertFalse(plan.individual)
        assertEquals(listOf("hello"), plan.parts)
    }
    @Test fun shortUnicodeFitsOneFallbackMessage() {
        val plan = SmsReplyPlan.prepare("BFF: ±10 m") { throw SecurityException() }
        assertEquals(listOf("BFF: ±10 m"), plan.parts)
    }
    @Test fun everyStandaloneReplyPartHasIgnorePrefix() {
        val payload = "Recent location https://maps.google.com/?q=12.345678,-123.456789 BFF where permissions failed"
        val plan = SmsReplyPlan.prepare("BFF-Answer: $payload") { throw SecurityException() }
        assertTrue(plan.parts.size > 1)
        assertTrue(plan.parts.all { it.length <= 70 && SmsRules.isAnswer(it) })
        assertTrue(plan.parts.none { SmsRules.matchesActivation(it, "BFF", "") })
        assertEquals(payload, plan.parts.joinToString("") { it.removePrefix("BFF-Answer: ") })
        assertTrue(plan.parts.any { it.contains("https://maps.google.com/?q=12.345678,-123.456789") })
    }

}
