package com.fcapps.bff

/** Some carrier configurations deny SIM metadata during Unicode division. */
data class SmsReplyPlan(val parts: ArrayList<String>, val individual: Boolean) {
    companion object {
        fun prepare(text: String, divide: (String) -> ArrayList<String>): SmsReplyPlan = try {
            SmsReplyPlan(divide(text), false)
        } catch (_: SecurityException) {
            // Standalone UCS-2 SMS holds 70 UTF-16 units. Avoid multipart APIs,
            // which may repeat the same denied carrier lookup. Prefer word boundaries
            // so the map URL remains clickable. Never cut a surrogate pair.
            val prefix = if (SmsRules.isAnswer(text)) "${SmsRules.ANSWER_PREFIX} " else ""
            val payload = if (prefix.isNotEmpty()) text.trimStart().substring(SmsRules.ANSWER_PREFIX.length).trimStart() else text
            val limit = 70 - prefix.length
            val parts = arrayListOf<String>()
            var start = 0
            while (start < payload.length) {
                var end = minOf(start + limit, payload.length)
                if (end < payload.length) {
                    if (payload[end - 1].isHighSurrogate()) end--
                    val space = payload.lastIndexOf(' ', end - 1)
                    if (space >= start) end = space + 1
                }
                parts.add(prefix + payload.substring(start, end))
                start = end
            }
            SmsReplyPlan(parts, true)
        }
    }
}
