package com.fintrackai.domain.sms

/**
 * Shared helpers injected into every pattern matcher so they don't have to recompute
 * common extractions (date, account, reference, VPA, etc.).
 */
data class PatternContext(
    val text: String,
    val parseAmount: (String?) -> Double?,
    val extractDate: (String) -> String?,
    val extractVPA: (String) -> String?,
    val extractAccount: (String) -> String?,
    val extractReference: (String) -> String?,
    val extractCardNumber: (String) -> String?,
    val reference: String?
)

/**
 * A single named pattern entry: a compiled [Regex] and a lambda that turns a successful
 * [MatchResult] + [PatternContext] into an [ExtractedTransaction].  Return null from
 * [extract] when the match doesn't produce a usable transaction.
 */
data class PatternEntry(
    val name: String,
    val regex: Regex,
    val extract: PatternContext.(MatchResult) -> ExtractedTransaction?
)

/**
 * Registry of per-bank [PatternEntry] lists and a shared fallback list.
 *
 * Matching order (enforced by [SmsPatternMatcher]):
 *   1. Bank-specific patterns for the detected bank (fast path — fewer regexes tried)
 *   2. Generic patterns (shared across all banks)
 *   3. Pattern-7 generic amount + keyword fallback (always last resort)
 *
 * Adding support for a new bank: add a key in [bankPatterns] whose key matches
 * [BankSenderDetector] output (i.e. matches an entry in [SmsConstants.BANK_NAMES]).
 */
object BankPatternRegistry {

    private const val CURRENCY_AMOUNT =
        """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)"""

    // ── HDFC ─────────────────────────────────────────────────────────────────

    private val hdfcPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "HDFC deposited",
            regex = Regex(
                """(?:Update!?\s*)?$CURRENCY_AMOUNT\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+for\s+(?<description>[^.]+?)(?:\.(?:Avl|$)|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val rawDescription = m.groups["description"]?.value?.trim() ?: ""
            // NEFT/RTGS/IMPS Cr- format: "<IFSC>-<merchant>-<sender name>-<UTR>"
            // e.g. "NEFT Cr-SBIN0004266-IIT ROORKEE-AASHWIN SHARMA-SBIN224242174867"
            val neftCrMatch = Regex(
                """^(?:NEFT|RTGS|IMPS)\s+Cr-[A-Z0-9]+-(.+?)-[A-Z0-9 ]+-[A-Z0-9]+$""",
                RegexOption.IGNORE_CASE
            ).find(rawDescription)
            val achCreditMatch = Regex(
                """ACH\s+C[-\s]+(?:SAL|SAL\s*)-\s*([A-Za-z][A-Za-z0-9]+)-""",
                RegexOption.IGNORE_CASE
            ).find(rawDescription)
            val merchant = when {
                neftCrMatch != null -> neftCrMatch.groupValues[1].trim()
                // FT structured format: "FT- <description>-<masked_acct> - <company> -"
                // e.g. "FT- Cnsltcy fee-XXXXXXXXXX8355 - VIHAAS DESIGN TECHNOLOGIES -"
                rawDescription.startsWith("FT", ignoreCase = true) -> {
                    val ftCompanyMatch = Regex(
                        """-\s*([A-Za-z][A-Za-z0-9\s&,./()-]{2,}?)\s*-\s*$"""
                    ).find(rawDescription)
                    ftCompanyMatch?.groupValues?.get(1)?.trim() ?: rawDescription
                }
                achCreditMatch != null -> {
                    val rawCompany = achCreditMatch.groupValues[1].trim()
                    if (rawCompany.matches(Regex("""[A-Z0-9]{4,20}"""))) "Salary"
                    else "Salary ($rawCompany)"
                }
                rawDescription.split("-").let { parts ->
                    parts.size >= 3 && parts[0].trim().matches(Regex("""[A-Z0-9]{6,11}"""))
                } -> {
                    val dashParts = rawDescription.split("-")
                    val company = dashParts[1].trim()
                    val purpose = dashParts[2].trim()
                    when {
                        purpose.equals("salary", ignoreCase = true) -> "Salary"
                        purpose.isNotBlank() && !purpose.matches(Regex("""X+""")) -> "$company ($purpose)"
                        else -> company
                    }
                }
                else -> rawDescription
            }
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(merchant),
                reference = reference
            )
        },

        PatternEntry(
            name = "HDFC spent on card at",
            regex = Regex(
                """$CURRENCY_AMOUNT\s+spent\s+on\s+(?<account>[A-Za-z][A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)\s+at\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s&*._-]+?)\s+on\s+(?<date>\d{4}-\d{2}-\d{2})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            var accountLabel = m.groups["account"]?.value?.trim()
            val bankCardMatch = accountLabel?.let {
                Regex("""^([A-Za-z][A-Za-z0-9\s&]+?)\s+(?:Bank\s+)?Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE).find(it)
            }
            val bank = bankCardMatch?.groupValues?.get(1)?.trim() ?: TransactionExtractor.extractBankName(text)
            val last4 = bankCardMatch?.groupValues?.get(2)
                ?.let { Regex("""[*Xx]*(\d{4,})$""").find(it) }
                ?.groupValues?.get(1)?.takeLast(4)
            accountLabel = if (bank != null && last4 != null) "$bank Card $last4"
            else TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value,
                fullAccount = accountLabel,
                merchant = TransactionExtractor.cleanMerchantName(
                    m.groups["merchant"]?.value?.trim()?.replace(Regex("""\s{2,}"""), " ")?.trim()
                ),
                reference = reference
            )
        },

        PatternEntry(
            name = "HDFC ACH/SIP debit",
            regex = Regex(
                """$CURRENCY_AMOUNT\s+debited\s+from\s+(?<bank>[A-Za-z]+(?:\s+Bank)?)\s+(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\.\s+Info\s*:\s*(?<info>[^.]+?)(?:\.\s*(?:Avl|$)|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val bank = m.groups["bank"]?.value?.trim() ?: TransactionExtractor.extractBankName(text) ?: "Bank"
            val acct = m.groups["account"]?.value?.trim()
            val rawInfo = m.groups["info"]?.value?.trim() ?: ""
            // ACH D- <COMPANY NAME>-<REF> or ACH D- <COMPANY NAME> SIP-<REF>
            val achCompanyMatch = Regex("""ACH\s+D[-\s]+([A-Za-z][A-Za-z0-9\s]+?)\s*(?:-[A-Z0-9]{6,}|$)""", RegexOption.IGNORE_CASE)
                .find(rawInfo)
            val merchant = when {
                achCompanyMatch != null && rawInfo.contains("SIP", ignoreCase = true) ->
                    "${achCompanyMatch.groupValues[1].trim()} SIP"
                achCompanyMatch != null ->
                    achCompanyMatch.groupValues[1].trim()
                rawInfo.contains("ACH", ignoreCase = true) -> "ACH Debit"
                else -> rawInfo
            }
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = if (acct != null) "$bank $acct" else acct,
                merchant = TransactionExtractor.cleanMerchantName(merchant),
                reference = reference
            )
        },

        PatternEntry(
            name = "HDFC Received IMPS/NEFT/UPI",
            regex = Regex(
                """Received!?\s*$CURRENCY_AMOUNT\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+On\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4})\s+For\s+(?:NEFT|RTGS|IMPS)\s*-\s*(?<merchant>.+?)\s*-\s*\d+""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ) { m ->
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "HDFC amt deducted",
            regex = Regex(
                """(?:Amt\s+Deducted!?\s*)?(?:Rs\.?|INR|₹)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+from\s+your\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+for\s+(?<description>[^.]+?)(?:\.|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["description"]?.value?.trim()),
                reference = reference
            )
        }
    )

    // ── SBI ──────────────────────────────────────────────────────────────────

    private val sbiPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "SBI UPI debit",
            regex = Regex(
                """A/?C\s+(?<account>[*Xx0-9]+)\s+debited\s+by\s+(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+on\s+date\s+(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})\s+trf\s+to\s+(?<merchant>[A-Za-z0-9\s]+?)\s+Refno\s+(?<ref>\d+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = m.groups["ref"]?.value ?: reference
            )
        },

        PatternEntry(
            name = "SBI debit card txn",
            regex = Regex(
                """transaction\s+number\s+(?<ref>\d+)\s+for\s+$CURRENCY_AMOUNT\s+by\s+(?<bank>[A-Za-z]+)\s+Debit\s+Card\s+(?<card>[*Xx0-9]+)\s+done\s+at\s+(?<merchant>[A-Za-z0-9]+)\s+on\s+(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val card = TransactionExtractor.cleanCardNumber(m.groups["card"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val bank = m.groups["bank"]?.value?.trim() ?: TransactionExtractor.extractBankName(text) ?: "Bank"
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = "$bank Debit Card $card",
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = m.groups["ref"]?.value ?: reference
            )
        },

        PatternEntry(
            name = "SBI YONO transfer",
            regex = Regex(
                """Ac\s+(?<account>[*Xx0-9]+)\s+debited\s+$CURRENCY_AMOUNT\s+for\s+transfer\s+to\s+(?<merchant>[A-Za-z0-9\s]+?)\s+Ac\s+(?<toAcct>[*Xx0-9]+)\s+dt\s+(?<date>\d{1,2}\.\d{1,2}\.\d{2,4})\s+Ref\s+(?<ref>\d+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = m.groups["ref"]?.value ?: reference
            )
        },

        PatternEntry(
            name = "SBI credit by transfer",
            regex = Regex(
                """A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)-?credited\s+by\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\d{2,4})\s+transfer\s+from\s+(?<merchant>[A-Za-z][A-Za-z\s]+?)\s+Ref\s+(?:No\.?\s+)?(?<ref>\d+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = m.groups["ref"]?.value ?: reference
            )
        },

        PatternEntry(
            name = "SBI NEFT via NetBanking",
            regex = Regex(
                """(?:Dear\s+Customer[,.]?\s*)?$CURRENCY_AMOUNT\s+is\s+debited\s+from\s+A/?[Cc]\.?\s*(?<account>[*Xx0-9]+)\s+for\s+(?:NEFT|RTGS|IMPS)\s+transaction\s+via\s+.*?NetBanking""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = "NEFT Transfer",
                reference = reference
            )
        }
    )

    // ── ICICI ─────────────────────────────────────────────────────────────────

    private val iciciPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "ICICI debit UPI",
            regex = Regex(
                """(?:[A-Za-z]+\s+)?(?:Bank\s+)?Acct\s+(?<account>[*Xx0-9]+)\s+debited\s+for\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4});\s*(?<merchant>[^.;]+?)\s+credited""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val acct = m.groups["account"]?.value?.trim()
            val bankName = TransactionExtractor.extractBankName(text) ?: "Bank"
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = "$bankName Acct $acct",
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "ICICI credit UPI",
            regex = Regex(
                """Acct\s+(?<account>[*Xx0-9]+)\s+is\s+credited\s+with\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+from\s+(?<merchant>[^.]+?)(?:\.\s*UPI|\.$|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val acct = m.groups["account"]?.value?.trim()
            val bankName = TransactionExtractor.extractBankName(text) ?: "Bank"
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = "$bankName Acct $acct",
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "ICICI CC payment received",
            regex = Regex(
                """Payment\s+of\s+$CURRENCY_AMOUNT\s+has\s+been\s+received\s+on\s+your\s+(?<bank>[A-Za-z\s]+?)\s+Credit\s+[Cc]ard\s+(?<cardNumber>[*Xx0-9]+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val cardNumber = TransactionExtractor.cleanCardNumber(m.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val bankRaw = m.groups["bank"]?.value?.trim() ?: ""
            val bankName = bankRaw.split(Regex("""\s+""")).lastOrNull()?.uppercase() ?: TransactionExtractor.extractBankName(text) ?: "Bank"
            val accountLabel = "$bankName Card $cardNumber"
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel,
                merchant = "Credit Card Payment (ending $cardNumber)",
                reference = reference
            )
        }
    )

    // ── South Indian Bank ────────────────────────────────────────────────────

    private val southIndianBankPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "SIB debit",
            regex = Regex(
                """A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)\s+is\s+debited\s+with\s+$CURRENCY_AMOUNT\s+Info\s*:\s*(?<info>[^.]+?)(?:\.\s*Final\s+balance|\.$|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val info = m.groups["info"]?.value?.trim() ?: ""
            val cardMatch = Regex("""debit\s+card\s+ending\s+[*]+(\d{4})""", RegexOption.IGNORE_CASE).find(info)
            val merchantLabel = if (cardMatch != null) {
                val bankName = TransactionExtractor.extractBankName(text) ?: "Bank"
                "$bankName Debit Card ${cardMatch.groupValues[1]}"
            } else {
                TransactionExtractor.cleanMerchantName(info) ?: info
            }
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = merchantLabel,
                reference = reference
            )
        },

        PatternEntry(
            name = "SIB UPI credit",
            regex = Regex(
                """UPI\s+Credit\s*:\s*(?:INR\s+)?$CURRENCY_AMOUNT\s+in\s+A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)\.?\s+Info\s*:\s*UPI/(?<bank>[A-Za-z0-9]+)/(?<ref>\d+)/""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = m.groups["bank"]?.value?.trim()?.uppercase(),
                reference = m.groups["ref"]?.value ?: reference
            )
        },

        PatternEntry(
            name = "SIB UPI debit colon",
            regex = Regex(
                """UPI\s+debit\s*:\s*$CURRENCY_AMOUNT\s*,\s*A/?[Cc]\.?\s+(?<account>[*Xx0-9]+)\s*,\s*(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val rrnMatch = Regex("""RRN\s*[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(text)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value?.trim(),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = null,
                reference = rrnMatch?.groupValues?.get(1) ?: reference
            )
        }
    )

    // ── Bank of India ────────────────────────────────────────────────────────

    private val boiPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "BOI IMPS credit",
            regex = Regex(
                """(?:ac\s+no\.?\s+)?(?:[*Xx]+)?(?<account>\d{4,})\s+is\s+credited\s+for\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{6})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val rawDate = m.groups["date"]?.value ?: ""
            val formattedDate = if (rawDate.length == 6)
                "${rawDate.substring(0, 2)}/${rawDate.substring(2, 4)}/${rawDate.substring(4, 6)}"
            else rawDate
            val refMatch = Regex("""(?:IMPS\s+Ref\s+no\.?|Ref\s+no\.?|Refno)\s+(\d+)""", RegexOption.IGNORE_CASE).find(text)
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = formattedDate,
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.extractBankName(text, null) ?: "IMPS Transfer",
                reference = refMatch?.groupValues?.get(1) ?: reference
            )
        },

        PatternEntry(
            name = "BOI UPI debit",
            regex = Regex(
                """[Ss]\.(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+debited\s+A/?[Cc](?<account>[*Xx0-9]+)\s+and\s+credited\s+to\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&-]*?)\s+via\s+UPI""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }
    )

    // ── Utkarsh ───────────────────────────────────────────────────────────────

    private val utkarshPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "Utkarsh SuperCard spend",
            regex = Regex(
                """$CURRENCY_AMOUNT\s+was\s+spent\s+on\s+your\s+SuperCard\s+at\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&*-]+?)(?:\s*\.|,|\s+Available|\s+View|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val cardNumbers = TransactionExtractor.extract4DigitNumbers(text)
            val cardNum = cardNumbers.firstOrNull() ?: "XXXX"
            val bankName = TransactionExtractor.extractBankName(text) ?: "Utkarsh"
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = "$bankName SuperCard $cardNum",
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "Utkarsh SuperCard UPI debit",
            regex = Regex(
                """SuperCard\s+(?<cardNum>\d{4})\s+debited\s+for\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val bankName = TransactionExtractor.extractBankName(text) ?: "Utkarsh"
            val upiRef = Regex("""UPI\s*[-–]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value?.trim(),
                fullAccount = "$bankName SuperCard ${m.groups["cardNum"]?.value}",
                merchant = "UPI",
                reference = upiRef ?: reference
            )
        }
    )

    // ── Indian Bank ───────────────────────────────────────────────────────────

    private val indianBankPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "Indian Bank debit",
            regex = Regex(
                """A/?[Cc]\.?\s+[*Xx]*(?<account>\d{4,})\s+debited\s+$CURRENCY_AMOUNT\s+on\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+to\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&-]+?)(?:\.\s*UPI|\.\s*Not\b|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = extractReference(text) ?: reference
            )
        }
    )

    // ── AXIS ─────────────────────────────────────────────────────────────────

    private val axisPatterns: List<PatternEntry> = listOf(

        // "Spent <currency> <amount>\nAxis Bank Card no. XX<last4>\n<date>\n<merchant>"
        // e.g. "Spent USD 23.6\nAxis Bank Card no. XX2912\n23-04-26 23:51:41 IST\nCURSOR, AI\nAvl Limit: INR 194185.55"
        PatternEntry(
            name = "Axis card spend",
            regex = Regex(
                """Spent\s+$CURRENCY_AMOUNT\s+Axis\s+Bank\s+Card\s+no\.\s+(?<card>[*Xx0-9]+)\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})[^\n]*\n(?<merchant>[^\n]+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
            )
        ) { m ->
            val last4 = m.groups["card"]?.value?.filter { it.isDigit() }?.takeLast(4)
            val currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = currency,
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = if (last4 != null) "Axis Bank Card $last4" else "Axis Bank Card",
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        }
    )

    // ── Per-bank map ──────────────────────────────────────────────────────────

    /**
     * Keys must match the bank strings returned by [BankSenderDetector.detect] and
     * entries in [SmsConstants.BANK_NAMES].
     */
    val bankPatterns: Map<String, List<PatternEntry>> = mapOf(
        "HDFC"             to hdfcPatterns,
        "SBI"              to sbiPatterns,
        "ICICI"            to iciciPatterns,
        "AXIS"             to axisPatterns,
        "SOUTH INDIAN BANK" to southIndianBankPatterns,
        "BOI"              to boiPatterns,
        "UTKARSH"          to utkarshPatterns,
        "INDIAN BANK"      to indianBankPatterns
    )

    // ── Generic patterns (tried for ANY bank after bank-specific fails) ───────

    val genericPatterns: List<PatternEntry> = listOf(

        PatternEntry(
            name = "transaction of at card",
            regex = Regex(
                """transaction\s+of\s+$CURRENCY_AMOUNT\s+at\s+(?<merchant>[A-Za-z0-9]+)\s+.*?debited\s+to\s+your\s+.*?(?:Card|CreduCard)\s+ending\s+(?<cardNumber>\d{4})\s+on\s+(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val cardNumber = TransactionExtractor.cleanCardNumber(m.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val bankName = TransactionExtractor.extractBankName(text) ?: "SBI"
            val accountLabel = "$bankName Bank Card $cardNumber"
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel,
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim() ?: ""),
                reference = reference
            )
        },

        PatternEntry(
            name = "amount debited from a/c to a/c",
            regex = Regex(
                """(?:Sms[:\s]*)?(?:[A-Za-z\s]+:)?\s*(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s*debited\s+from\s+(?:a/?c\.?\s*)?(?<fromAcct>[*Xx0-9A-Za-z-]+).*?(?:on\s+)?(?<date>\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?).*?to\s+(?:a/?c\.?\s*)?(?<toAcct>[*Xx0-9A-Za-z-]+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val toAcct = m.groups["toAcct"]?.value?.trim()
            val merchant = extractVPA(text) ?: TransactionExtractor.transferMerchantLabel(text, toAcct)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["fromAcct"]?.value?.trim(),
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "Amt Sent",
            regex = Regex(
                """(?:Amt\s+)?Sent\s+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)[\s\S]*?From\s+(?<fromAcct>[^\n]+)[\s\S]*?To\s+(?<toAcct>[^\n]+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val toAcct = m.groups["toAcct"]?.value?.trim()
            val vpa = extractVPA(text)
            val toIsAccount = Regex("""To\s+a/?c""", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val merchant = when {
                vpa != null -> vpa
                toIsAccount -> TransactionExtractor.transferMerchantLabel(text, toAcct)
                else -> toAcct
            }
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = m.groups["fromAcct"]?.value?.trim(),
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "Credit Alert a/c",
            regex = Regex(
                """(?:Sms[:\s]*)?.*?(?:Credit\s+Alert!|credited|credit)\s+(?:to\s+)?(?:HDFC\s+Bank\s+)?(?:a/?c\.?\s*)?(?<account>[*Xx0-9A-Za-z\s-]+)?.*?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)[\s\S]*?(?:on\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(extractVPA(text)),
                reference = reference
            )
        },

        PatternEntry(
            name = "salary deposit FT",
            regex = Regex(
                """(?:Update!?\s*)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+).*?(?:on\s+)?(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}).*?for\s+FT[-:\s]+(?<vendor>[A-Z0-9]+[A-Z0-9X]*)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            var cleanVendor = m.groups["vendor"]?.value?.trim()
            if (cleanVendor != null) {
                cleanVendor = cleanVendor.replace(Regex("""X{10,}$"""), "").replace(Regex("""X+$"""), "")
                if (cleanVendor.length < 3) cleanVendor = "Salary"
            }
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = cleanVendor ?: "Salary",
                reference = reference
            )
        },

        PatternEntry(
            name = "deposit with merchant",
            regex = Regex(
                """(?:Update!?\s*)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+).*?(?:on\s+)?(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}).*?for\s+(?<referenceNum>\d+)\s+(?<merchant>[A-Z][A-Za-z0-9\s]+?)(?:\s+\d{6,}|\s*\.|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            var merchant = m.groups["merchant"]?.value?.trim()
            if (merchant != null) {
                merchant = merchant.replace(Regex("""\s+\d{6,}.*$"""), "").trim()
                    .replace(Regex("""\s+[A-Z]{2,4}$"""), "").trim()
                if (merchant.length < 2) merchant = null
            }
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(merchant),
                reference = m.groups["referenceNum"]?.value?.trim() ?: reference
            )
        },

        PatternEntry(
            name = "NEFT/RTGS/IMPS deposit",
            regex = Regex(
                """(?:Update!?\s*)?$CURRENCY_AMOUNT\s+deposited\s+in\s+.*?(?:a/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/]\w{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+for\s+(?:NEFT|RTGS|IMPS)\s+Cr-[A-Z0-9]+-(?<merchant>.+?)-[A-Z0-9 ]+-[A-Z0-9]+(?:\.|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "FASTag debit",
            regex = Regex(
                """FASTag\s+Acc.*?(?:ending\s+with\s+)?(?<account>\d{4,})\s+VRN\s*:\s*[A-Z0-9]+\s+debited\s+(?:Rs\.?|INR|₹)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?)\s+(?<chargeType>[A-Za-z\s]+?)\s+at\s+[A-Za-z0-9\s]+\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val chargeType = m.groups["chargeType"]?.value?.trim()?.takeIf { it.isNotBlank() } ?: "Toll Charges"
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(chargeType),
                reference = reference
            )
        },

        PatternEntry(
            name = "ATM withdrawal",
            regex = Regex(
                """(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+withdrawn\s+from\s+(?<account>[A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val accountRaw = m.groups["account"]?.value?.trim()
            val bankMatch = Regex("""^([A-Za-z0-9\s&]+?)\s+(?:Bank\s+)?Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE).find(accountRaw ?: "")
            val bank = bankMatch?.groupValues?.get(1)?.trim() ?: TransactionExtractor.extractBankName(text)
            val last4 = bankMatch?.groupValues?.get(2)
                ?.let { Regex("""[*Xx]*(\d{4,})$""").find(it) }
                ?.groupValues?.get(1)?.takeLast(4)
            val merchant = when {
                last4 != null -> "Withdrawal ($last4)"
                else -> "Withdrawal"
            }
            val accountLabel = if (bank != null && last4 != null) "$bank Card x$last4" else accountRaw
            val dateMatch = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}""").find(text)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = dateMatch?.value ?: extractDate(text),
                fullAccount = accountLabel,
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "Spent/Withdrawn card at",
            regex = Regex(
                """(?:Spent|Withdrawn)\s+(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:From|On)\s+(?<account>[A-Z][A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)\s+At\s+(?<merchant>[+\-]?[A-Za-z0-9][A-Za-z0-9\s.+\-]*?)(?:\s+On\s+\d|\s+Bal\b|\s+Not\b|\.Not\b|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            var accountLabel = m.groups["account"]?.value?.trim()
            val bankCardRegex = Regex("""^([A-Z][A-Za-z0-9\s&]+?)\s+Bank\s+Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE)
            val cardLast4 = accountLabel?.let { bankCardRegex.find(it) }?.groupValues?.get(2)
                ?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
            if (accountLabel != null) {
                val bm = bankCardRegex.find(accountLabel)
                accountLabel = if (bm != null) {
                    "${bm.groupValues[1]} Bank Card ${TransactionExtractor.cleanCardNumber(bm.groupValues[2]) ?: bm.groupValues[2]}"
                } else {
                    TransactionExtractor.cleanAccountName(accountLabel)
                }
            }
            val isWithdrawal = text.trimStart().startsWith("Withdrawn", ignoreCase = true)
            val merchant = if (isWithdrawal) {
                if (cardLast4 != null) "Withdrawal ($cardLast4)" else "Cash Withdrawal"
            } else {
                m.groups["merchant"]?.value?.trim()?.let { TransactionExtractor.cleanMerchantName(it) }
            }
            val dateMatch = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""").find(text)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = dateMatch?.value ?: extractDate(text),
                fullAccount = TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel,
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "spent on CC ending",
            regex = Regex(
                """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+spent\s+on\s+your\s+(?<account>[A-Z][A-Za-z0-9\s&]+?\s+Credit\s+Card)\s+ending\s+(?<cardNumber>[\dXx*]+)\s+at\s+(?<merchant>[A-Za-z0-9\s&]+?)(?:\s+on\s+|\s+at\s+|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val cardNumber = TransactionExtractor.cleanCardNumber(m.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            val accountText = m.groups["account"]?.value?.trim() ?: ""
            val bankMatch = Regex("""^([A-Z][A-Za-z0-9]+)""").find(accountText)
            val bankName = bankMatch?.groupValues?.get(1) ?: accountText.split(Regex("""\s+""")).firstOrNull() ?: ""
            val accountLabel = "$bankName Card $cardNumber"
            var merchant = m.groups["merchant"]?.value?.trim()
            if (merchant != null) merchant = TransactionExtractor.cleanMerchantName(merchant)
            val dateMatch = Regex("""\d{1,2}/\d{1,2}/\d{2,4}""").find(text)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = dateMatch?.value ?: extractDate(text),
                fullAccount = TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel,
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "spent using/on card date on/at",
            regex = Regex(
                """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{2,3})*(?:\.\d{1,2})?|\.\d{1,2})\s+spent\s+(?:using\s+|on\s+)[^.]*?(?:Card|card)\s+(?<cardNumber>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+(?:on|at)\s+(?<merchant>(?:null\*)?[A-Za-z0-9*& -]+?)(?:\.|,|\s+Avl\b|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val cardNumber = TransactionExtractor.cleanCardNumber(
                m.groups["cardNumber"]?.value?.trim() ?: extractCardNumber(text) ?: "XXXX"
            ) ?: "XXXX"
            var merchant = m.groups["merchant"]?.value?.trim()
            val accountMatch = Regex("""([A-Z][A-Za-z0-9\s&]+?\s+Bank\s+Card\s+[*Xx0-9]+)""", RegexOption.IGNORE_CASE).find(text)
            val accountFallbackMatch = Regex("""spent\s+(?:using\s+|on\s+)([A-Z][A-Za-z0-9\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)""", RegexOption.IGNORE_CASE).find(text)
            val accountFlexibleMatch = if (accountMatch == null && accountFallbackMatch == null)
                Regex("""([A-Z][A-Za-z\s&]+?\s+(?:Bank\s+)?Card\s+[*Xx0-9]+)""", RegexOption.IGNORE_CASE).find(text)
            else null

            var accountLabel = accountMatch?.groupValues?.get(1)?.trim()
                ?: accountFallbackMatch?.groupValues?.get(1)?.trim()
                ?: accountFlexibleMatch?.groupValues?.get(1)?.trim()

            if (accountLabel == null) {
                val bm = Regex("""([A-Z][A-Za-z]+)\s+Bank""", RegexOption.IGNORE_CASE).find(text)
                val bankName = bm?.groupValues?.get(1) ?: TransactionExtractor.extractBankName(text) ?: "Card"
                accountLabel = "$bankName Bank Card $cardNumber"
            } else {
                val bm = Regex("""^([A-Z][A-Za-z0-9\s&]+?)\s+Bank\s+Card\s+([*Xx0-9]+)""", RegexOption.IGNORE_CASE).find(accountLabel)
                accountLabel = if (bm != null) {
                    "${bm.groupValues[1]} Bank Card ${TransactionExtractor.cleanCardNumber(bm.groupValues[2]) ?: bm.groupValues[2]}"
                } else {
                    TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel
                }
            }
            if (merchant != null) {
                merchant = merchant.replace(Regex("""^null\*""", RegexOption.IGNORE_CASE), "").trim()
                    .replace(Regex("""[.,]+$"""), "").trim()
                merchant = TransactionExtractor.cleanMerchantName(merchant)
            }
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel,
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "CC payment credited to card",
            regex = Regex(
                """(?:Payment\s+of\s+|PAYMENT\s+OF\s+)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:was\s+)?(?:credited\s+to\s+(?:your\s+)?(?:card|credit\s+card)|RECEIVED\s+TOWARDS\s+YOUR\s+CREDIT\s+CARD).*?(?:ending|no\.?|number|ENDING\s+WITH)\s*(?<cardNumber>[\dXx*]+)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val cardNumber = TransactionExtractor.cleanCardNumber(m.groups["cardNumber"]?.value?.trim() ?: "XXXX") ?: "XXXX"
            var bankName = "Card"
            val bankMatch1 = Regex("""([A-Z]{2,})(?:BANK|BANK\s+CARD|CARD|CARDMEMBER)""", RegexOption.IGNORE_CASE).find(text)
            if (bankMatch1 != null) {
                val bankText = bankMatch1.groupValues[1]
                bankName = if (bankText.length > 4 && bankText.uppercase().endsWith("BANK"))
                    bankText.replace(Regex("""BANK$""", RegexOption.IGNORE_CASE), "").uppercase()
                else bankText.uppercase()
            } else {
                val cardmemberMatch = Regex("""DEAR\s+([A-Z]{2,})(?:BANK)?\s+CARDMEMBER""", RegexOption.IGNORE_CASE).find(text)
                if (cardmemberMatch != null) {
                    bankName = cardmemberMatch.groupValues[1].uppercase()
                } else {
                    val commonBankMatch = Regex("""\b(HDFC|SBI|ICICI|AXIS|KOTAK|PNB|PUNJAB|YES|RBL)\b""", RegexOption.IGNORE_CASE).find(text)
                    if (commonBankMatch != null) bankName = commonBankMatch.groupValues[1].uppercase()
                }
            }
            val accountLabel = "$bankName Bank Card $cardNumber"
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel,
                merchant = "Credit Card Payment (ending $cardNumber)",
                reference = reference
            )
        },

        PatternEntry(
            name = "CC payment no card number",
            regex = Regex(
                """received\s+payment\s+of\s+$CURRENCY_AMOUNT\s+.*?credited\s+to\s+your\s+(?<bank>[A-Za-z]+)\s+Credit\s+Card""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ) { m ->
            val bankName = m.groups["bank"]?.value?.trim()?.uppercase() ?: TransactionExtractor.extractBankName(text) ?: "Bank"
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = "$bankName Credit Card",
                merchant = "Credit Card Payment",
                reference = reference
            )
        },

        PatternEntry(
            name = "NEFT credit UTR by merchant",
            regex = Regex(
                """$CURRENCY_AMOUNT\s+credited\s+to\s+your\s+A/?[Cc]\.?\s+(?:No\.?\s+)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+through\s+NEFT\s+with\s+UTR\s+(?<ref>\d+)\s+by\s+(?<merchant>[A-Za-z][A-Za-z0-9\s&]+?)(?:\s*,|\s*-SBI|\s*$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = m.groups["ref"]?.value ?: reference
            )
        },

        PatternEntry(
            name = "credited/received/deposited",
            regex = Regex(
                """(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:credited|received|deposited)\s+(?:to\s+)?(?:account|a/?c\.?)?\s*(?<account>[*Xx0-9A-Za-z-]+)?.*?(?:on\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val vpa = extractVPA(text)
            val cardMatch = Regex("""(?:CREDIT\s+CARD|credit\s+card).*?(?:ending|no\.?|number|ENDING\s+WITH)\s*([\dXx*]+)""", RegexOption.IGNORE_CASE).find(text)
            if (cardMatch != null) {
                val cn = TransactionExtractor.cleanCardNumber(cardMatch.groupValues[1].trim()) ?: "XXXX"
                return@PatternEntry ExtractedTransaction(
                    type = "credit",
                    amount = parseAmount(m.groups["amount"]?.value),
                    currency = "INR",
                    date = m.groups["date"]?.value ?: extractDate(text),
                    fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                    merchant = "Credit Card Payment (ending $cn)",
                    reference = reference
                )
            }
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim() ?: extractAccount(text),
                merchant = vpa,
                reference = reference
            )
        },

        PatternEntry(
            name = "Rs paid to/spent at/debited for",
            regex = Regex(
                """(?<currency>USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED|Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:paid\s+to|spent\s+at|debited\s+for)\s+(?<merchant>[^\n,]+?)(?:\s+on\s+|\s+at\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})?""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "UPI payment to merchant",
            regex = Regex(
                """(?:UPI|upi)\s+(?:payment|transaction)\s+(?:of\s+)?(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+(?:to|for)\s+(?<merchant>[^\n,]+?)(?:\s+on\s+)?(?<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})?""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val vpa = TransactionExtractor.cleanMerchantName(extractVPA(text))
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = extractAccount(text),
                merchant = vpa ?: TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = reference
            )
        },

        PatternEntry(
            name = "Paid Rs For",
            regex = Regex(
                """(?:Alert!?\s*)?Paid\s+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+For:\s*(?<merchant>[^\n,]+?)(?:\s+From|\s+Via|\s+on\s+|\s+at\s+|$)""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            var merchant = m.groups["merchant"]?.value?.trim()
            if (merchant != null && Regex("""credit\s+card""", RegexOption.IGNORE_CASE).containsMatchIn(merchant)) {
                val cardMatch = Regex("""(?:ending|no\.?|number|ENDING\s+WITH)\s*([\dXx*]+)""", RegexOption.IGNORE_CASE).find(text)
                val cardSuffix = cardMatch?.groupValues?.get(1)?.trim()
                merchant = if (cardSuffix != null) "Credit Card Payment (ending $cardSuffix)" else "Credit Card Payment"
            }
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = extractDate(text),
                fullAccount = extractAccount(text),
                merchant = merchant,
                reference = reference
            )
        },

        PatternEntry(
            name = "Money Sent from/to",
            regex = Regex(
                """Money\s+Sent[-\s]+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+From\s+.*?(?:A/?c\.?\s*)?(?<fromAcct>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?)\s+To\s+(?:A/?c\.?\s*)?(?<toAcct>[*Xx0-9]+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ) { m ->
            val toAcct = m.groups["toAcct"]?.value?.trim()
            val merchantLabel = extractVPA(text) ?: TransactionExtractor.transferMerchantLabel(text, toAcct)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["fromAcct"]?.value?.trim(),
                merchant = merchantLabel,
                reference = reference
            )
        },

        PatternEntry(
            name = "Money received in",
            regex = Regex(
                """Money\s+received[-\s]+(?:Rs\.?|INR|₹|rs\.?)\s*(?<amount>\d+(?:[.,]\d{3})*(?:\.\d{1,2})?)\s+in\s+your\s+.*?(?:A/?c\.?\s*)?(?<account>[*Xx0-9]+)\s+on\s+(?<date>\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?).*?by\s+.*?(?<byAcct>[*Xx]+\d{4,})""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val byAcct = m.groups["byAcct"]?.value?.trim()
            val merchantLabel = extractVPA(text) ?: TransactionExtractor.transferMerchantLabel(text, byAcct)
            ExtractedTransaction(
                type = "credit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = "INR",
                date = m.groups["date"]?.value ?: extractDate(text),
                fullAccount = m.groups["account"]?.value?.trim(),
                merchant = merchantLabel,
                reference = reference
            )
        },

        PatternEntry(
            name = "mandate executed",
            regex = Regex(
                """mandate\s+for\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9\s.&,-]+?),\s*$CURRENCY_AMOUNT\s+has\s+been\s+executed""",
                RegexOption.IGNORE_CASE
            )
        ) { m ->
            val refMatch = Regex("""Reference\s+(?:number|no\.?)\s*:\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
            ExtractedTransaction(
                type = "debit",
                amount = parseAmount(m.groups["amount"]?.value),
                currency = TransactionExtractor.normalizeCurrencyCode(m.groups["currency"]?.value),
                date = extractDate(text),
                fullAccount = extractAccount(text),
                merchant = TransactionExtractor.cleanMerchantName(m.groups["merchant"]?.value?.trim()),
                reference = refMatch?.groupValues?.get(1) ?: reference
            )
        }
    )
}
