package com.fintrackai.domain.sms

import java.security.MessageDigest

private const val SENDER_BODY_SEPARATOR = "\u0000"

object SmsDedupeHashHelper {

    fun normalizeBody(body: String): String =
        body.trim().replace(Regex("\\s+"), " ")

    /** Stable fingerprint: same inbound SMS always maps to the same hash. */
    fun contentHash(sender: String?, body: String): String {
        val s = (sender ?: "").trim().lowercase()
        val b = normalizeBody(body)
        val payload = "$s$SENDER_BODY_SEPARATOR$b"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
