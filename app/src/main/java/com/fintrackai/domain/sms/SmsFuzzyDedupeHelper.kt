package com.fintrackai.domain.sms

object SmsFuzzyDedupeHelper {

    fun batchKey(smsSender: String?, date: String, amount: Double, type: String): String =
        listOf(smsSender ?: "", date, amount.toString(), type)
            .joinToString(SmsFuzzyDedupeConstants.BATCH_KEY_SEPARATOR)

    /**
     * Whether two normalized SMS bodies likely describe the same inbound message
     * (e.g. multipart fragment vs full text, or later line appended by the bank).
     */
    fun isLikelySameSmsBody(normalizedA: String, normalizedB: String): Boolean {
        if (normalizedA == normalizedB) return true
        val a = normalizedA
        val b = normalizedB
        val shorter: String
        val longer: String
        if (a.length <= b.length) {
            shorter = a
            longer = b
        } else {
            shorter = b
            longer = a
        }
        if (shorter.length < SmsFuzzyDedupeConstants.MIN_SHORTER_NORMALIZED_LENGTH) return false
        return longer.startsWith(shorter)
    }
}
