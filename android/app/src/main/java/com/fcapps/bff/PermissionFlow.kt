package com.fcapps.bff

/** Pure ordering/state: Android owns grants; this flow never treats a launch as a grant. */
class PermissionFlow(
    val sdk: Int,
    var step: Step = Step.RECEIVE_SMS,
    var locationWanted: Boolean = true,
    var waiting: Boolean = false
) {
    enum class Step { RECEIVE_SMS, SEND_SMS, CONTACTS, NOTIFICATIONS, LOCATION, BACKGROUND_LOCATION, OVERLAY, FULL_SCREEN, BATTERY, COMPLETE }

    fun relevant(step: Step): Boolean = when (step) {
        Step.NOTIFICATIONS -> sdk >= 33
        Step.LOCATION -> locationWanted
        Step.BACKGROUND_LOCATION -> locationWanted && sdk >= 29
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

    fun skip(): Boolean {
        if (waiting || step == Step.RECEIVE_SMS || step == Step.COMPLETE) return false
        if (step in listOf(Step.SEND_SMS, Step.LOCATION, Step.BACKGROUND_LOCATION)) locationWanted = false
        advance()
        return true
    }

    fun advance() {
        if (step != Step.COMPLETE) step = Step.entries[step.ordinal + 1]
        while (!relevant(step)) step = Step.entries[step.ordinal + 1]
    }
}
