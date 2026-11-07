package com.fcapps.bff

/**
 * Pure ordering/state for a staged permission walk. Android owns grants; this flow never
 * treats a launch as a grant. The same machine drives initial setup (SETUP) and the
 * Settings › Location switch (LOCATION), each with its own step list.
 */
class PermissionFlow(
    val sdk: Int,
    val steps: List<Step> = SETUP,
    var step: Step = steps.first(),
    var waiting: Boolean = false
) {
    enum class Step { RECEIVE_SMS, CONTACTS, NOTIFICATIONS, OVERLAY, FULL_SCREEN, BATTERY, SEND_SMS, LOCATION, BACKGROUND_LOCATION, COMPLETE }

    companion object {
        /** Initial setup: what the siren needs. Location lives in Settings › Location (v3.11). */
        val SETUP = listOf(Step.RECEIVE_SMS, Step.CONTACTS, Step.NOTIFICATIONS, Step.OVERLAY, Step.FULL_SCREEN, Step.BATTERY, Step.COMPLETE)
        /** Settings › Location › "Location" switch. */
        val LOCATION = listOf(Step.LOCATION, Step.BACKGROUND_LOCATION, Step.COMPLETE)
        /** Settings › Location › "Send SMS" switch. */
        val SMS = listOf(Step.SEND_SMS, Step.COMPLETE)
    }

    fun relevant(step: Step): Boolean = when (step) {
        Step.NOTIFICATIONS -> sdk >= 33
        Step.BACKGROUND_LOCATION -> sdk >= 29
        Step.FULL_SCREEN -> sdk >= 34
        else -> true
    }

    /** Required steps cannot be skipped: receiving SMS in setup; every step of the switch walks. */
    fun required(step: Step): Boolean = step == Step.RECEIVE_SMS || steps !== SETUP

    fun begin(): Boolean {
        if (waiting || step == Step.COMPLETE) return false
        waiting = true
        return true
    }

    fun result(granted: Boolean) {
        waiting = false
        if (granted) advance()
    }

    fun skip(): Boolean {
        if (waiting || step == Step.COMPLETE || required(step)) return false
        advance()
        return true
    }

    fun advance() {
        var i = steps.indexOf(step)
        if (i < 0) { step = Step.COMPLETE; return }
        do { i = minOf(i + 1, steps.lastIndex); step = steps[i] } while (step != Step.COMPLETE && !relevant(step))
    }
}
