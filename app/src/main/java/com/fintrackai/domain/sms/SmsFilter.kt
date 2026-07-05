package com.fintrackai.domain.sms

import com.fintrackai.domain.model.SmsMessage

object SmsFilter {
    fun filterTransactionSMS(messages: List<SmsMessage>): List<SmsMessage> {
        return messages.filter { sms ->
            // Gate on sender first — eliminates the majority of inbox messages with a cheap set lookup.
            if (!SmsTransactionalSenderCodes.isAllowedSender(sms.address)) return@filter false
            val bodyLower = sms.body.lowercase()
            // Plain string checks before regex — cheap exclusions first.
            if (SmsConstants.OTP_KEYWORDS.any { kw -> bodyLower.contains(kw) }) return@filter false
            if (SmsConstants.FAILED_TRANSACTION_KEYWORDS.any { kw -> bodyLower.contains(kw) }) return@filter false
            // Must contain a transaction keyword before running any remaining regex.
            if (!SmsConstants.TRANSACTION_KEYWORD_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            // Regex exclusion checks — only reached for sender-allowed, transaction-containing messages.
            if (SmsConstants.REVERSED_TRANSACTION_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            if (SmsConstants.EMI_CONVERSION_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            if (SmsConstants.CREDIT_CARD_STATEMENT_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            if (SmsConstants.PENDING_TRANSACTION_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            if (SmsConstants.FRAUD_CONSENT_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            if (SmsConstants.PROMOTIONAL_PATTERNS.any { it.containsMatchIn(sms.body) }) return@filter false
            true
        }
    }
}
