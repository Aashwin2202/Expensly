package com.fintrackai.domain.sms

import kotlin.math.roundToInt

data class CardTypeResult(val type: String, val isConfident: Boolean)

object TransactionCategorizer {

    suspend fun categorizeTransaction(
        extracted: ExtractedTransaction,
        getMerchantCategory: suspend (String) -> String?,
        remoteMerchantCategories: Map<String, String> = emptyMap(),
        remoteWordCategories: Map<String, String> = emptyMap()
    ): ExtractedTransaction {
        val merchantRaw = extracted.merchant ?: extracted.fullAccount
        var category: String? = null
        var extractedMerchantName: String? = null

        // 1. User-saved mapping (highest priority)
        if (merchantRaw != null) {
            try {
                category = getMerchantCategory(merchantRaw)
            } catch (_: Exception) {}
        }

        if (category == null) {
           if (merchantRaw != null) {
                val normalized = merchantRaw.lowercase().trim()

                // 2. Remote merchant mappings (from DB, synced from Supabase)
                if (remoteMerchantCategories.isNotEmpty()) {
                    val sortedRemoteMerchantKeys = remoteMerchantCategories.keys.sortedByDescending { it.length }
                    for (key in sortedRemoteMerchantKeys) {
                        val isMatch = if (key.length <= 4) {
                            Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
                        } else {
                            normalized.contains(key)
                        }
                        if (isMatch) {
                            category = remoteMerchantCategories[key]
                            extractedMerchantName = key.split("\\s+".toRegex())
                                .joinToString(" ") { word ->
                                    word.replaceFirstChar { it.uppercase() }
                                }
                            break
                        }
                    }
                }

                // 3. Local hardcoded merchant mappings (fallback)
                if (category == null) {
                    val sortedMerchantKeys = SmsConstants.MERCHANT_CATEGORIES.keys.sortedByDescending { it.length }
                    for (key in sortedMerchantKeys) {
                        val isMatch = if (key.length <= 4) {
                            Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
                        } else {
                            normalized.contains(key)
                        }
                        if (isMatch) {
                            category = SmsConstants.MERCHANT_CATEGORIES[key]
                            extractedMerchantName = key.split("\\s+".toRegex())
                                .joinToString(" ") { word ->
                                    word.replaceFirstChar { it.uppercase() }
                                }
                            break
                        }
                    }
                }

                // 4. Remote word mappings (from DB, synced from Supabase)
                if (category == null && remoteWordCategories.isNotEmpty()) {
                    val sortedRemoteWordKeys = remoteWordCategories.keys.sortedByDescending { it.length }
                    for (key in sortedRemoteWordKeys) {
                        val isMatch = if (key.length <= 4) {
                            Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
                        } else {
                            normalized.contains(key)
                        }
                        if (isMatch) {
                            category = remoteWordCategories[key]
                            break
                        }
                    }
                }

                // 5. Local hardcoded word mappings (final fallback)
                if (category == null) {
                    val sortedWordKeys = SmsConstants.WORD_CATEGORIES.keys.sortedByDescending { it.length }
                    for (key in sortedWordKeys) {
                        val isMatch = if (key.length <= 4) {
                            Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
                        } else {
                            normalized.contains(key)
                        }
                        if (isMatch) {
                            category = SmsConstants.WORD_CATEGORIES[key]
                            break
                        }
                    }
                }
            }
        }

        if (category == null) category = "others"
        return extracted.copy(
            category = category,
            accounts = extracted.fullAccount,
            merchant = extractedMerchantName ?: extracted.merchant
        )
    }

    suspend fun convertToAppTransaction(
        extracted: ExtractedTransaction,
        time: String,
        smsBody: String,
        senderAddress: String?,
        originalSms: String?,
        getAccountMapping: suspend (String, String) -> com.fintrackai.domain.model.AccountMapping?,
        saveAccountMapping: suspend (String, String, String, Boolean) -> Unit,
        messageDateStr: String? = null,
        getMerchantCountInStats: suspend (String) -> Boolean? = { null }
    ): com.fintrackai.domain.model.Transaction {
        val uniqueId = "${System.currentTimeMillis()}-${(1..9).map { ('a'..'z').random() }.joinToString("")}"

        val hasValidExtractedDate = extracted.date?.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) == true
        val hasValidMessageDate = messageDateStr?.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) == true
        val finalDate = when {
            hasValidMessageDate -> messageDateStr
            hasValidExtractedDate -> extracted.date
            else -> TransactionExtractor.normalizeDate(extracted.date)
        }

        var finalTime = time.ifEmpty { "00:00" }
        if (!finalTime.matches(Regex("^\\d{2}:\\d{2}$"))) finalTime = "00:00"
        else if (finalTime.startsWith("24:")) finalTime = "00:" + finalTime.substring(3)

        var amountInINR = extracted.amount ?: 0.0
        if (extracted.currency != null && extracted.currency != "INR" && (extracted.amount ?: 0.0) > 0) {
            try {
                amountInINR = ForexService.convertToINR(extracted.amount ?: 0.0, extracted.currency)
            } catch (_: Exception) {}
        }
        val roundedAmount = amountInINR.roundToInt().toDouble()

        val text = originalSms ?: smsBody
        val finalAccount = if (text.isNotEmpty()) {
            resolveAccount(text, senderAddress, extracted, getAccountMapping, saveAccountMapping)
        } else {
            extracted.accounts ?: extracted.fullAccount ?: ""
        }

        var countInStats = true
        if (senderAddress != null) {
            val isFromCreditCard = SmsTransactionalSenderCodes.isCreditCardTransactionalSender(senderAddress)
            val isFromBank = SmsTransactionalSenderCodes.isAllowedSender(senderAddress) && !isFromCreditCard
            if (isFromCreditCard && extracted.type == "credit") {
                countInStats = false
            } else if (isFromBank) {
                val isCCPayment1 = Regex(
                    """Paid\s+(?:Rs\.?|INR|₹|rs\.?)\s*\d+(?:[.,]\d{3})*(?:\.\d{1,2})?\s+For:\s*Credit\s+Card\s+payment""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(smsBody)
                val ccCompanyPattern = Regex("To\\s+(${SmsConstants.CREDIT_CARD_COMPANIES.joinToString("|")})", RegexOption.IGNORE_CASE)
                val isCCPayment2 = Regex(
                    """Sent\s+(?:Rs\.?|INR|₹|rs\.?)\s*\d+(?:[.,]\d{3})*(?:\.\d{1,2})?""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(smsBody) && ccCompanyPattern.containsMatchIn(smsBody)
                val isCCPaymentConfirm = Regex(
                    """Payment\s+of|PAYMENT\s+OF|credited\s+to\s+your\s+card|RECEIVED\s+TOWARDS\s+YOUR\s+CREDIT\s+CARD""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(smsBody)
                if (isCCPayment1 || isCCPayment2 || isCCPaymentConfirm) {
                    countInStats = false
                }
            }
        }
        if (extracted.category == "investment") countInStats = false
        if (Regex("""FASTag""", RegexOption.IGNORE_CASE).containsMatchIn(smsBody)) countInStats = false
        // Prepaid card top-ups (NCMC, wallet loads, etc.) are self-transfers, not income.
        if (Regex("""prepaid\s*card.*?\bloaded\b""", RegexOption.IGNORE_CASE).containsMatchIn(smsBody)) countInStats = false

        // Apply user-saved preference only when SMS rules haven't already excluded this transaction.
        if (countInStats) {
            val merchantForLookup = extracted.merchant
            if (!merchantForLookup.isNullOrBlank()) {
                val savedPreference = try { getMerchantCountInStats(merchantForLookup) } catch (_: Exception) { null }
                if (savedPreference != null) countInStats = savedPreference
            }
        }

        val cleanedMerchant = extracted.merchant ?: "Bank Transaction"

        val smsDedupeHash = if (text.isNotBlank()) {
            SmsDedupeHashHelper.contentHash(senderAddress, text)
        } else {
            null
        }

        return com.fintrackai.domain.model.Transaction(
            id = uniqueId,
            merchant = cleanedMerchant,
            amount = roundedAmount,
            type = extracted.type ?: "debit",
            category = extracted.category ?: "others",
            date = finalDate,
            time = finalTime,
            accounts = finalAccount,
            reference = extracted.reference,
            countInStats = countInStats,
            originalSms = originalSms,
            smsSender = senderAddress,
            smsDedupeHash = smsDedupeHash
        )
    }

    /**
     * Determines account type from SMS text signals.
     *
     * Credit card signals:
     *  - Explicit "credit card" phrase in message
     *  - Sender header contains CRD/CARD codes (bank credit card sender)
     *  - "spent on your ... Credit Card ending"
     *  - "Credit Card payment"
     *
     * Debit card signals:
     *  - Explicit "debit card" phrase
     *  - "withdrawn" / "withdrawal" (ATM cash withdrawal)
     *  - "ATM" with card reference (ATM card / ATM withdrawal)
     *  - "bank card" with withdrawal context
     *  - "POS" debit at merchant via card (point-of-sale debit)
     *  - "cash at atm" or "cash withdrawal"
     *
     * Falls back to "account" when no card signals are present.
     */
    private fun detectCardType(text: String, senderAddress: String?, extractedAccounts: String?, transactionType: String? = null): CardTypeResult {
        // --- Credit card indicators ---
        val hasCreditCardPhrase = Regex(
            """credit\s*card""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        val senderIsCreditCard = senderAddress != null &&
                SmsTransactionalSenderCodes.isCreditCardTransactionalSender(senderAddress)

        // --- Debit card indicators ---
        val hasDebitCardPhrase = Regex(
            """debit\s*card|prepaid\s*card""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        val hasWithdrawnKeyword = Regex(
            """withdraw[nal]*""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        val hasAtmReference = Regex(
            """atm|cash\s+at\s+atm|cash\s+withdrawal|atm\s+card""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        // Any occurrence of the word "card" in the message — covers:
        //   "spent Rs. 120 on HDFC bank card"
        //   "card ending 1234", "bank card", "your card"
        //   masked numbers like "card 813234XXXX9989"
        val hasCardWord = Regex("""\bcard\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

        // Masked/partial card number patterns: 813234XXXX9989, XXXX1234, 1234XXXX, etc.
        val hasMaskedCardNumber = Regex(
            """\d{4,}[Xx*]{2,}\d{2,}|\b[Xx*]{4}\d{4}\b|\d{6}[Xx*]{4,}\d{4}""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        // "card ending/no/number" or extractedAccounts contains "card"
        val hasGenericCardRef = Regex(
            """card\s+(?:ending|no\.?|number|xxxx)""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text) ||
                (extractedAccounts != null && Regex("card", RegexOption.IGNORE_CASE).containsMatchIn(extractedAccounts))

        val isDebitCard = hasDebitCardPhrase || hasWithdrawnKeyword || hasAtmReference

        // A credit transaction referencing a card with no debit-card signals is unambiguously a
        // credit card — refunds/reversals to debit cards say "credited to your account", not "card".
        val isCreditTransaction = transactionType == "credit"
        val hasAnyCardSignal = hasGenericCardRef || hasCardWord || hasMaskedCardNumber || hasCreditCardPhrase || senderIsCreditCard
        val isCreditCardFromTransactionType = isCreditTransaction && hasAnyCardSignal && !isDebitCard

        val isCreditCard = hasCreditCardPhrase || (senderIsCreditCard && !isDebitCard) ||
                ((hasGenericCardRef || hasCardWord || hasMaskedCardNumber) && !isDebitCard)

        // Confident debit: explicit "debit card" phrase, withdrawal/ATM keywords, or bank card + withdrawal context.
        // Confident credit: explicit "credit card" phrase, known credit-card sender, or a credit
        //   transaction with any card reference and no debit-card signals.
        // Everything else (generic "card" word on a debit transaction, masked number only) is ambiguous.
        val isConfidentDebit = isDebitCard
        val isConfidentCredit = isCreditCard && (hasCreditCardPhrase || senderIsCreditCard || isCreditCardFromTransactionType)
        val isConfident = isConfidentDebit || isConfidentCredit

        return when {
            isDebitCard  -> CardTypeResult("debit_card", true)
            isCreditCard -> CardTypeResult("credit_card", isConfident)
            else         -> CardTypeResult("account", true) // plain account has no ambiguity
        }
    }

    private suspend fun resolveAccount(
        text: String,
        senderAddress: String?,
        extracted: ExtractedTransaction,
        getAccountMapping: suspend (String, String) -> com.fintrackai.domain.model.AccountMapping?,
        saveAccountMapping: suspend (String, String, String, Boolean) -> Unit
    ): String {
        val accountDigits = TransactionExtractor.extract4DigitNumbers(text)
        if (accountDigits.isEmpty()) return extracted.fullAccount ?: ""

        val last4Digits = accountDigits[0]

        // Extract bank name early — needed as part of the composite key (last4Digits, bankName)
        // to disambiguate cards/accounts that share the same last 4 digits but belong to different banks.
        val bankName = TransactionExtractor.extractBankName(text, senderAddress) ?: return extracted.fullAccount ?: ""

        // Look up by (last4Digits, bankName) — accountType is NOT part of the key so the stored
        // type is always used, preventing heuristic drift from reclassifying an existing account.
        // Exception: a confident debit signal (withdrawn/ATM/debit card phrase) can override a
        // non-confident credit_card mapping, since debit withdrawals are unambiguous.
        val existingMapping = getAccountMapping(last4Digits, bankName)
        if (existingMapping != null && existingMapping.bankName.isNotBlank()) {
            val currentResult = detectCardType(text, senderAddress, extracted.accounts, extracted.type)
            val shouldOverride = existingMapping.accountType == "credit_card" &&
                    !existingMapping.isConfident &&
                    currentResult.type == "debit_card" &&
                    currentResult.isConfident
            if (shouldOverride) {
                saveAccountMapping(last4Digits, bankName, "debit_card", true)
                val accountLabel = "$bankName Debit Card $last4Digits"
                return TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel
            }
            val accountLabel = when (existingMapping.accountType) {
                "credit_card" -> "${existingMapping.bankName} Credit Card $last4Digits"
                "debit_card"  -> "${existingMapping.bankName} Debit Card $last4Digits"
                else          -> "${existingMapping.bankName} Bank A/C $last4Digits"
            }
            return TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel
        }

        // No mapping yet — detect type for the first time and persist it.
        val cardTypeResult = detectCardType(text, senderAddress, extracted.accounts, extracted.type)
        val accountType = cardTypeResult.type
        saveAccountMapping(last4Digits, bankName, accountType, cardTypeResult.isConfident)
        val accountLabel = when (accountType) {
            "credit_card" -> "$bankName Credit Card $last4Digits"
            "debit_card"  -> "$bankName Debit Card $last4Digits"
            else          -> "$bankName Bank A/C $last4Digits"
        }
        return TransactionExtractor.cleanAccountName(accountLabel) ?: accountLabel
    }
}
