package com.fcapps.bff

/** Each sender has independent deadlines. Duplicate requests are coalesced until completion. */
class LocationRequests {
    data class Request(val recipient: String, val startedMs: Long, var firstSent: Boolean = false)
    data class Reply(val recipient: String, val final: Boolean)
    private val requests = linkedMapOf<String, Request>()
    val isEmpty: Boolean get() = requests.isEmpty()

    fun add(recipient: String, nowMs: Long): Boolean {
        if (requests.containsKey(recipient)) return false
        requests[recipient] = Request(recipient, nowMs)
        return true
    }

    fun due(nowMs: Long): List<Reply> {
        val replies = mutableListOf<Reply>()
        val iterator = requests.values.iterator()
        while (iterator.hasNext()) {
            val request = iterator.next()
            val age = nowMs - request.startedMs
            if (!request.firstSent && age >= 10_000) {
                request.firstSent = true
                replies += Reply(request.recipient, false)
            }
            if (age >= 60_000) {
                replies += Reply(request.recipient, true)
                iterator.remove()
            }
        }
        return replies
    }

    fun clear() = requests.clear()
}

/** All ages use the elapsed realtime clock, unaffected by wall-clock changes. */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracy: Float, val elapsedMs: Long) {
    companion object {
        const val MAX_AGE_MS = 120_000L
        fun best(fixes: Collection<LocationFix>, nowMs: Long): LocationFix? = fixes
            .filter { nowMs - it.elapsedMs in 0..MAX_AGE_MS && it.accuracy.isFinite() && it.accuracy >= 0 }
            .minWithOrNull(compareBy<LocationFix> { it.accuracy }.thenByDescending { it.elapsedMs })
    }
}
