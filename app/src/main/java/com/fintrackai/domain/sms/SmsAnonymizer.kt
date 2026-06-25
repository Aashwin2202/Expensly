package com.fintrackai.domain.sms

/**
 * Converts a raw bank SMS into a privacy-safe structural skeleton.
 *
 * Placeholder tokens:
 *   <AMOUNT>   — numeric amount (currency prefix is preserved: "Rs. <AMOUNT>")
 *   <BANK>     — bank name (HDFC, ICICI, SBI, …)
 *   <DATE>     — any date format
 *   <TIME>     — any time format
 *   <ACCTNUM>  — account number only (keyword preserved: "a/c <ACCTNUM>")
 *   <CARDNUM>  — card number last digits (keyword preserved: "card <CARDNUM>")
 *   <REF>      — reference/UTR/Txn ID number
 *   <VPA>      — UPI VPA (user@bank)
 *   <NUM>      — any remaining digit sequence
 */
object SmsAnonymizer {

    // Amount: capture currency prefix in group 1, mask only the digits
    private val AMOUNT_RE = Regex(
        """((?:rs\.?|inr|₹)\s*)[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE
    )

    // Bank names sourced from SmsConstants
    private val BANK_RE = Regex(
        """\b(${SmsConstants.BANK_NAMES.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE
    )

    // UPI VPA: local-part@domain
    private val VPA_RE = Regex("""[\w.+\-]{2,}@[\w.\-]+""", RegexOption.IGNORE_CASE)

    // Time: HH:MM or HH:MM:SS, or dot-separated HH.MM (e.g. "10.30 am"), optionally followed by am/pm
    private val TIME_RE = Regex(
        """\b\d{1,2}(?::\d{2}|[.:]\d{2})(?::\d{2})?(?:\s*[ap]m)?\b""",
        RegexOption.IGNORE_CASE
    )

    // Dates — most specific first.
    // End anchors use (?=[^a-z0-9]|$) instead of \b so formats fused with _ or . are captured.
    private val DATE_MONTH_NAME_RE = Regex(
        """\b\d{1,2}[-/](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-/]\d{2,4}(?=[^a-z0-9]|$)""",
        RegexOption.IGNORE_CASE
    )
    // Compact no-separator form: "14jun24", "05apr2024" — used by some banks in debit alerts
    private val DATE_COMPACT_RE = Regex(
        """\b\d{1,2}(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\d{2,4}(?=[^a-z0-9]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_ISO_RE = Regex("""\b\d{4}[-/]\d{2}[-/]\d{2}\b""")
    private val DATE_NUMERIC_RE = Regex("""\b\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\b""")

    // Account: group 1 = keyword, group 2 = leading digits (optional), group 3 = mask chars (optional), group 4 = last digits
    private val ACCOUNT_ALPHA_RE = Regex(
        """((?:a/?c\.?|account|acct)\s*(?:no\.?)?\s*)(\d*?)([*xX]+)(\d+)|""" +
        """((?:a/?c\.?|account|acct)\s*(?:no\.?)?\s*)\d+""",
        RegexOption.IGNORE_CASE
    )
    // Card: group 1 = keyword, group 2 = leading digits (optional), group 3 = mask chars (optional), group 4 = last digits
    private val ACCOUNT_CARD_RE = Regex(
        """((?:debit\s+card|credit\s+card|debit|card)\s*(?:ending\s+(?:with\s+)?|no\.?\s*)?)(\d*?)([*xX]+)(\d+)|""" +
        """((?:debit\s+card|credit\s+card|debit|card)\s*(?:ending\s+(?:with\s+)?|no\.?\s*)?)\d{4,}""",
        RegexOption.IGNORE_CASE
    )

    // Reference IDs — keyword followed by an alphanumeric code (pure-digit or mixed).
    // "vide" is used by some banks (e.g. HDFC credit card) as a ref-introducing keyword;
    // it may appear alone ("vide ABC123") or precede "ref#" ("vide ref# ABC123").
    private val REF_RE = Regex(
        """(?:(?:vide\s+)?ref(?:erence)?#?|utr|rrn[o]?|txn\s*(?:no\.?|id\.?)?|upi\s+ref(?:erence)?|order\s+(?:id|no\.?)|auth(?:orization)?\s*(?:code|no\.?)?|approval\s*(?:no\.?|code)?|trace\s*(?:no\.?|id)?|vide)\s*[:#]?\s*[a-z0-9]{3,}""",
        RegexOption.IGNORE_CASE
    )

    // Final sweep for any remaining digit sequences
    private val BARE_NUM_RE = Regex("""\d+""")

    // Trailing punctuation attached to word tokens — strip before returning skeleton
    private val TRAILING_PUNCT_RE = Regex("""[.,:;!\-]+$""")

    fun toSkeleton(text: String): String {
        var s = text.trim().replace(Regex("""\s+"""), " ").lowercase()

        s = VPA_RE.replace(s, "<VPA>")
        s = AMOUNT_RE.replace(s) { mr -> mr.groupValues[1] + "<AMOUNT>" }
        s = BANK_RE.replace(s, "<BANK>")
        s = TIME_RE.replace(s, "<TIME>")
        s = DATE_MONTH_NAME_RE.replace(s, "<DATE>")
        s = DATE_COMPACT_RE.replace(s, "<DATE>")
        s = DATE_ISO_RE.replace(s, "<DATE>")
        s = DATE_NUMERIC_RE.replace(s, "<DATE>")
        s = ACCOUNT_ALPHA_RE.replace(s) { mr ->
            if (mr.groupValues[3].isNotEmpty())  // masked form: digits + X's + last digits
                mr.groupValues[1] + (if (mr.groupValues[2].isNotEmpty()) "<NUM>" else "") + mr.groupValues[3] + "<ACCTNUM>"
            else
                mr.groupValues[5] + "<ACCTNUM>"
        }
        s = ACCOUNT_CARD_RE.replace(s) { mr ->
            if (mr.groupValues[3].isNotEmpty())  // masked form: digits + X's + last digits
                mr.groupValues[1] + (if (mr.groupValues[2].isNotEmpty()) "<NUM>" else "") + mr.groupValues[3] + "<CARDNUM>"
            else
                mr.groupValues[5] + "<CARDNUM>"
        }
        s = REF_RE.replace(s, "<REF>")
        s = BARE_NUM_RE.replace(s, "<NUM>")

        // Strip trailing punctuation from each space-separated token so "bal:" and "bal" and
        // "ref." all produce the same token.  Placeholders are left untouched.
        s = s.split(" ").joinToString(" ") { tok ->
            if (tok.startsWith("<") && tok.endsWith(">")) tok
            else TRAILING_PUNCT_RE.replace(tok, "")
        }

        return s.replace(Regex("""\s+"""), " ").trim()
    }

}
