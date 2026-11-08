package com.fcapps.bff

/** Decides what an incoming text asks for. No Android dependencies, so it is unit-testable. */
object SmsRules {

    enum class AlarmBlock { NONE, NO_MATCH, SILENT, RULE, PIN }

    data class Decision(val matched: Boolean, val alarm: Boolean, val location: Boolean, val whereRequested: Boolean = false, val alarmBlock: AlarmBlock = AlarmBlock.NO_MATCH) {
        val status: String get() = when {
            alarm && location -> Prefs.STATUS_BOTH
            alarm -> Prefs.STATUS_SUCCESS
            location -> Prefs.STATUS_LOCATED
            else -> Prefs.STATUS_BLOCKED
        }
    }

    private val WHERE = Regex("\\bwhere\\b", RegexOption.IGNORE_CASE)
    private val SILENT = Regex("\\bsilent\\b", RegexOption.IGNORE_CASE)

    private fun token(text: String) = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(text)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)

    fun matchesActivation(body: String, word: String, pin: String): Boolean =
        word.isNotBlank() && (token(word).containsMatchIn(body) ||
            (pin.isNotEmpty() && token(word + pin).containsMatchIn(body)))

    /** PIN is a complete field immediately after the activation word; legacy BFF1234 is accepted. */
    fun decide(body: String, word: String, pin: String, alarmGate: String, locGate: String, locationEnabled: Boolean): Decision {
        if (!matchesActivation(body, word, pin)) return Decision(false, false, false)
        val hasWhere = WHERE.containsMatchIn(body)
        val separatedPin = if (pin.isNotEmpty()) Regex("^\\s+${Regex.escape(pin)}(?![\\p{L}\\p{N}_])") else null
        val pinOk = pin.isNotEmpty() && (token(word + pin).containsMatchIn(body) ||
            token(word).findAll(body).any { separatedPin!!.containsMatchIn(body.substring(it.range.last + 1)) })
        fun pass(gate: String) = gate == Prefs.MATRIX_YES || (gate == Prefs.MATRIX_PIN && pinOk)
        val alarm = !SILENT.containsMatchIn(body) && pass(alarmGate)
        val location = hasWhere && locationEnabled && pass(locGate)
        val block = when {
            alarm -> AlarmBlock.NONE
            SILENT.containsMatchIn(body) -> AlarmBlock.SILENT
            alarmGate == Prefs.MATRIX_PIN -> AlarmBlock.PIN
            else -> AlarmBlock.RULE
        }
        return Decision(true, alarm, location, hasWhere, block)
    }
}
