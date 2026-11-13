package com.fcapps.bff

/**
 * Pure ordering/state for a staged permission walk. Android owns grants; this flow never
 * treats a launch as a grant. The same machine drives initial setup (SETUP) and the
 * Settings › Location switch (LOCATION), each with its own step list.
 */
class PermissionFlow(
    val sdk: Int,
    val steps: List<Step> = GROUP_A,
    var step: Step = steps.first(),
    var waiting: Boolean = false
) {
    enum class Step { RECEIVE_SMS, CONTACTS, NOTIFICATIONS, OVERLAY, FULL_SCREEN, BATTERY, SEND_SMS, LOCATION, BACKGROUND_LOCATION, COMPLETE }

    companion object {
        /** Setup step 1 of 3: one-tap prompts, a quick first success. */
        val GROUP_A = listOf(Step.CONTACTS, Step.NOTIFICATIONS, Step.COMPLETE)
        /** Setup step 2 of 3: Settings toggles that let the alarm ring on a locked phone. Required. */
        val GROUP_B = listOf(Step.OVERLAY, Step.FULL_SCREEN, Step.BATTERY, Step.COMPLETE)
        /** Setup step 3 of 3: the SMS trigger itself, behind a warning screen. Required. */
        val GROUP_C = listOf(Step.RECEIVE_SMS, Step.COMPLETE)
        /** Every setup group, in order; location and SMS sending live in Settings › Location. */
        val SETUP_GROUPS = listOf(GROUP_A, GROUP_B, GROUP_C)
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

    fun begin(): Boolean {
        if (waiting || step == Step.COMPLETE) return false
        waiting = true
        return true
    }

    fun result(granted: Boolean) {
        waiting = false
        if (granted) advance()
    }

    fun advance() {
        var i = steps.indexOf(step)
        if (i < 0) { step = Step.COMPLETE; return }
        do { i = minOf(i + 1, steps.lastIndex); step = steps[i] } while (step != Step.COMPLETE && !relevant(step))
    }
}
