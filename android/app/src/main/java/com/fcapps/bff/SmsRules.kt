package com.fcapps.bff

/** Decides what an incoming text asks for. No Android dependencies, so it is unit-testable. */
object SmsRules {

    data class Decision(val matched: Boolean, val alarm: Boolean, val location: Boolean) {
        val status: String get() = when {
            alarm && location -> Prefs.STATUS_BOTH
            alarm -> Prefs.STATUS_SUCCESS
            location -> Prefs.STATUS_LOCATED
            else -> Prefs.STATUS_BLOCKED
        }
    }

    private val WHERE = Regex("\\bwhere\\b", RegexOption.IGNORE_CASE)
    private val SILENT = Regex("\\bsilent\\b", RegexOption.IGNORE_CASE)

    /** word: activation word, matched anywhere, case-insensitive. pin: empty = none, else must appear anywhere. */
    fun decide(body: String, word: String, pin: String, alarmGate: String, locGate: String, locationEnabled: Boolean): Decision {
        if (word.isBlank() || !body.contains(word, ignoreCase = true)) return Decision(false, false, false)
        val pinOk = pin.isEmpty() || body.contains(pin)
        fun pass(gate: String) = gate == Prefs.MATRIX_YES || (gate == Prefs.MATRIX_PIN && pinOk)
        val alarm = !SILENT.containsMatchIn(body) && pass(alarmGate)
        val location = WHERE.containsMatchIn(body) && locationEnabled && pass(locGate)
        return Decision(true, alarm, location)
    }
}
