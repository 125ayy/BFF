package com.fcapps.bff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRulesTest {
    private val Y = Prefs.MATRIX_YES; private val P = Prefs.MATRIX_PIN; private val N = Prefs.MATRIX_NO

    @Test fun noWord_noMatch() {
        val d = SmsRules.decide("hello there", "BFF", "", Y, Y, true)
        assertFalse(d.matched); assertFalse(d.alarm); assertFalse(d.location)
    }

    @Test fun word_yes_alarmOnly() {
        val d = SmsRules.decide("BFF", "BFF", "", Y, Y, true)
        assertTrue(d.alarm); assertFalse(d.location); assertEquals(Prefs.STATUS_SUCCESS, d.status)
    }

    @Test fun where_addsLocation() {
        val d = SmsRules.decide("BFF where are you", "BFF", "", Y, Y, true)
        assertTrue(d.alarm); assertTrue(d.location); assertEquals(Prefs.STATUS_BOTH, d.status)
    }

    @Test fun silentWhere_locationOnly() {
        val d = SmsRules.decide("BFF silent where", "BFF", "", Y, Y, true)
        assertFalse(d.alarm); assertTrue(d.location); assertEquals(Prefs.STATUS_LOCATED, d.status)
    }

    @Test fun pinGate_blocksWithoutPin() {
        val d = SmsRules.decide("BFF", "BFF", "1234", P, P, true)
        assertFalse(d.alarm); assertEquals(Prefs.STATUS_BLOCKED, d.status)
    }

    @Test fun pinGate_passesWithPin() {
        val d = SmsRules.decide("BFF 1234 where", "BFF", "1234", P, P, true)
        assertTrue(d.alarm); assertTrue(d.location)
    }

    @Test fun locationOff_noLocation() {
        val d = SmsRules.decide("BFF where", "BFF", "", Y, Y, false)
        assertTrue(d.alarm); assertFalse(d.location)
    }

    @Test fun no_blocks() {
        val d = SmsRules.decide("BFF where", "BFF", "", N, N, true)
        assertFalse(d.alarm); assertFalse(d.location); assertEquals(Prefs.STATUS_BLOCKED, d.status)
    }

    @Test fun caseInsensitiveWord() {
        assertTrue(SmsRules.decide("please bff now", "BFF", "", Y, Y, true).alarm)
    }
}
