package com.fintrackai.domain.sms

import com.fintrackai.data.local.db.SmsPatternEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ExtractedTransaction(
    val type: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val date: String? = null,
    val fullAccount: String? = null,
    val merchant: String? = null,
    val reference: String? = null,
    val category: String? = null,
    val accounts: String? = null,
    val patternIndex: Int? = null,
    val balance: Double? = null,
    val creditLimit: Double? = null
)

object TransactionExtractor {

    private const val CURRENCY_AMOUNT =
        """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)"""

    private val SUPPORTED_CURRENCIES = listOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "SGD", "AED")

    private val MONTH_NUMBERS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun daysInMonth(month: Int, year: Int): Int {
        val days = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (month == 2 && isLeapYear(year)) 29 else days[month - 1]
    }

    fun extract4DigitNumbers(text: String): List<String> {
        val highPriority = mutableListOf<String>()
        val mediumPriority = mutableListOf<String>()
        val lowPriority = mutableListOf<String>()

        for (m in CARD_KEYWORD_PATTERN.findAll(text)) {
            val raw = m.groupValues[1]
            // For long card numbers like 817430XXXXXX9989 or XXXXXX9989, take last 4 digits
            val fourDigits = raw.filter { it.isDigit() }.takeLast(4)
            if (fourDigits.length != 4) continue
            val num = fourDigits.toIntOrNull() ?: continue
            if (num in 1000..9999) highPriority.add(fourDigits)
        }

        for (dm in ANY_4_DIGIT_PATTERN.findAll(text)) {
            val fourDigits = dm.groupValues[1]
            val num = fourDigits.toIntOrNull() ?: continue
            if (fourDigits in highPriority) continue

            val startIdx = dm.range.first
            val endIdx = dm.range.last + 1
            val before = text.substring(maxOf(0, startIdx - 15), startIdx)
            val after = text.substring(endIdx, minOf(text.length, endIdx + 15))
            val context = before + dm.value + after
            val hasAccountKeyword = ACCOUNT_KEYWORD_CONTEXT_PATTERN.containsMatchIn(context)

            if (num in 1..31 && DATE_CONTEXT_PATTERN.containsMatchIn(before + after)) continue
            if (PHONE_CONTEXT_PATTERN.containsMatchIn(context)) continue
            if (LONG_DIGIT_PATTERN.containsMatchIn(context)) continue
            if (num == 1800 || num == 1900 || (num in 1800..1999 && TOLL_HOTLINE_CONTEXT_PATTERN.containsMatchIn(before))) continue
            if (DECIMAL_CONTEXT_PATTERN.containsMatchIn(after) && !hasAccountKeyword) continue

            if (num in 1000..9999) {
                if (hasAccountKeyword) {
                    if (fourDigits !in highPriority && fourDigits !in mediumPriority) {
                        mediumPriority.add(fourDigits)
                    }
                } else {
                    if (fourDigits !in highPriority && fourDigits !in mediumPriority && fourDigits !in lowPriority) {
                        lowPriority.add(fourDigits)
                    }
                }
            }
        }

        for (m in MASKED_4_DIGIT_PATTERN.findAll(text)) {
            val fourDigits = m.groupValues[1]
            val num = fourDigits.toIntOrNull() ?: continue
            if (num in 1000..9999) {
                val startIdx = m.range.first
                val endIdx = m.range.last + 1
                val before = text.substring(maxOf(0, startIdx - 15), startIdx)
                val after = text.substring(endIdx, minOf(text.length, endIdx + 15))
                val fullContext = before + m.value + after
                if (!MASKED_PHONE_CONTEXT_PATTERN.containsMatchIn(fullContext) &&
                    !LONG_DIGIT_PATTERN.containsMatchIn(fullContext)
                ) {
                    if (fourDigits !in highPriority && fourDigits !in mediumPriority) {
                        mediumPriority.add(fourDigits)
                    }
                }
            }
        }

        return highPriority + mediumPriority + lowPriority
    }

    fun extractBankName(text: String, senderAddress: String? = null): String? {
        if (senderAddress != null) {
            val senderUpper = senderAddress.uppercase()
            for (bank in SmsConstants.BANK_NAMES) {
                if (senderUpper.contains(bank)) return bank
            }
        }

        val match1 = BANK_PATTERN_1.find(text)
        if (match1 != null) {
            val bankText = match1.groupValues[1]
            if (bankText.length > 4 && bankText.uppercase().endsWith("BANK")) {
                return bankText.replace(BANK_SUFFIX_PATTERN, "").uppercase()
            }
            return bankText.uppercase()
        }

        val match2 = BANK_PATTERN_2.find(text)
        if (match2 != null) return match2.groupValues[1].uppercase()

        for ((bank, pattern) in BANK_NAME_PATTERNS) {
            if (pattern.containsMatchIn(text)) return bank
        }

        return null
    }

    fun cleanCardNumber(cardNumber: String?): String? {
        if (cardNumber == null) return null
        val digits = cardNumber.replace(NON_DIGIT_PATTERN, "")
        return digits.ifEmpty { cardNumber }
    }

    fun cleanAccountName(account: String?): String? {
        if (account == null) return null
        var cleaned = account
        cleaned = cleaned.replace(ACCOUNT_LABEL_NOISE_PATTERN, "")
        cleaned = cleaned.replace(CREDIT_CARD_LABEL_PATTERN, "Card")
        cleaned = cleaned.replace(CREDIT_WORD_PATTERN, "")
        cleaned = cleaned.replace(MASKED_ACCOUNT_NUMBER_PATTERN) { it.groupValues[1] }
        cleaned = cleaned.replace(WHITESPACE_PATTERN, " ").trim()
        return cleaned
    }

    fun cleanMerchantName(merchant: String?): String? {
        if (merchant == null) return null
        var cleaned = merchant.trim()
        for (suffix in SmsConstants.MERCHANT_SUFFIXES) {
            cleaned = cleaned.replace(suffix, "")
        }
        // Strip masked account numbers (xx1234, **1234, XX5648) and standalone long digit strings (UTR/ref numbers)
        cleaned = cleaned.replace(MASKED_ACCOUNT_IN_MERCHANT_PATTERN, " ")
        cleaned = cleaned.replace(LONG_DIGIT_IN_MERCHANT_PATTERN, " ")
        cleaned = cleaned.trim().replace(MULTI_SPACE_PATTERN, " ")
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.lowercase().split(WORD_SPLIT_PATTERN).joinToString(" ") { word ->
                if (word.isNotEmpty()) word.replaceFirstChar { it.uppercase() } else word
            }
            // Restore bank names and payment modes to uppercase — title-casing turns "HDFC" into "Hdfc", etc.
            for ((word, pattern) in BANK_WORD_PATTERNS) {
                cleaned = pattern.replace(cleaned) { word }
            }
        }
        return cleaned.ifEmpty { merchant }
    }

    private val BALANCE_PATTERN = Regex(
        """(?:acbal[:\s]*([\d,]+(?:\.\d{1,2})?))|(?:(?<!(?:min(?:imum)?|maintain|required|keep)\s)(?:Avl\.?\s*Bal(?:ance)?|(?:\w+\s+){0,2}[Aa]vailable\s+[Bb]al(?:ance)?|A(?:/c|cc)?\s*Bal(?:ance)?|(?:Final\s+)?[Bb]al(?:ance)?)\s*(?:is\s+(?:now\s+)?)?\s*[:\-]?\s*(?:Rs\.?|INR|₹|rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""" +
        """)""",
        RegexOption.IGNORE_CASE
    )

    private val CREDIT_LIMIT_PATTERN = Regex(
        """(?:Avl\.?\s*(?:Credit\s+)?[Ll]imit|(?:\w+\s+){0,2}[Aa]vailable\s+[Ll]imit|Credit\s+Limit\s+Avl\.?|[Bb]alance\s+[Ll]imit)\s*(?:is\s+(?:now\s+)?)?\s*[:\-]?\s*(?:Rs\.?|INR|₹|rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Hoisted from extract4DigitNumbers — compiled once per class load instead of per SMS.
    private val CARD_KEYWORD_PATTERN = Regex(
        """(?:Card|card|ending|A/C|A/c|a/c|A/C\.?|account|acct|credit\s+card|CREDIT\s+CARD|ENDING\s+WITH|ending\s+with|no\.?|number|NO\.?|NUMBER)[\s:]*([*Xx\d]+)""",
        RegexOption.IGNORE_CASE
    )
    private val ANY_4_DIGIT_PATTERN = Regex("""\b(\d{4})\b""")
    private val ACCOUNT_KEYWORD_CONTEXT_PATTERN = Regex(
        """(?:Card|card|ending|A/C|account|credit\s+card|CREDIT\s+CARD|ENDING\s+WITH|ending\s+with|no\.?|number|NO\.?|NUMBER)""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_CONTEXT_PATTERN = Regex("""/|-|on|at|date""", RegexOption.IGNORE_CASE)
    private val PHONE_CONTEXT_PATTERN = Regex(
        """(?:call|sms|to|from|phone|mobile|tel)[\s:]*\d*[\s-]*\d{4}[\s-]*\d+""",
        RegexOption.IGNORE_CASE
    )
    private val LONG_DIGIT_PATTERN = Regex("""\d{10,}""")
    private val DECIMAL_CONTEXT_PATTERN = Regex("""\.\d{1,2}|,\d{3}""")
    private val MASKED_4_DIGIT_PATTERN = Regex("""[*Xx]+(\d{4})""")
    private val MASKED_PHONE_CONTEXT_PATTERN = Regex(
        """(?:call|sms|to|from|phone|mobile|tel)[\s:]*\d*[*Xx]*\d{4}""",
        RegexOption.IGNORE_CASE
    )

    // Hoisted from extractBankName
    private val BANK_PATTERN_1 = Regex("""\b([A-Z]{2,})(?:BANK|BANK\s+CARD|CARD|BANK\s+A/C)\b""", RegexOption.IGNORE_CASE)
    private val BANK_PATTERN_2 = Regex("""\b([A-Z]{2,})\s+(?:Bank|Credit|Debit)\s+Card""", RegexOption.IGNORE_CASE)
    private val BANK_SUFFIX_PATTERN = Regex("""BANK$""", RegexOption.IGNORE_CASE)
    private val BANK_NAME_PATTERNS: Map<String, Regex> by lazy {
        SmsConstants.BANK_NAMES.associateWith { bank -> Regex("""\b${Regex.escape(bank)}\b""", RegexOption.IGNORE_CASE) }
    }

    // Hoisted from cleanAccountName
    private val ACCOUNT_LABEL_NOISE_PATTERN = Regex(
        """\b(ending|ending with|spent on|spent using|using|on your|your|towards your|received towards)\b""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT_CARD_LABEL_PATTERN = Regex("""\bCredit\s+Card\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_WORD_PATTERN = Regex("""\bCredit\s+(?=\s|$)""", RegexOption.IGNORE_CASE)
    private val MASKED_ACCOUNT_NUMBER_PATTERN = Regex("""\b[*Xx]+(\d{4,})\b""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")

    // Hoisted from cleanMerchantName
    private val MASKED_ACCOUNT_IN_MERCHANT_PATTERN = Regex("""(?:^|\s)[Xx*]{1,4}\d{4,}(?:\s|$)""")
    private val LONG_DIGIT_IN_MERCHANT_PATTERN = Regex("""(?:^|\s)\d{6,}(?:\s|$)""")
    private val MULTI_SPACE_PATTERN = Regex("""\s{2,}""")
    private val WORD_SPLIT_PATTERN = Regex("""\s+""")
    private val BANK_WORD_PATTERNS: Map<String, Regex> by lazy {
        (SmsConstants.BANK_NAMES + listOf("UPI", "IMPS", "NEFT", "RTGS", "ACH", "INR")).associateWith { word ->
            Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE)
        }
    }

    // Hoisted from resolveFallbackMerchant
    private val ACCOUNT_SUFFIX_PATTERN = Regex(
        """(?:a/?c\.?|account|acct)(?:\s+(?:No|no)\.?)?[\s:]*([*Xx0-9]+|\d{4,})""",
        RegexOption.IGNORE_CASE
    )
    private val CARD_SUFFIX_PATTERN = Regex("""(?:Card|card)\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val WITHDRAWAL_PATTERN = Regex(
        """\b(?:atm\s+wdl|cash\s+wdl|cash\s+wthdl|cash\s+withdrawal|atm\s+cash|atm\s+txn|nfs\*cash\s+wdl)\b""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT_CARD_TEXT_PATTERN = Regex("""credit\s+card|creditcard""", RegexOption.IGNORE_CASE)
    private val DEBIT_CARD_TEXT_PATTERN = Regex("""debit\s+card""", RegexOption.IGNORE_CASE)
    private val UPI_TEXT_PATTERN = Regex("""\bUPI\b""", RegexOption.IGNORE_CASE)
    private val NEFT_TEXT_PATTERN = Regex("""\bNEFT\b""", RegexOption.IGNORE_CASE)
    private val RTGS_TEXT_PATTERN = Regex("""\bRTGS\b""", RegexOption.IGNORE_CASE)
    private val IMPS_TEXT_PATTERN = Regex("""\bIMPS\b""", RegexOption.IGNORE_CASE)
    private val WITHDRAWN_TEXT_PATTERN = Regex("""\b(?:withdrawn|withdrawal)\b""", RegexOption.IGNORE_CASE)

    // Hoisted from transferMerchantLabel
    private val ACCT_PREFIX_PATTERN = Regex(
        """^\s*(?:a/?c\.?|account|acct)(?:\s+(?:No|no)\.?)?\s*""",
        RegexOption.IGNORE_CASE
    )

    // Hoisted from applyMerchantOverrides
    private val SALARY_PATTERN = Regex("""\bsalary\b""", RegexOption.IGNORE_CASE)
    private val TOLL_CHARGES_PATTERN = Regex("""\btoll\s+charges?\b""", RegexOption.IGNORE_CASE)

    // Hoisted from extractTransactionDataInternal — created every call previously.
    private val AMOUNT_PATTERN = Regex(CURRENCY_AMOUNT, RegexOption.IGNORE_CASE)
    private val DATE_PATTERN = Regex("""(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""")
    private val DATE_PATTERN_WITH_MONTH = Regex(
        """(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_PATTERN_NO_SEP = Regex(
        """(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_PATTERN_DOT = Regex("""(?<date>\d{1,2}\.\d{1,2}\.\d{2,4})""")
    private val VPA_PATTERN = Regex(
        """(?:from|via|VPA|vpa)[\s:]*([\w.+\-]+@[\w.\-]+)|([\w.+\-]+@[\w.\-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_ACCOUNT_PATTERN = Regex(
        """(?:a/?c\.?|account|acct)(?:\s+(?:No|no)\.?)?[\s:]*([*Xx0-9]+|\d{4,})""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_CARD_PATTERN = Regex("""(?:Card|card)\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val REFERENCE_PATTERN = Regex(
        """(?:Ref(?:erence)?(?:\s+No\.?)?|Refno|UPI|UTR)[:\s-]+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    // Hoisted legacy patterns (Pattern 0–7 inside extractTransactionDataInternal)
    private val TRANSACTION_OF_AT_PATTERN = Regex(
        """transaction\s+of\s+$CURRENCY_AMOUNT\s+at\s+(?<merchant>[A-Za-z0-9]+)\s+.*?debited\s+to\s+your\s+.*?(?:Card|CreduCard)\s+ending\s+(?<cardNumber>\d{4})\s+on\s+(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val DEBIT_PATTERN_1 = Regex(
        """(?:Sms[:\s]*)?(?:[A-Za-z\s]+:)?\s*(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s*debited\s+from\s+(?:a/?c\.?\s*)?(?<fromAcct>[*Xx0-9A-Za-z-]+).*?(?:on\s+)?(?<date>\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?).*?to\s+(?:a/?c\.?\s*)?(?<toAcct>[*Xx0-9A-Za-z-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val DEBIT_PATTERN_2 = Regex(
        """(?:Amt\s+)?Sent\s+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)[\s\S]*?From\s+(?<fromAcct>[^\n]+)[\s\S]*?To\s+(?<toAcct>[^\n]+)""",
        RegexOption.IGNORE_CASE
    )
    private val TO_IS_ACCOUNT_PATTERN = Regex("""To\s+a/?c""", RegexOption.IGNORE_CASE)
    private val CREDIT_PATTERN_1 = Regex(
        """(?:Sms[:\s]*)?.*?(?:Credit\s+Alert!|credited|credit)\s+(?:to\s+)?(?:HDFC\s+Bank\s+)?(?:a/?c\.?\s*)?(?<account>[*Xx0-9A-Za-z\s-]+)?.*?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)[\s\S]*?(?:on\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    // Pattern 3.51: HDFC FT structured format — "for FT- <description>-<masked_acct> - <company> -"
    // e.g. "for FT- Cnsltcy fee-XXXXXXXXXX8355 - VIHAAS DESIGN TECHNOLOGIES -."
    private val FT_STRUCTURED_PATTERN = Regex(
        """(?:Update!?\s*)?$CURRENCY_AMOUNT\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+).*?(?:on\s+)?(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}).*?for\s+FT[-:\s]+(?<description>[^.]+?)-\s*(?<company>[A-Za-z][A-Za-z0-9\s&,./()-]{2,}?)\s*-\s*(?:\.|$)""",
        RegexOption.IGNORE_CASE
    )
    private val SALARY_DEPOSIT_PATTERN = Regex(
        """(?:Update!?\s*)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+).*?(?:on\s+)?(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}).*?for\s+FT[-:\s]+(?<vendor>[A-Z0-9]+[A-Z0-9X]*)""",
        RegexOption.IGNORE_CASE
    )
    private val VENDOR_TRAILING_X_PATTERN = Regex("""X{10,}$""")
    private val VENDOR_TRAILING_X_SHORT_PATTERN = Regex("""X+$""")
    private val DEPOSIT_WITH_MERCHANT_PATTERN = Regex(
        """(?:Update!?\s*)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+).*?(?:on\s+)?(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}).*?for\s+(?<referenceNum>\d+)\s+(?<merchant>[A-Z][A-Za-z0-9\s]+?)(?:\s+\d{6,}|\s*\.|$)""",
        RegexOption.IGNORE_CASE
    )
    private val DEPOSIT_MERCHANT_TRAILING_DIGITS_PATTERN = Regex("""\s+\d{6,}.*$""")
    private val DEPOSIT_MERCHANT_TRAILING_CAPS_PATTERN = Regex("""\s+[A-Z]{2,4}$""")
    private val NEFT_DEPOSIT_PATTERN = Regex(
        """(?:Update!?\s*)?$CURRENCY_AMOUNT\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+for\s+(?:NEFT|RTGS|IMPS)\s+Cr-[A-Z0-9]+-(?<merchant>.+?)-[A-Z0-9 ]+-[A-Z0-9]+(?:\.|$)""",
        RegexOption.IGNORE_CASE
    )
    private val RECEIVED_CREDIT_PATTERN = Regex(
        """Received!?\s*$CURRENCY_AMOUNT\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+On\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4})\s+For\s+(?:NEFT|RTGS|IMPS)\s*-\s*(?<merchant>.+?)\s*-\s*\d+""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val GENERIC_DEPOSIT_PATTERN = Regex(
        """(?:Update!?\s*)?$CURRENCY_AMOUNT\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+for\s+(?<description>[^.]+?)(?:\.(?:Avl|$)|$)""",
        RegexOption.IGNORE_CASE
    )
    private val ACH_CREDIT_PATTERN = Regex("""ACH\s+C[-\s]+(?:SAL|SAL\s*)-\s*([A-Za-z][A-Za-z0-9]+)-""", RegexOption.IGNORE_CASE)
    private val ABBREVIATION_PATTERN = Regex("""[A-Z0-9]{4,20}""")
    private val IFSC_CODE_PATTERN = Regex("""[A-Z0-9]{6,11}""")
    private val X_ONLY_PATTERN = Regex("""X+""")
    private val AMT_DEDUCTED_PATTERN = Regex(
        """(?:Amt\s+Deducted!?\s*)?(?:Rs\.?|INR|₹)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+from\s+your\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+for\s+(?<description>[^.]+?)(?:\.|$)""",
        RegexOption.IGNORE_CASE
    )
    private val FASTAG_PATTERN = Regex(
        """FASTag\s+Acc.*?(?:ending\s+with\s+)?(?<account>\d{4,})\s+VRN\s*:\s*[A-Z0-9]+\s+debited\s+(?:Rs\.?|INR|₹)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+(?<chargeType>[A-Za-z\s]+?)\s+at\s+[A-Za-z0-9\s]+\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val ATM_WITHDRAWAL_PATTERN = Regex(
        """(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+withdrawn\s+from\s+(?<account>[A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val ATM_BANK_CARD_PATTERN = Regex("""^([A-Za-z0-9\s&]+?)\s+(?:Bank\s+)?Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val MASKED_CARD_LAST4_PATTERN = Regex("""[*Xx]*(\d{4,})$""")
    private val ISO_DATE_IN_TEXT_PATTERN = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}""")
    private val WITHDRAWN_CARD_PATTERN = Regex(
        """(?:Spent|Withdrawn)\s+(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:From|On)\s+(?<account>[A-Z][A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)\s+At\s+(?<merchant>[+\-]?[A-Za-z0-9][A-Za-z0-9\s.+\-]*?)(?:\s+On\s+\d|\s+Bal\b|\s+Not\b|\.Not\b|$)""",
        RegexOption.IGNORE_CASE
    )
    private val BANK_CARD_LABEL_PATTERN = Regex("""^([A-Z][A-Za-z0-9\s&]+?)\s+Bank\s+Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val ISO_DATE_ONLY_PATTERN = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""")
    private val CREDIT_CARD_ENDING_PATTERN = Regex(
        """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+spent\s+on\s+your\s+(?<account>[A-Z][A-Za-z0-9\s&]+?\s+Credit\s+Card)\s+ending\s+(?<cardNumber>[\dXx*]+)\s+at\s+(?<merchant>[A-Za-z0-9\s&]+?)(?:\s+on\s+|\s+at\s+|$)""",
        RegexOption.IGNORE_CASE
    )
    private val BANK_FIRST_WORD_PATTERN = Regex("""^([A-Z][A-Za-z0-9]+)""")
    private val SIMPLE_DATE_SLASH_PATTERN = Regex("""\d{1,2}/\d{1,2}/\d{2,4}""")
    private val CREDIT_CARD_SPEND_PATTERN = Regex(
        """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?|\.\d{1,2})\s+spent\s+(?:using\s+|on\s+)[^.]*?(?:Card|card)\s+(?<cardNumber>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+(?:on|at)\s+(?<merchant>(?:null\*)?[A-Za-z0-9*& -]+?)(?:\.|,|\s+Avl\b|$)""",
        RegexOption.IGNORE_CASE
    )
    private val SPEND_ACCOUNT_PATTERN = Regex("""([A-Z][A-Za-z0-9\s&]+?\s+Bank\s+Card\s+[*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val SPEND_ACCOUNT_FALLBACK_PATTERN = Regex(
        """spent\s+(?:using\s+|on\s+)([A-Z][A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val SPEND_ACCOUNT_FLEXIBLE_PATTERN = Regex("""([A-Z][A-Za-z\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val BANK_NAME_IN_TEXT_PATTERN = Regex("""([A-Z][A-Za-z]+)\s+Bank""", RegexOption.IGNORE_CASE)
    private val BANK_CARD_FULL_PATTERN = Regex("""^([A-Z][A-Za-z0-9\s&]+?)\s+Bank\s+Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val NULL_PREFIX_PATTERN = Regex("""^null\*""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION_PATTERN = Regex("""[.,]+$""")
    private val HDFC_CARD_SPEND_AT_PATTERN = Regex(
        """$CURRENCY_AMOUNT\s+spent\s+on\s+(?<account>[A-Za-z][A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)\s+at\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s&*._-]+?)\s+on\s+(?<date>\d{4}-\d{2}-\d{2})""",
        RegexOption.IGNORE_CASE
    )
    private val HDFC_BANK_CARD_PATTERN = Regex("""^([A-Za-z][A-Za-z0-9\s&]+?)\s+(?:Bank\s+)?Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_IN_MERCHANT_PATTERN = Regex("""\s{2,}""")
    private val ICICI_DEBIT_PATTERN = Regex(
        """(?:[A-Za-z]+\s+)?(?:Bank\s+)?Acct\s+(?<account>[*Xx0-9]+)\s+debited\s+for\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4});\s*(?<merchant>[^.;]+?)\s+credited""",
        RegexOption.IGNORE_CASE
    )
    private val ICICI_CREDIT_PATTERN = Regex(
        """Acct\s+(?<account>[*Xx0-9]+)\s+is\s+credited\s+with\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+from\s+(?<merchant>[^.]+?)(?:\.\s*UPI|\.$|$)""",
        RegexOption.IGNORE_CASE
    )
    private val SBI_UPI_DEBIT_PATTERN = Regex(
        """A/?C\s+(?<account>[*Xx0-9]+)\s+debited\s+by\s+(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+on\s+date\s+(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})\s+trf\s+to\s+(?<merchant>[A-Za-z0-9\s]+?)\s+Refno\s+(?<ref>\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val SBI_DEBIT_CARD_TXN_PATTERN = Regex(
        """transaction\s+number\s+(?<ref>\d+)\s+for\s+$CURRENCY_AMOUNT\s+by\s+(?<bank>[A-Za-z]+)\s+Debit\s+Card\s+(?<card>[*Xx0-9]+)\s+done\s+at\s+(?<merchant>[A-Za-z0-9]+)\s+on\s+(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val SBI_YONO_TRANSFER_PATTERN = Regex(
        """Ac\s+(?<account>[*Xx0-9]+)\s+debited\s+$CURRENCY_AMOUNT\s+for\s+transfer\s+to\s+(?<merchant>[A-Za-z0-9\s]+?)\s+Ac\s+(?<toAcct>[*Xx0-9]+)\s+dt\s+(?<date>\d{1,2}\.\d{1,2}\.\d{2,4})\s+Ref\s+(?<ref>\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val INDIAN_BANK_DEBIT_PATTERN = Regex(
        """A/?[Cc]\.?\s+[*Xx]*(?<account>\d{4,})\s+debited\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+to\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&-]+?)(?:\.\s*UPI|\.\s*Not\b|$)""",
        RegexOption.IGNORE_CASE
    )
    private val SBI_CREDIT_TRANSFER_PATTERN = Regex(
        """A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)-?credited\s+by\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})\s+transfer\s+from\s+(?<merchant>[A-Za-z][A-Za-z\s]+?)\s+Ref\s+(?:No\.?\s+)?(?<ref>\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val SIB_DEBIT_PATTERN = Regex(
        """A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)\s+is\s+debited\s+with\s+$CURRENCY_AMOUNT\s+Info\s*:\s*(?<info>[^.]+?)(?:\.\s*Final\s+balance|\.$|$)""",
        RegexOption.IGNORE_CASE
    )
    private val SIB_DEBIT_CARD_INFO_PATTERN = Regex("""debit\s+card\s+ending\s+[*]+(\d{4})""", RegexOption.IGNORE_CASE)
    private val SIB_UPI_CREDIT_PATTERN = Regex(
        """UPI\s+Credit\s*:\s*(?:INR\s+)?$CURRENCY_AMOUNT\s+in\s+A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)\.?\s+Info\s*:\s*UPI/(?<bank>[A-Za-z0-9]+)/(?<ref>\d+)/""",
        RegexOption.IGNORE_CASE
    )
    private val MANDATE_EXECUTED_PATTERN = Regex(
        """mandate\s+for\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&,-]+?),\s*$CURRENCY_AMOUNT\s+has\s+been\s+executed""",
        RegexOption.IGNORE_CASE
    )
    private val REFERENCE_NUMBER_PATTERN = Regex("""Reference\s+(?:number|no\.?)\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val BOI_IMPS_CREDIT_PATTERN = Regex(
        """(?:ac\s+no\.?\s+)?(?:[*Xx]+)?(?<account>\d{4,})\s+is\s+credited\s+for\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{6})""",
        RegexOption.IGNORE_CASE
    )
    private val IMPS_REF_PATTERN = Regex("""(?:IMPS\s+Ref\s+no\.?|Ref\s+no\.?|Refno)\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val BOI_UPI_DEBIT_PATTERN = Regex(
        """[Ss]\.(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+debited\s+A/?[Cc](?<account>[*Xx0-9]+)\s+and\s+credited\s+to\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&-]*?)\s+via\s+UPI""",
        RegexOption.IGNORE_CASE
    )
    private val SUPER_CARD_SPEND_PATTERN = Regex(
        """$CURRENCY_AMOUNT\s+was\s+spent\s+on\s+your\s+SuperCard\s+at\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&*-]+?)(?:\s*\.|,|\s+Available|\s+View|$)""",
        RegexOption.IGNORE_CASE
    )
    private val SUPER_CARD_UPI_DEBIT_PATTERN = Regex(
        """SuperCard\s+(?<cardNum>\d{4})\s+debited\s+for\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*)""",
        RegexOption.IGNORE_CASE
    )
    private val UPI_REF_DASH_PATTERN = Regex("""UPI\s*[-–]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val UPI_DEBIT_COLON_PATTERN = Regex(
        """UPI\s+debit\s*:\s*$CURRENCY_AMOUNT\s*,\s*A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)\s*,\s*(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val RRN_PATTERN = Regex("""RRN\s*[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
    private val ACH_SIP_DEBIT_PATTERN = Regex(
        """$CURRENCY_AMOUNT\s+debited\s+from\s+(?<bank>[A-Za-z]+(?:\s+Bank)?)\s+(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\.\s+Info\s*:\s*(?<info>[^.]+?)(?:[-.]|\s*$)""",
        RegexOption.IGNORE_CASE
    )
    private val SIP_NAME_PATTERN = Regex("""ACH\s+D[-\s]+(.+?)\s+SIP""", RegexOption.IGNORE_CASE)
    private val NEFT_NETBANKING_DEBIT_PATTERN = Regex(
        """(?:Dear\s+Customer[,.]?\s*)?$CURRENCY_AMOUNT\s+is\s+debited\s+from\s+A/?[Cc]\.?\s*(?<account>[*Xx0-9]+)\s+for\s+(?:NEFT|RTGS|IMPS)\s+transaction\s+via\s+.*?NetBanking""",
        RegexOption.IGNORE_CASE
    )
    private val DEBIT_PATTERN_3 = Regex(
        """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:paid\s+to|spent\s+at|debited\s+for)\s+(?<merchant>[^\n,]+?)(?:\s+on\s+|\s+at\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})?""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT_CARD_PAYMENT_PATTERN = Regex(
        """(?:Payment\s+of\s+|PAYMENT\s+OF\s+)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:was\s+)?(?:credited\s+to\s+(?:your\s+)?(?:card|credit\s+card)|RECEIVED\s+TOWARDS\s+YOUR\s+CREDIT\s+CARD).*?(?:ending|no\.?|number|ENDING\s+WITH)\s*(?<cardNumber>[\dXx*]+)""",
        RegexOption.IGNORE_CASE
    )
    private val CC_BANK_NAME_PATTERN = Regex("""([A-Z]{2,})(?:BANK|BANK\s+CARD|CARD|CARDMEMBER)""", RegexOption.IGNORE_CASE)
    private val CC_BANK_SUFFIX_PATTERN = Regex("""BANK$""", RegexOption.IGNORE_CASE)
    private val CC_CARDMEMBER_PATTERN = Regex("""DEAR\s+([A-Z]{2,})(?:BANK)?\s+CARDMEMBER""", RegexOption.IGNORE_CASE)
    private val COMMON_BANK_PATTERN = Regex("""\b(HDFC|SBI|ICICI|AXIS|KOTAK|PNB|PUNJAB|YES|RBL)\b""", RegexOption.IGNORE_CASE)
    private val CC_PAYMENT_RECEIVED_PATTERN = Regex(
        """Payment\s+of\s+$CURRENCY_AMOUNT\s+has\s+been\s+received\s+on\s+your\s+(?<bank>[A-Za-z\s]+?)\s+Credit\s+[Cc]ard\s+(?<cardNumber>[*Xx0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val CC_PAYMENT_NO_CARD_PATTERN = Regex(
        """received\s+payment\s+of\s+$CURRENCY_AMOUNT\s+.*?credited\s+to\s+your\s+(?<bank>[A-Za-z]+)\s+Credit\s+Card""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val NEFT_CREDIT_PATTERN = Regex(
        """$CURRENCY_AMOUNT\s+credited\s+to\s+your\s+A/?[Cc]\.?\s+(?:No\.?\s+)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+through\s+NEFT\s+with\s+UTR\s+(?<ref>\d+)\s+by\s+(?<merchant>[A-Za-z][A-Za-z0-9\s&]+?)(?:\s*,|\s*-SBI|\s*$)""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT_PATTERN_2 = Regex(
        """(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:credited|received|deposited)\s+(?:to\s+)?(?:account|a/?c\.?)?\s*(?<account>[*Xx0-9A-Za-z-]+)?.*?(?:on\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val CC_IN_CREDIT_PATTERN = Regex(
        """(?:CREDIT\s+CARD|credit\s+card).*?(?:ending|no\.?|number|ENDING\s+WITH)\s*([\dXx*]+)""",
        RegexOption.IGNORE_CASE
    )
    private val UPI_PATTERN = Regex(
        """(?:UPI|upi)\s+(?:payment|transaction)\s+(?:of\s+)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:to|for)\s+(?<merchant>[^\n,]+?)(?:\s+on\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})?""",
        RegexOption.IGNORE_CASE
    )
    private val PAID_FOR_PATTERN = Regex(
        """(?:Alert!?\s*)?Paid\s+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+For:\s*(?<merchant>[^\n,]+?)(?:\s+From|\s+Via|\s+on\s+|\s+at\s+|$)""",
        RegexOption.IGNORE_CASE
    )
    private val PAID_CC_ENDING_PATTERN = Regex("""(?:ending|no\.?|number|ENDING\s+WITH)\s*([\dXx*]+)""", RegexOption.IGNORE_CASE)
    private val MONEY_SENT_PATTERN = Regex(
        """Money\s+Sent[-\s]+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+From\s+.*?(?:A/?c\.?\s*)?(?<fromAcct>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?)\s+To\s+(?:A/?c\.?\s*)?(?<toAcct>[*Xx0-9]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val MONEY_RECEIVED_PATTERN = Regex(
        """Money\s+received[-\s]+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+in\s+your\s+.*?(?:A/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?).*?by\s+.*?(?<byAcct>[*Xx]+\d{4,})""",
        RegexOption.IGNORE_CASE
    )
    // Pattern 7 generic fallback sub-patterns
    private val CREDITED_TO_CARD_PATTERN = Regex("""\bcredited\s+to\s+(?:your\s+)?(?:card|credit\s+card)\b""", RegexOption.IGNORE_CASE)
    private val RECEIVED_TOWARDS_CC_PATTERN = Regex("""\bRECEIVED\s+TOWARDS\s+YOUR\s+CREDIT\s+CARD\b""", RegexOption.IGNORE_CASE)
    private val DEBIT_KEYWORDS_PATTERN = Regex("""\b(debited|spent|paid|withdrawn|deducted)\b|\bDebit\s+Card\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_KEYWORDS_PATTERN = Regex("""\b(credited|received|deposited|added)\b""", RegexOption.IGNORE_CASE)
    private val CC_TEXT_PATTERN = Regex("""credit\s+card|creditcard""", RegexOption.IGNORE_CASE)
    private val CC_ENDING_PATTERN = Regex("""(?:ending|no\.?|number|ending\s+with)\s*([\dXx*]+)""", RegexOption.IGNORE_CASE)
    private val DATE_LIKE_PATTERN = Regex("""^\d{1,2}[/\-]\d{1,2}([/\-]\d{2,4})?$""")
    // normalizeDate patterns
    private val ISO_DATE_NORMALIZE_PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val MONTH_NAME_DATE_PATTERN = Regex(
        """^(\d{1,2})[-/](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/](\d{2,4})$""",
        RegexOption.IGNORE_CASE
    )
    private val COMPACT_MONTH_DATE_PATTERN = Regex(
        """^(\d{1,2})(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(\d{2,4})$""",
        RegexOption.IGNORE_CASE
    )
    private val SIX_DIGIT_DATE_PATTERN = Regex("""^(\d{2})(\d{2})(\d{2})$""")
    private val DAY_MONTH_PATTERN = Regex(
        """^(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*$""",
        RegexOption.IGNORE_CASE
    )
    private val PARTIAL_DATE_PATTERN = Regex("""^(\d{1,2})[-/](\d{1,2})$""")
    private val SLASH_DATE_PATTERN = Regex("""^(\d{1,2})/(\d{1,2})/(\d{2,4})$""")
    private val DASH_DATE_PATTERN = Regex("""^(\d{1,2})-(\d{1,2})-(\d{2,4})$""")
    private val DOT_DATE_PATTERN = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{2,4})$""")
    private val TOLL_HOTLINE_CONTEXT_PATTERN = Regex("""call|sms|toll""", RegexOption.IGNORE_CASE)
    private val NON_DIGIT_PATTERN = Regex("""[^0-9]""")

    fun extractBalance(sms: String): Double? {
        val m = BALANCE_PATTERN.find(sms) ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { return null }
        return raw.replace(",", "").toDoubleOrNull()
    }

    fun extractCreditLimit(sms: String): Double? =
        CREDIT_LIMIT_PATTERN.find(sms)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    fun extractTransactionData(
        sms: String,
        dynamicPatterns: List<SmsPatternEntity> = emptyList(),
        senderAddress: String? = null
    ): ExtractedTransaction {
        val result = extractTransactionDataInternal(sms, dynamicPatterns, senderAddress)
        val text = sms.replace("\r", "\n").trim()
        val withOverrides = applyMerchantOverrides(result, text)
        val merchant = withOverrides.merchant
            ?: withOverrides.accounts
            ?: resolveFallbackMerchant(text, senderAddress)
        return withOverrides.copy(
            merchant = cleanMerchantName(merchant) ?: "Bank Transaction",
            balance = withOverrides.balance ?: extractBalance(text),
            creditLimit = withOverrides.creditLimit ?: extractCreditLimit(text)
        )
    }

    private fun resolveFallbackMerchant(text: String, senderAddress: String?): String {
        val accountSuffix = ACCOUNT_SUFFIX_PATTERN
            .find(text)?.groupValues?.getOrNull(1)
            ?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
            ?: extract4DigitNumbers(text).firstOrNull()
        val cardSuffix = CARD_SUFFIX_PATTERN
            .find(text)?.groupValues?.getOrNull(1)
            ?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }

        val isWithdrawal = WITHDRAWAL_PATTERN.containsMatchIn(text)

        val txType = when {
            CREDIT_CARD_TEXT_PATTERN.containsMatchIn(text) -> "Credit Card Payment"
            DEBIT_CARD_TEXT_PATTERN.containsMatchIn(text) -> "Card Purchase"
            isWithdrawal -> "Withdrawal"
            UPI_TEXT_PATTERN.containsMatchIn(text) -> "UPI Transfer"
            NEFT_TEXT_PATTERN.containsMatchIn(text) -> "NEFT Transfer"
            RTGS_TEXT_PATTERN.containsMatchIn(text) -> "RTGS Transfer"
            IMPS_TEXT_PATTERN.containsMatchIn(text) -> "IMPS Transfer"
            WITHDRAWN_TEXT_PATTERN.containsMatchIn(text) -> "Bank Debit"
            else -> null
        }

        return when {
            txType == "Withdrawal" && accountSuffix != null -> "Withdrawal ($accountSuffix)"
            txType == "Withdrawal" && cardSuffix != null -> "Withdrawal ($cardSuffix)"
            txType != null && txType.endsWith("Transfer") && accountSuffix != null -> "$txType ($accountSuffix)"
            txType != null -> txType
            else -> "Bank Transaction"
        }
    }

    internal fun transferMerchantLabel(text: String, beneficiaryAccount: String?): String {
        val mode = when {
            UPI_TEXT_PATTERN.containsMatchIn(text) -> "UPI"
            NEFT_TEXT_PATTERN.containsMatchIn(text) -> "NEFT"
            RTGS_TEXT_PATTERN.containsMatchIn(text) -> "RTGS"
            IMPS_TEXT_PATTERN.containsMatchIn(text) -> "IMPS"
            else -> null
        }
        val label = mode?.let { "$it Transfer" } ?: "Bank Transfer"
        // Strip "A/c" prefix and other account keywords before extracting digits
        val cleanedAcct = beneficiaryAccount
            ?.replace(ACCT_PREFIX_PATTERN, "")
            ?.trim()
        val last4 = cleanedAcct?.filter { it.isDigit() }?.takeLast(4)
            ?.takeIf { it.length == 4 }
        return if (last4 != null) "$label ($last4)" else label
    }

    private fun extractTransactionDataInternal(
        sms: String,
        dynamicPatterns: List<SmsPatternEntity> = emptyList(),
        senderAddress: String? = null
    ): ExtractedTransaction {
        val empty = ExtractedTransaction()
        if (sms.isBlank()) return empty

        val text = sms.replace("\r", "\n").trim()
        if (SmsConstants.CREDIT_CARD_STATEMENT_PATTERNS.any { it.containsMatchIn(text) }) return empty

        fun parseAmount(amountStr: String?): Double? {
            if (amountStr == null) return null
            return amountStr.replace(",", "").toDoubleOrNull()
        }

        fun extractDate(smsText: String): String? {
            val monthMatch = DATE_PATTERN_WITH_MONTH.find(smsText)
            if (monthMatch != null) return monthMatch.groups["date"]?.value
            val numericMatch = DATE_PATTERN.find(smsText)
            if (numericMatch != null) return numericMatch.groups["date"]?.value
            val noSepMatch = DATE_PATTERN_NO_SEP.find(smsText)
            if (noSepMatch != null) return noSepMatch.groups["date"]?.value
            return DATE_PATTERN_DOT.find(smsText)?.groups?.get("date")?.value
        }

        fun extractCardNumber(smsText: String): String? {
            val m = INLINE_CARD_PATTERN.find(smsText)
            val raw = m?.groupValues?.getOrNull(1) ?: return null
            // Take last 4 digits to handle long masked numbers like 817430XXXXXX9989
            val last4 = raw.filter { it.isDigit() }.takeLast(4)
            return if (last4.length == 4) last4 else cleanCardNumber(raw)
        }

        fun extractVPA(smsText: String): String? {
            val m = VPA_PATTERN.find(smsText)
            val raw = m?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: m?.groupValues?.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return raw?.substringBefore("@")?.takeIf { it.isNotEmpty() }
        }

        fun extractAccount(smsText: String): String? {
            return INLINE_ACCOUNT_PATTERN.find(smsText)?.groupValues?.getOrNull(1)
        }

        fun extractReference(smsText: String): String? {
            return REFERENCE_PATTERN.find(smsText)?.groupValues?.getOrNull(1)
        }

        val reference = extractReference(text)

        // Dynamic patterns from Supabase (tried before hardcoded patterns)
        // Within the same priority, bank-specific patterns (sender_id set) are tried before
        // generic ones (sender_id null) so a precise bank pattern wins over a broad fallback.
        val detectedBankForDynamic = senderAddress?.let { BankSenderDetector.detect(it) }
            ?: extractBankName(text, senderAddress = null)
        val orderedDynamic = dynamicPatterns.sortedWith(
            compareBy({ it.priority }, { if (it.senderId != null) 0 else 1 })
        )
        for (dp in orderedDynamic) {
            val patternBank = dp.senderId?.let { BankSenderDetector.detect(it) }
            if (patternBank != null && patternBank != detectedBankForDynamic) continue
            val result = DynamicPatternEngine.tryMatch(dp, text, detectedBankForDynamic)
            if (result != null && result.amount != null && result.amount > 0) {
                return result.copy(
                    date = result.date ?: extractDate(text),
                    reference = result.reference ?: reference
                )
            }
        }

        // Build shared context passed to every registry pattern
        val ctx = PatternContext(
            text = text,
            parseAmount = ::parseAmount,
            extractDate = ::extractDate,
            extractVPA = ::extractVPA,
            extractAccount = ::extractAccount,
            extractReference = ::extractReference,
            extractCardNumber = ::extractCardNumber,
            reference = reference
        )

        // 1. Bank-specific patterns (detected via sender address first, then SMS text)
        val detectedBank = senderAddress?.let { BankSenderDetector.detect(it) }
            ?: extractBankName(text, senderAddress)
        val bankPatternList = detectedBank?.let { BankPatternRegistry.bankPatterns[it] }
        if (bankPatternList != null) {
            for (entry in bankPatternList) {
                val m = entry.regex.find(text) ?: continue
                val result = entry.extract(ctx, m)
                if (result != null && result.amount != null && result.amount > 0) return result
            }
        }

        // 2. Generic patterns (shared across all banks)
        for (entry in BankPatternRegistry.genericPatterns) {
            val m = entry.regex.find(text) ?: continue
            val result = entry.extract(ctx, m)
            if (result != null && result.amount != null && result.amount > 0) return result
        }

        // 3. Legacy hardcoded patterns below (kept as additional fallback coverage)
        // Pattern 0: "transaction of <amount> at <merchant> ... debited to your ... Card ending <4digits> on <date>"
        var match = TRANSACTION_OF_AT_PATTERN.find(text)
        if (match != null) {
            val cardNumber = cleanCardNumber(match.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val bankName = extractBankName(text) ?: "SBI"
            val accountLabel = "$bankName Bank Card $cardNumber"
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = cleanAccountName(accountLabel) ?: accountLabel,
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim() ?: ""),
                reference = reference
            )
        }

        // Pattern 1: "<amount> debited from a/c <fromAcct> ... on <date> ... to a/c <toAcct>"
        match = DEBIT_PATTERN_1.find(text)
        if (match != null) {
            val toAcct = match.groups["toAcct"]?.value?.trim()
            val merchant = extractVPA(text) ?: transferMerchantLabel(text, toAcct)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["fromAcct"]?.value?.trim(),
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 2: "Amt Sent / Sent Rs <amount> ... From <fromAcct> ... To <toAcct/merchant> ... On <date>"
        match = DEBIT_PATTERN_2.find(text)
        if (match != null) {
            val toAcct = match.groups["toAcct"]?.value?.trim()
            val vpa = extractVPA(text)
            val toIsAccount = TO_IS_ACCOUNT_PATTERN.containsMatchIn(text)
            val merchant = when {
                vpa != null -> vpa
                toIsAccount -> transferMerchantLabel(text, toAcct)
                else -> toAcct
            }
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = match.groups["fromAcct"]?.value?.trim(),
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3: "Credit Alert! / credited / credit ... a/c <account> ... Rs <amount> ... on <date>"
        match = CREDIT_PATTERN_1.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(extractVPA(text)),
                reference = reference
            )
        }

        // Pattern 3.51: HDFC FT structured — "for FT- <description>-<masked_acct> - <company> -."
        // e.g. "for FT- Cnsltcy fee-XXXXXXXXXX8355 - VIHAAS DESIGN TECHNOLOGIES -."
        match = FT_STRUCTURED_PATTERN.find(text)
        if (match != null) {
            val company = match.groups["company"]?.value?.trim()
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(company),
                reference = reference
            )
        }

        // Pattern 3.5: Salary deposit "Rs <amount> deposited in ... a/c <account> ... on <date> ... for FT-<vendor>"
        match = SALARY_DEPOSIT_PATTERN.find(text)
        if (match != null) {
            var cleanVendor = match.groups["vendor"]?.value?.trim()
            if (cleanVendor != null) {
                cleanVendor = cleanVendor.replace(VENDOR_TRAILING_X_PATTERN, "").replace(VENDOR_TRAILING_X_SHORT_PATTERN, "")
                if (cleanVendor.length < 3) cleanVendor = "Salary"
            }
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanVendor ?: "Salary",
                reference = reference
            )
        }

        // Pattern 3.6: Deposit with merchant "Rs <amount> deposited in ... a/c <account> ... on <date> ... for <ref> <merchant>"
        match = DEPOSIT_WITH_MERCHANT_PATTERN.find(text)
        if (match != null) {
            var merchant = match.groups["merchant"]?.value?.trim()
            if (merchant != null) {
                merchant = merchant.replace(DEPOSIT_MERCHANT_TRAILING_DIGITS_PATTERN, "").trim()
                    .replace(DEPOSIT_MERCHANT_TRAILING_CAPS_PATTERN, "").trim()
                if (merchant.length < 2) merchant = null
            }
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(merchant),
                reference = match.groups["referenceNum"]?.value?.trim() ?: reference
            )
        }

        // Pattern 3.61: NEFT/RTGS/IMPS deposit — "<currency> <amount> deposited in <bank> A/c <account> on <date> for NEFT Cr-<ifsc>-<merchant>-<sender>-<utr>"
        // e.g. "Update! INR 92,320.00 deposited in HDFC Bank A/c XX5988 on 24-OCT-24 for NEFT Cr-CHAS0INBX01-J.P. Morgan Services India Private Limited-AASHWIN SHARMA-CHASH29878170677"
        match = NEFT_DEPOSIT_PATTERN.find(text)
        if (match != null) {
            var merchant = match.groups["merchant"]?.value?.trim()
            if (merchant != null) merchant = cleanMerchantName(merchant)
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3.611: Received credit — "Received! INR <amount> in ... A/c <account> On <date> For IMPS/NEFT/RTGS -<merchant>- <ref>"
        // e.g. "Received!\nINR 1,127.00 in HDFC Bank A/c xx5648\nOn 25-08-25\nFor IMPS -Cashfree Private Lim- 523717422710"
        match = RECEIVED_CREDIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 3.62: Generic deposit — "<currency> <amount> deposited in <bank> A/c <account> on <date> for <description>"
        // e.g. "Update! INR 9,770.00 deposited in HDFC Bank A/c XX5648 on 31-MAR-26 for Interest paid till 31-MAR-2026."
        // e.g. "Update! INR 3,00,000.00 deposited in HDFC Bank A/c XX5648 on 01-APR-26 for A2AINT01-VIHAAS DESIGN TECHNOLOGIES-Salary-XXX."
        match = GENERIC_DEPOSIT_PATTERN.find(text)
        if (match != null) {
            val rawDescription = match.groups["description"]?.value?.trim() ?: ""
            // ACH credit format: "ACH C- SAL-<company>-<ref>" or "ACH C-<purpose>-<company>-<ref>"
            val achCreditMatch = ACH_CREDIT_PATTERN.find(rawDescription)
            val merchant = when {
                achCreditMatch != null -> {
                    // Company name is the first dash-segment after SAL-; clean up alphanumeric-only bank codes
                    val rawCompany = achCreditMatch.groupValues[1].trim()
                    // Heuristic: if it looks like an all-caps abbreviation (≤ 14 chars, no spaces), treat as salary
                    if (rawCompany.matches(ABBREVIATION_PATTERN)) "Salary"
                    else "Salary ($rawCompany)"
                }
                // Format: <IFSC/code>-<company>-<purpose>-<ref>
                rawDescription.split("-").let { parts ->
                    parts.size >= 3 && parts[0].trim().matches(IFSC_CODE_PATTERN)
                } -> {
                    val dashParts = rawDescription.split("-")
                    val company = dashParts[1].trim()
                    val purpose = dashParts[2].trim()
                    when {
                        purpose.equals("salary", ignoreCase = true) -> "Salary"
                        purpose.isNotBlank() && !purpose.matches(X_ONLY_PATTERN) -> "$company ($purpose)"
                        else -> company
                    }
                }
                else -> rawDescription
            }
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(merchant),
                reference = reference
            )
        }

        // Pattern 3.63: "Amt Deducted! Rs.<amount> from your <bank> A/c <account> for <description>"
        // e.g. "Amt Deducted! Rs.3,00,000.00 from your HDFC Bank A/c XX5648 for Money Transfer via HDFC Bank Online Banking."
        match = AMT_DEDUCTED_PATTERN.find(text)
        if (match != null) {
            val description = match.groups["description"]?.value?.trim()
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(description),
                reference = reference
            )
        }

        // Pattern 3.64: FASTag debit — "FASTag Acc ending with <acc> VRN: <vrn> debited Rs.<amount> <charge-type> at <location> on <date>"
        // e.g. "SBI FASTag Acc ending with 39820 VRN: DL1CAA2066 debited Rs.80 Toll Charges at Kaudia Checkpost on 28-Apr-2026"
        match = FASTAG_PATTERN.find(text)
        if (match != null) {
            val chargeType = match.groups["chargeType"]?.value?.trim()
                ?.takeIf { it.isNotBlank() } ?: "Toll Charges"
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanMerchantName(chargeType),
                reference = reference
            )
        }

        // Pattern 3.65: ATM/cash withdrawal — "Rs <amount> withdrawn from <bank> Bank Card <x1234> at <location>"
        // Merchant is synthesised as "Withdrawal (<Bank> x<last4>)" — the "at" location is an ATM site, not a merchant.
        match = ATM_WITHDRAWAL_PATTERN.find(text)
        if (match != null) {
            val accountRaw = match.groups["account"]?.value?.trim()
            val bankMatch = ATM_BANK_CARD_PATTERN.find(accountRaw ?: "")
            val bank = bankMatch?.groupValues?.get(1)?.trim()
                ?: extractBankName(text)
            val last4 = bankMatch?.groupValues?.get(2)
                ?.let { MASKED_CARD_LAST4_PATTERN.find(it) }
                ?.groupValues?.get(1)?.takeLast(4)
            val merchant = when {
                last4 != null -> "Withdrawal ($last4)"
                else -> "Withdrawal"
            }
            val accountLabel = if (bank != null && last4 != null) "$bank Card x$last4" else accountRaw
            val dateMatch = ISO_DATE_IN_TEXT_PATTERN.find(text)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = dateMatch?.value ?: extractDate(text),
                fullAccount = accountLabel,
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3.7: "Spent/Withdrawn Rs <amount> From/On <account> Bank Card <number> At <merchant>"
        match = WITHDRAWN_CARD_PATTERN.find(text)
        if (match != null) {
            var accountLabel = match.groups["account"]?.value?.trim()
            val cardLast4 = accountLabel?.let { BANK_CARD_LABEL_PATTERN.find(it) }?.groupValues?.get(2)
                ?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
            if (accountLabel != null) {
                val bankMatch = BANK_CARD_LABEL_PATTERN.find(accountLabel)
                accountLabel = if (bankMatch != null) {
                    "${bankMatch.groupValues[1]} Bank Card ${cleanCardNumber(bankMatch.groupValues[2]) ?: bankMatch.groupValues[2]}"
                } else {
                    cleanAccountName(accountLabel)
                }
            }
            val isWithdrawal = text.trimStart().startsWith("Withdrawn", ignoreCase = true)
            val merchant = if (isWithdrawal) {
                if (cardLast4 != null) "Withdrawal ($cardLast4)" else "Cash Withdrawal"
            } else {
                match.groups["merchant"]?.value?.trim()?.let { cleanMerchantName(it) }
            }
            val dateMatch = ISO_DATE_ONLY_PATTERN.find(text)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = dateMatch?.value ?: extractDate(text),
                fullAccount = cleanAccountName(accountLabel) ?: accountLabel,
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3.74: "<currency> <amount> spent on your <bank> Credit Card ending <number> at <merchant>"
        // Accepts INR/Rs/₹ and foreign currencies (USD, EUR, GBP, etc.) for international card spends.
        match = CREDIT_CARD_ENDING_PATTERN.find(text)
        if (match != null) {
            val cardNumber = cleanCardNumber(match.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val accountText = match.groups["account"]?.value?.trim() ?: ""
            val bankMatch = BANK_FIRST_WORD_PATTERN.find(accountText)
            val bankName = bankMatch?.groupValues?.get(1) ?: accountText.split(WHITESPACE_PATTERN).firstOrNull() ?: ""
            val accountLabel = "$bankName Card $cardNumber"
            var merchant = match.groups["merchant"]?.value?.trim()
            if (merchant != null) merchant = cleanMerchantName(merchant)
            val dateMatch = SIMPLE_DATE_SLASH_PATTERN.find(text)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = dateMatch?.value ?: extractDate(text),
                fullAccount = cleanAccountName(accountLabel) ?: accountLabel,
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3.75: "<currency> <amount> spent using/on ... Card <number> on <date> on/at <merchant>"
        // Supports all currencies (USD, EUR, INR, Rs, etc.) and amounts starting with decimal (.00)
        match = CREDIT_CARD_SPEND_PATTERN.find(text)
        if (match != null) {
            val cardNumber = cleanCardNumber(match.groups["cardNumber"]?.value?.trim() ?: extractCardNumber(text) ?: "XXXX") ?: "XXXX"
            var merchant = match.groups["merchant"]?.value?.trim()
            val accountMatch = SPEND_ACCOUNT_PATTERN.find(text)
            val accountFallbackMatch = SPEND_ACCOUNT_FALLBACK_PATTERN.find(text)
            val accountFlexibleMatch = if (accountMatch == null && accountFallbackMatch == null) {
                SPEND_ACCOUNT_FLEXIBLE_PATTERN.find(text)
            } else null

            var accountLabel = accountMatch?.groupValues?.get(1)?.trim()
                ?: accountFallbackMatch?.groupValues?.get(1)?.trim()
                ?: accountFlexibleMatch?.groupValues?.get(1)?.trim()

            if (accountLabel == null) {
                val bankMatch2 = BANK_NAME_IN_TEXT_PATTERN.find(text)
                val bankName = bankMatch2?.groupValues?.get(1) ?: extractBankName(text) ?: "Card"
                accountLabel = "$bankName Bank Card $cardNumber"
            } else {
                val bankCardMatch = BANK_CARD_FULL_PATTERN.find(accountLabel)
                accountLabel = if (bankCardMatch != null) {
                    "${bankCardMatch.groupValues[1]} Bank Card ${cleanCardNumber(bankCardMatch.groupValues[2]) ?: bankCardMatch.groupValues[2]}"
                } else {
                    cleanAccountName(accountLabel) ?: accountLabel
                }
            }

            if (merchant != null) {
                merchant = merchant.replace(NULL_PREFIX_PATTERN, "").trim()
                    .replace(TRAILING_PUNCTUATION_PATTERN, "").trim()
                merchant = cleanMerchantName(merchant)
            }
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = cleanAccountName(accountLabel) ?: accountLabel,
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3.76: "<currency> <amount> spent on <bank> Card <number> at <merchant> on <YYYY-MM-DD[:HH:MM:SS]>"
        // e.g. "Rs.1499 spent on HDFC Bank Card x3839 at Lifestyle MUM on 2025-02-23:17:17:27 Avl bal: ..."
        // Note: date is ISO YYYY-MM-DD optionally fused with time via colon (2025-02-23:17:17:27)
        match = HDFC_CARD_SPEND_AT_PATTERN.find(text)
        if (match != null) {
            var accountLabel = match.groups["account"]?.value?.trim()
            val bankCardMatch = accountLabel?.let { HDFC_BANK_CARD_PATTERN.find(it) }
            val bank = bankCardMatch?.groupValues?.get(1)?.trim() ?: extractBankName(text)
            val last4 = bankCardMatch?.groupValues?.get(2)
                ?.let { MASKED_CARD_LAST4_PATTERN.find(it) }
                ?.groupValues?.get(1)?.takeLast(4)
            accountLabel = if (bank != null && last4 != null) "$bank Card $last4"
                           else cleanAccountName(accountLabel) ?: accountLabel
            var merchant = match.groups["merchant"]?.value?.trim()
                ?.replace(MULTI_SPACE_IN_MERCHANT_PATTERN, " ")
                ?.trim()
            merchant = cleanMerchantName(merchant)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value,
                fullAccount = accountLabel,
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 3.8: ICICI debit UPI — "<bank> Acct <account> debited for Rs <amount> on <date>; <merchant> credited. UPI:<ref>"
        // e.g. "ICICI Bank Acct XX337 debited for Rs 190.00 on 23-Mar-26; SURAJ SATISHCHA credited. UPI:399433830715."
        match = ICICI_DEBIT_PATTERN.find(text)
        if (match != null) {
            val acct = match.groups["account"]?.value?.trim()
            val bankName = extractBankName(text) ?: "Bank"
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = "$bankName Acct $acct",
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 3.9: ICICI credit UPI — "Acct <account> is credited with Rs <amount> on <date> from <merchant>"
        // e.g. "Dear Customer, Acct XX337 is credited with Rs 200.00 on 01-Apr-26 from ABDURRAHMAN. UPI:609103588483-ICICI Bank."
        match = ICICI_CREDIT_PATTERN.find(text)
        if (match != null) {
            val acct = match.groups["account"]?.value?.trim()
            val bankName = extractBankName(text) ?: "Bank"
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = "$bankName Acct $acct",
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 3.91: SBI/UPI debit without currency prefix — "A/C <account> debited by <amount> on date <DDMMMYY> trf to <merchant> Refno <ref>"
        // e.g. "Dear UPI user A/C X4401 debited by 169.83 on date 29Mar25 trf to Innovative Retai Refno 508806212487. -SBI"
        match = SBI_UPI_DEBIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = match.groups["ref"]?.value ?: reference
            )
        }

        // Pattern 3.92: SBI debit card transaction — "transaction number <ref> for Rs.<amount> by <bank> Debit Card <card> done at <merchant> on <DDMMMYY>"
        // e.g. "transaction number 533718233750 for Rs.89.00 by SBI Debit Card X0914 done at 78004330 on 03Dec25 at 00:14:36."
        match = SBI_DEBIT_CARD_TXN_PATTERN.find(text)
        if (match != null) {
            val card = cleanCardNumber(match.groups["card"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val bank = match.groups["bank"]?.value?.trim() ?: extractBankName(text) ?: "Bank"
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = "$bank Debit Card $card",
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = match.groups["ref"]?.value ?: reference
            )
        }

        // Pattern 3.93: SBI YONO fund transfer — "Ac <account> debited Rs.<amount> for transfer to <merchant> Ac <toAcct> dt <DD.MM.YY> Ref <ref>"
        // e.g. "Your Ac x4401 debited Rs.3,00,000.00 for transfer to VANSHI Ac x8435 dt 04.04.26 Ref 209524221. YONO SBI"
        match = SBI_YONO_TRANSFER_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = match.groups["ref"]?.value ?: reference
            )
        }

        // Pattern 3.935: Indian Bank style — "A/c *<account> debited Rs. <amount> on <date> to <merchant>. UPI:<ref>"
        // e.g. "A/c *7097 debited Rs. 130.00 on 09-01-26 to Foodbook. UPI:600919109622."
        match = INDIAN_BANK_DEBIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = extractReference(text) ?: reference
            )
        }

        // Pattern 3.94: SBI credit by transfer — "A/c <account>-credited by Rs.<amount> on <DDMMMYY> transfer from <merchant> Ref No <ref>"
        // e.g. "Dear SBI User, your A/c X4401-credited by Rs.95000 on 31Mar26 transfer from VANSHIKA SAINI Ref No 120880161049 -SBI"
        match = SBI_CREDIT_TRANSFER_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = match.groups["ref"]?.value ?: reference
            )
        }

        // Pattern 3.941: South Indian Bank debit — "A/c <account> is debited with Rs.<amount> Info: <description>"
        // e.g. "Your A/c X1826 is debited with Rs.118.00 Info: SMS charges including GST-Qtly. Final balance is Rs.4642.93"
        // e.g. "Your A/c X1826 is debited with Rs.442.50 Info: AMC for debit card ending **8651 FY. Final balance is Rs.7720.55"
        match = SIB_DEBIT_PATTERN.find(text)
        if (match != null) {
            val info = match.groups["info"]?.value?.trim() ?: ""
            // Check if the Info field mentions a debit card — extract the card number if so
            val cardMatch = SIB_DEBIT_CARD_INFO_PATTERN.find(info)
            val merchantLabel = if (cardMatch != null) {
                val bankName = extractBankName(text) ?: "Bank"
                "$bankName Debit Card ${cardMatch.groupValues[1]}"
            } else {
                cleanMerchantName(info) ?: info
            }
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = merchantLabel,
                reference = reference
            )
        }

        // Pattern 3.942: South Indian Bank UPI credit — "UPI Credit:INR Rs.<amount> in A/c <account>. Info:UPI/<bank>/<ref>/ on <date>"
        // e.g. "UPI Credit:INR Rs.199.00 in A/c X1826. Info:UPI/HDFC/643955005964/ on 14-03-26 16:40:06."
        // Note: "INR Rs." is a redundant dual-prefix used by South Indian Bank; handled by optional Rs. after INR.
        match = SIB_UPI_CREDIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = match.groups["bank"]?.value?.trim()?.uppercase(),
                reference = match.groups["ref"]?.value ?: reference
            )
        }

        // Pattern 3.943: Mandate executed — "mandate for <merchant>, Rs.<amount> has been executed; Reference number: <ref>"
        // e.g. "Your mandate for Google Play, Rs.130.00 has been executed; Reference number: 273087220706"
        // e.g. "Dear Customer, Your mandate for APPLE MEDIA SERVICES, Rs.99.00 has been executed; Reference number: 103085136500- South Indian bank"
        match = MANDATE_EXECUTED_PATTERN.find(text)
        if (match != null) {
            val refMatch = REFERENCE_NUMBER_PATTERN.find(text)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = extractAccount(text),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = refMatch?.groupValues?.get(1) ?: reference
            )
        }

        // Pattern 3.95: BOI IMPS credit — "ac no. XXXX<digits> is credited for Rs. <amount> on <DDMMYY>"
        // e.g. "Your ac no. XXXX1128 is credited for Rs. 30000.00 on 060426 and XX3893 debited (IMPS Ref no. 609645072416)"
        match = BOI_IMPS_CREDIT_PATTERN.find(text)
        if (match != null) {
            val rawDate = match.groups["date"]?.value ?: ""
            val formattedDate = if (rawDate.length == 6) "${rawDate.substring(0,2)}/${rawDate.substring(2,4)}/${rawDate.substring(4,6)}" else rawDate
            val refMatch = IMPS_REF_PATTERN.find(text)
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = formattedDate,
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = extractBankName(text, null) ?: "IMPS Transfer",
                reference = refMatch?.groupValues?.get(1) ?: reference
            )
        }

        // Pattern 3.96: BOI/UPI debit with "s." amount prefix — "<s.amount> debited A/c<account> and credited to <merchant> via UPI"
        // e.g. "s.301.16 debited A/cXX1128 and credited to ZOMATO via UPI Ref No 300228298700 on 05Apr26"
        match = BOI_UPI_DEBIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 3.97: Utkarsh/generic SuperCard spend — "INR <amount> was spent on your SuperCard at <merchant>"
        // e.g. "INR 1,623.00 was spent on your SuperCard at Myntra . Available credit limit is now INR 9,047.67."
        match = SUPER_CARD_SPEND_PATTERN.find(text)
        if (match != null) {
            val cardNumbers = extract4DigitNumbers(text)
            val cardNum = cardNumbers.firstOrNull() ?: "XXXX"
            val bankName = extractBankName(text) ?: "Utkarsh"
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = "$bankName SuperCard $cardNum",
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 3.98: SuperCard UPI debit — "SuperCard <4digits> debited for <currency> <amount> on <DD Mon> for UPI - <ref>"
        // e.g. "Dear Jatin, your SuperCard 2839 debited for INR 110.00 on 19 Jan 02:01 PM for UPI - 638552361567."
        match = SUPER_CARD_UPI_DEBIT_PATTERN.find(text)
        if (match != null) {
            val bankName = extractBankName(text) ?: "Utkarsh"
            val upiRef = UPI_REF_DASH_PATTERN.find(text)?.groupValues?.get(1)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value?.trim(),
                fullAccount = "$bankName SuperCard ${match.groups["cardNum"]?.value}",
                merchant = "UPI",
                reference = upiRef ?: reference
            )
        }

        // Pattern 3.99: South Indian Bank / generic UPI debit colon format — "UPI debit:Rs.<amount>,A/c <account>, <date> <time> RRN:<ref>"
        // e.g. "UPI debit:Rs.15.00,A/c X1826, 06-04-26 19:20:05 RRN:121183120255. Bal:Rs.6488.73 Block A/c?..."
        // e.g. "UPI debit:Rs.99.00,A/c X1826, 09-04-26 21:22:52 RRN:103085136500. Bal:Rs.2029.59 Block A/c?Call18004251809/SMS BLK<A/c>to 9840777222-South Indian Bank"
        match = UPI_DEBIT_COLON_PATTERN.find(text)
        if (match != null) {
            val rrnMatch = RRN_PATTERN.find(text)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value?.trim(),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = transferMerchantLabel(text, null),
                reference = rrnMatch?.groupValues?.get(1) ?: reference
            )
        }

        // Pattern 3.99a: ACH/SIP/EMI auto-debit — "<currency> <amount> debited from <bank> <account> on <date>. Info: <description>"
        // e.g. "UPDATE: INR 5,000.00 debited from HDFC Bank XX5988 on 30-May-26. Info: ACH D-GOLD SIP-000CO9A9ZF123NHDW00. Avl bal: INR 3,74,934.59"
        // e.g. "UPDATE: INR 11,166.00 debited from HDFC Bank XX5988 on 05-SEP-24. Info: EMI 462175686 Chq S4621756860061 0924462175686."
        match = ACH_SIP_DEBIT_PATTERN.find(text)
        if (match != null) {
            val bank = match.groups["bank"]?.value?.trim() ?: extractBankName(text) ?: "Bank"
            val acct = match.groups["account"]?.value?.trim()
            val rawInfo = match.groups["info"]?.value?.trim() ?: ""
            // ACH D-GOLD SIP-<ref> → merchant = "Gold SIP", ref = last segment
            // EMI <ref> Chq ... → merchant = "EMI"
            val merchant = when {
                rawInfo.startsWith("EMI", ignoreCase = true) -> "EMI"
                rawInfo.contains("SIP", ignoreCase = true) -> {
                    val sipName = SIP_NAME_PATTERN
                        .find(rawInfo)?.groupValues?.get(1)?.trim()
                    if (sipName != null) "$sipName SIP" else "SIP"
                }
                rawInfo.contains("ACH", ignoreCase = true) -> "ACH Debit"
                else -> rawInfo
            }
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = if (acct != null) "$bank $acct" else acct,
                merchant = cleanMerchantName(merchant),
                reference = reference
            )
        }

        // Pattern 3.991: NEFT/RTGS/IMPS debit via NetBanking — "Rs.<amount> is debited from A/c <account> for NEFT transaction via <bank> NetBanking"
        // e.g. "Dear Customer, Rs.9,000.00 is debited from A/c XXXX5648 for NEFT transaction via HDFC Bank NetBanking. Call 18002586161 if txn not done by you"
        match = NEFT_NETBANKING_DEBIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = transferMerchantLabel(text, null),
                reference = reference
            )
        }

        // Pattern 4: "<currency> <amount> paid to / spent at / debited for <merchant>"
        match = DEBIT_PATTERN_3.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = extractAccount(text),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 4.5: Credit card payment "Rs <amount> credited to your card / RECEIVED TOWARDS YOUR CREDIT CARD ... ending <number>"
        match = CREDIT_CARD_PAYMENT_PATTERN.find(text)
        if (match != null) {
            val cardNumber = cleanCardNumber(match.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            var bankName = "Card"
            val bankMatch1 = CC_BANK_NAME_PATTERN.find(text)
            if (bankMatch1 != null) {
                val bankText = bankMatch1.groupValues[1]
                bankName = if (bankText.length > 4 && bankText.uppercase().endsWith("BANK")) {
                    bankText.replace(CC_BANK_SUFFIX_PATTERN, "").uppercase()
                } else {
                    bankText.uppercase()
                }
            } else {
                val cardmemberMatch = CC_CARDMEMBER_PATTERN.find(text)
                if (cardmemberMatch != null) {
                    bankName = cardmemberMatch.groupValues[1].uppercase()
                } else {
                    val commonBankMatch = COMMON_BANK_PATTERN.find(text)
                    if (commonBankMatch != null) bankName = commonBankMatch.groupValues[1].uppercase()
                }
            }
            val accountLabel = "$bankName Bank Card $cardNumber"
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = cleanAccountName(accountLabel) ?: accountLabel,
                merchant = "Credit Card Payment (ending $cardNumber)",
                reference = reference
            )
        }

        // Pattern 4.6: CC payment received on card — "Payment of Rs <amount> has been received on your <bank> Credit card <cardNumber> ... on <date>"
        // e.g. "Payment of Rs 52,452 has been received on your ICICI Bank Credit card XX0088 through bharat bill payment system on 08-Mar-26."
        match = CC_PAYMENT_RECEIVED_PATTERN.find(text)
        if (match != null) {
            val cardNumber = cleanCardNumber(match.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val bankRaw = match.groups["bank"]?.value?.trim() ?: ""
            val bankName = bankRaw.split(WHITESPACE_PATTERN).lastOrNull()?.uppercase() ?: extractBankName(text) ?: "Bank"
            val accountLabel = "$bankName Card $cardNumber"
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = cleanAccountName(accountLabel) ?: accountLabel,
                merchant = "Credit Card Payment (ending $cardNumber)",
                reference = reference
            )
        }

        // Pattern 4.7: CC payment credited without card number — "received payment of Rs.<amount> ... credited to your <bank> Credit Card"
        // e.g. "We have received payment of Rs.2,506.00 via UPI & the same has been credited to your SBI Credit Card."
        match = CC_PAYMENT_NO_CARD_PATTERN.find(text)
        if (match != null) {
            val bankName = match.groups["bank"]?.value?.trim()?.uppercase() ?: extractBankName(text) ?: "Bank"
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = "$bankName Credit Card",
                merchant = "Credit Card Payment",
                reference = reference
            )
        }

        // Pattern 4.8: NEFT credit — "<currency> <amount> credited to your A/c No <account> on <date> through NEFT with UTR <ref> by <merchant>"
        // e.g. "INR 950.00 credited to your A/c No XX4401 on 26/02/2026 through NEFT with UTR 000505123246 by TRUWORTH HEALTH TECHNOLOGIES PVT LT"
        match = NEFT_CREDIT_PATTERN.find(text)
        if (match != null) {
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = normalizeCurrencyCode(match.groups["currency"]?.value),
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = match.groups["ref"]?.value ?: reference
            )
        }

        // Pattern 5: "Rs <amount> credited/received/deposited to account/a/c <account> ... on <date>"
        match = CREDIT_PATTERN_2.find(text)
        if (match != null) {
            val vpa = extractVPA(text)
            val cardMatch = CC_IN_CREDIT_PATTERN.find(text)
            if (cardMatch != null) {
                val cn = cleanCardNumber(cardMatch.groupValues[1].trim()) ?: "XXXX"
                return ExtractedTransaction(
                    type = "credit",
                    amount = parseAmount(match.groups["amount"]?.value),
                    currency = "INR",
                    date = match.groups["date"]?.value ?: extractDate(text),
                    fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                    merchant = "Credit Card Payment (ending $cn)",
                    reference = reference
                )
            }
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = vpa,
                reference = reference
            )
        }

        // Pattern 6: "UPI payment of Rs <amount> to/for <merchant> on <date>"
        match = UPI_PATTERN.find(text)
        if (match != null) {
            val vpa = cleanMerchantName(extractVPA(text))
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = extractAccount(text),
                merchant = vpa ?: cleanMerchantName(match.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }

        // Pattern 6.5: "Paid Rs <amount> For: <merchant>"
        match = PAID_FOR_PATTERN.find(text)
        if (match != null) {
            var merchant = match.groups["merchant"]?.value?.trim()
            if (merchant != null && CREDIT_CARD_TEXT_PATTERN.containsMatchIn(merchant)) {
                val cardMatch = PAID_CC_ENDING_PATTERN.find(text)
                val cardSuffix = cardMatch?.groupValues?.get(1)?.trim()
                merchant = if (cardSuffix != null) "Credit Card Payment (ending $cardSuffix)" else "Credit Card Payment"
            }
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = extractAccount(text),
                merchant = merchant,
                reference = reference
            )
        }

        // Pattern 6.6: "Money Sent-<INR/Rs> <amount> From ... A/c <fromAcct> on <date> To A/c <toAcct>"
        // DOT_MATCHES_ALL needed: HDFC formats this as a multi-line SMS with \n between From/To lines
        match = MONEY_SENT_PATTERN.find(text)
        if (match != null) {
            val toAcct = match.groups["toAcct"]?.value?.trim()
            val merchantLabel = extractVPA(text) ?: transferMerchantLabel(text, toAcct)
            return ExtractedTransaction(
                type = "debit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["fromAcct"]?.value?.trim(),
                merchant = merchantLabel,
                reference = reference
            )
        }

        // Pattern 6.7: "Money received[-] <INR/Rs> <amount> in your ... A/c <account> on <date> by ... <byAcct>"
        match = MONEY_RECEIVED_PATTERN.find(text)
        if (match != null) {
            val byAcct = match.groups["byAcct"]?.value?.trim()
            val merchantLabel = extractVPA(text) ?: transferMerchantLabel(text, byAcct)
            return ExtractedTransaction(
                type = "credit",
                amount = parseAmount(match.groups["amount"]?.value),
                currency = "INR",
                date = match.groups["date"]?.value ?: extractDate(text),
                fullAccount = match.groups["account"]?.value?.trim(),
                merchant = merchantLabel,
                reference = reference
            )
        }

        // Pattern 7: Generic fallback — extract first currency+amount match and determine type from keywords
        match = AMOUNT_PATTERN.find(text)
        if (match != null) {
            val amount = parseAmount(match.groups["amount"]?.value)
            val currency = normalizeCurrencyCode(match.groups["currency"]?.value)
            val date = extractDate(text)
            val account = extractAccount(text)
            val vpa = extractVPA(text)

            val type: String? = when {
                CREDITED_TO_CARD_PATTERN.containsMatchIn(text) ||
                    RECEIVED_TOWARDS_CC_PATTERN.containsMatchIn(text) -> "credit"
                DEBIT_KEYWORDS_PATTERN.containsMatchIn(text) -> "debit"
                CREDIT_KEYWORDS_PATTERN.containsMatchIn(text) -> "credit"
                else -> null
            }

            // Credit card payments: check the SMS text itself, not the captured merchant
            val merchant: String? = if (CC_TEXT_PATTERN.containsMatchIn(text)) {
                val cardMatch = CC_ENDING_PATTERN.find(text)
                val suffix = cardMatch?.groupValues?.get(1)?.filter { it.isDigit() }?.takeLast(4)
                if (suffix != null) "Credit Card Payment (ending $suffix)" else "Credit Card Payment"
            } else if (vpa != null) {
                cleanMerchantName(vpa)
            } else {
                var found: String? = null
                for (pattern in SmsConstants.MERCHANT_PATTERNS) {
                    val captured = pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
                    if (captured.isNullOrEmpty() || captured.length !in 3..49) continue
                    // Reject if the entire capture is a known banking keyword
                    if (captured.lowercase() in SmsConstants.MERCHANT_PATTERN_BLOCKLIST) continue
                    // Reject if majority of characters are digits (account/ref number slipped through)
                    if (captured.count { it.isDigit() } > captured.length / 2) continue
                    // Reject if it looks like a date (dd/mm/yy, dd-mm-yy)
                    if (DATE_LIKE_PATTERN.matches(captured)) continue
                    found = cleanMerchantName(captured)
                    break
                }
                found
            }

            return ExtractedTransaction(
                type = type,
                amount = amount,
                currency = currency,
                date = date,
                fullAccount = account,
                merchant = cleanMerchantName(merchant),
                reference = reference,
                patternIndex = 7
            )
        }

        return empty.copy(reference = reference)
    }

    private fun applyMerchantOverrides(result: ExtractedTransaction, text: String): ExtractedTransaction {
        var updated = result.copy(fullAccount = cleanAccountName(result.fullAccount) ?: result.fullAccount)
        if (SALARY_PATTERN.containsMatchIn(text)) {
            return updated.copy(merchant = "Salary")
        }
        if (TOLL_CHARGES_PATTERN.containsMatchIn(text)) {
            return updated.copy(merchant = "Toll Charges")
        }
        return updated
    }

    fun normalizeDate(dateStr: String?): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (dateStr.isNullOrBlank()) return today

        val cleaned = dateStr.trim()

        val isoMatch = ISO_DATE_NORMALIZE_PATTERN.find(cleaned)
        if (isoMatch != null) {
            val year = isoMatch.groupValues[1].toIntOrNull() ?: 0
            val month = isoMatch.groupValues[2].toIntOrNull() ?: 0
            val day = isoMatch.groupValues[3].toIntOrNull() ?: 0
            if (year in 2000..2100 && month in 1..12 && day in 1..31) return cleaned
        }

        val monthNameMatch = MONTH_NAME_DATE_PATTERN.find(cleaned)
        if (monthNameMatch != null) {
            var day = monthNameMatch.groupValues[1].toIntOrNull() ?: return today
            val monthName = monthNameMatch.groupValues[2].lowercase().take(3)
            val month = MONTH_NUMBERS[monthName] ?: return today
            var year = monthNameMatch.groupValues[3].toIntOrNull() ?: return today
            year = normalizeYear(year)
            if (year !in 2000..2100) year = LocalDate.now().year
            if (month !in 1..12) return today
            if (day < 1 || day > daysInMonth(month, year)) return today
            return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        // Handle compact "DDMMMYYformat (no separator) like "29Mar25", "03Dec25", "31Mar26"
        val compactMonthMatch = COMPACT_MONTH_DATE_PATTERN.find(cleaned)
        if (compactMonthMatch != null) {
            val day = compactMonthMatch.groupValues[1].toIntOrNull() ?: return today
            val monthName = compactMonthMatch.groupValues[2].lowercase().take(3)
            val month = MONTH_NUMBERS[monthName] ?: return today
            var year = compactMonthMatch.groupValues[3].toIntOrNull() ?: return today
            year = normalizeYear(year)
            if (year !in 2000..2100) year = LocalDate.now().year
            if (month !in 1..12) return today
            if (day < 1 || day > daysInMonth(month, year)) return today
            return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        // Handle 6-digit compact DDMMYY — e.g. "060426" → 06/04/26
        val sixDigitMatch = SIX_DIGIT_DATE_PATTERN.find(cleaned)
        if (sixDigitMatch != null) {
            val day = sixDigitMatch.groupValues[1].toIntOrNull() ?: return today
            val month = sixDigitMatch.groupValues[2].toIntOrNull() ?: return today
            var year = sixDigitMatch.groupValues[3].toIntOrNull() ?: return today
            year = normalizeYear(year)
            if (year !in 2000..2100) year = LocalDate.now().year
            if (month !in 1..12) return today
            if (day < 1 || day > daysInMonth(month, year)) return today
            return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        // Handle "DD Mon" (no year) — e.g. "19 Jan" — assume current year
        val dayMonthMatch = DAY_MONTH_PATTERN.find(cleaned)
        if (dayMonthMatch != null) {
            val day = dayMonthMatch.groupValues[1].toIntOrNull() ?: return today
            val monthName = dayMonthMatch.groupValues[2].lowercase().take(3)
            val month = MONTH_NUMBERS[monthName] ?: return today
            val year = LocalDate.now().year
            if (month !in 1..12) return today
            if (day < 1 || day > daysInMonth(month, year)) return today
            return "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        // Handle partial DD-MM or DD/MM dates (no year) — assume current year
        val partialDateMatch = PARTIAL_DATE_PATTERN.find(cleaned)
        if (partialDateMatch != null) {
            val day = partialDateMatch.groupValues[1].toIntOrNull() ?: return today
            val month = partialDateMatch.groupValues[2].toIntOrNull() ?: return today
            val year = LocalDate.now().year
            if (month !in 1..12) return today
            if (day < 1 || day > daysInMonth(month, year)) return today
            return "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        val slashMatch = SLASH_DATE_PATTERN.find(cleaned)
        val dashMatch = DASH_DATE_PATTERN.find(cleaned)
        val dotMatch = DOT_DATE_PATTERN.find(cleaned)
        val parts = slashMatch ?: dashMatch ?: dotMatch ?: return today

        var day = parts.groupValues[1].toIntOrNull() ?: return today
        var month = parts.groupValues[2].toIntOrNull() ?: return today
        var year = parts.groupValues[3].toIntOrNull() ?: return today

        year = normalizeYear(year)
        if (year !in 2000..2100) year = LocalDate.now().year
        if (month !in 1..12) return today
        if (day < 1 || day > daysInMonth(month, year)) return today

        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    private fun normalizeYear(year: Int): Int {
        if (year >= 100) return year
        val currentYear = LocalDate.now().year
        val currentCentury = (currentYear / 100) * 100
        return if (year <= 50) {
            2000 + year
        } else {
            val opt1 = currentCentury + year
            val opt2 = 1900 + year
            if (kotlin.math.abs(opt1 - currentYear) < kotlin.math.abs(opt2 - currentYear)) opt1 else opt2
        }
    }

    internal fun normalizeCurrencyCode(raw: String?): String {
        if (raw.isNullOrBlank()) return "INR"
        val s = raw.trim().uppercase().replace(".", "")
        if (s == "RS" || s == "INR" || s == "₹") return "INR"
        if (s in SUPPORTED_CURRENCIES) return s
        return if (s.length == 3) s else "INR"
    }
}
