package com.fintrackai.domain.sms

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses bank balance SMS messages to extract available balance, account last-4 digits,
 * and the date the balance was reported.
 *
 * Example inputs:
 *  - "Available Bal in HDFC Bank A/c XX5988 as on yesterday:28-MAR-26 is INR 12,36,140.03. Cheques..."
 *  - "Avl Bal: INR 1,23,456.78 in A/c XX1234 as on 28-MAR-26"
 *  - "Your A/c XX9876 has Avail Bal of Rs.50,000.00 as on 01-Apr-2026"
 */
object BalanceSmsParser {

    data class BalanceSmsResult(
        val last4Digits: String,
        val availableBalance: Double,
        /** ISO date string YYYY-MM-DD */
        val balanceDate: String,
        val balanceTimestamp: Long
    )

    private val BALANCE_KEYWORDS = listOf(
        "available bal", "avl bal", "avail bal", "available balance", "avl balance", "avail balance",
        "acbal:"
    )

    private val AMOUNT_PATTERN = Regex(
        """(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)|acbal:\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_PATTERN = Regex(
        """[Aa]/[Cc]\s*(?:no\.?)?\s*[Xx*]{0,4}(\d{4})"""
    )

    // Matches: 28-MAR-26, 28-Mar-2026, 01-Apr-26, 28/03/2026, 28-03-2026
    private val DATE_PATTERNS = listOf(
        Regex("""(\d{1,2})[/-]([A-Za-z]{3})[/-](\d{2,4})"""),
        Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})""")
    )

    private val MONTH_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    fun isBalanceSms(body: String): Boolean {
        val lower = body.lowercase()
        return BALANCE_KEYWORDS.any { lower.contains(it) }
    }

    fun parse(body: String, smsTimestamp: Long): BalanceSmsResult? {
        if (!isBalanceSms(body)) return null

        val last4 = ACCOUNT_PATTERN.find(body)?.groupValues?.get(1) ?: return null
        val amountMatch = AMOUNT_PATTERN.find(body) ?: return null
        val balanceStr = amountMatch.groupValues[1].ifEmpty { amountMatch.groupValues[2] }.ifEmpty { return null }
        val balance = balanceStr.replace(",", "").toDoubleOrNull() ?: return null

        val dateStr = extractDate(body) ?: run {
            // Fallback: use SMS timestamp as date
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.format(java.util.Date(smsTimestamp))
        }

        val balanceTimestamp = parseDateToMillis(dateStr) ?: smsTimestamp

        return BalanceSmsResult(
            last4Digits = last4,
            availableBalance = balance,
            balanceDate = dateStr,
            balanceTimestamp = balanceTimestamp
        )
    }

    private fun extractDate(body: String): String? {
        // Named-month pattern: 28-MAR-26
        DATE_PATTERNS[0].find(body)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let null
            val monthStr = m.groupValues[2].lowercase()
            val month = MONTH_MAP[monthStr] ?: return@let null
            val yearRaw = m.groupValues[3].toIntOrNull() ?: return@let null
            val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
            return "%04d-%02d-%02d".format(year, month, day)
        }
        // Numeric pattern: 28/03/2026 or 28-03-26
        DATE_PATTERNS[1].find(body)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let null
            val month = m.groupValues[2].toIntOrNull() ?: return@let null
            val yearRaw = m.groupValues[3].toIntOrNull() ?: return@let null
            val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
            if (month in 1..12 && day in 1..31) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }
        return null
    }

    private fun parseDateToMillis(isoDate: String): Long? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time
        } catch (e: Exception) {
            null
        }
    }
}
