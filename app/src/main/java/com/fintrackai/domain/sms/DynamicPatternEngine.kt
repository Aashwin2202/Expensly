package com.fintrackai.domain.sms

import android.util.Log
import com.fintrackai.data.local.db.SmsPatternEntity

object DynamicPatternEngine {

    // Guard against backends that serialise null as the string "null"
    private fun String?.nullIfStringNull(): String? = if (this == "null") null else this

    fun tryMatch(
        pattern: SmsPatternEntity,
        text: String,
        detectedBank: String? = null
    ): ExtractedTransaction? {
        val options = pattern.regexOptions.split(",").filter { it.isNotBlank() }.mapNotNull {
            when (it.trim()) {
                "IGNORE_CASE" -> RegexOption.IGNORE_CASE
                "DOT_MATCHES_ALL" -> RegexOption.DOT_MATCHES_ALL
                "MULTILINE" -> RegexOption.MULTILINE
                else -> null
            }
        }.toSet()

        val regex = try {
            Regex(pattern.regex.replace("\\\\", "\\"), options)
        } catch (e: Exception) {
            Log.w("DynamicPattern", "Invalid regex for pattern ${pattern.id}: ${pattern.regex} — ${e.message}")
            return null
        }

        val match = regex.find(text) ?: return null

        val amount = pattern.groupAmount.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.replace(",", "")?.toDoubleOrNull()
        }

        val currency = pattern.groupCurrency.nullIfStringNull()?.let { name ->
            match.groups[name]?.value
        }?.let { TransactionExtractor.normalizeCurrencyCode(it) }
            ?: pattern.defaultCurrency

        val date = pattern.groupDate.nullIfStringNull()?.let { name ->
            match.groups[name]?.value
        }

        // Static merchant template takes priority over groupMerchant.
        // Placeholders like {groupName} are replaced with the corresponding named capture group value.
        // Values substituted into the template are uppercased so bank names (HDFC, SBI, etc.)
        // are always rendered correctly regardless of how they appear in the raw SMS.
        val merchant: String? = pattern.merchant.nullIfStringNull()?.let { template ->
            val resolved = Regex("""\{(\w+)\}""").replace(template) { placeholder ->
                val groupName = placeholder.groupValues[1]
                match.groups[groupName]?.value?.trim()?.uppercase() ?: placeholder.value
            }
            resolved.ifBlank { null }
        } ?: pattern.groupMerchant.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.trim()
        }?.let { raw ->
            if (pattern.cleanMerchant) TransactionExtractor.cleanMerchantName(raw) else raw
        }

        val account = pattern.groupAccount.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.trim()
        }

        val reference = pattern.groupReference.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.trim()
        }

        val cardNumber = pattern.groupCardNumber.nullIfStringNull()?.let { name ->
            TransactionExtractor.cleanCardNumber(match.groups[name]?.value?.trim())
        }

        val bankName = pattern.groupBankName.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.trim()?.uppercase()
        }

        val balance = pattern.groupBalance.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.replace(",", "")?.toDoubleOrNull()
        }

        // Available credit limit (credit-card SMS). Prefer the pattern's named group; fall back
        // to scanning the SMS text so dynamic matches still capture it when no group is defined.
        val creditLimit = pattern.groupCreditLimit.nullIfStringNull()?.let { name ->
            match.groups[name]?.value?.replace(",", "")?.toDoubleOrNull()
        } ?: TransactionExtractor.extractCreditLimit(text)

        // Resolve bank name for account label: sender ID > regex group > SMS text scan
        // detectedBank is already BankSenderDetector-resolved at the call site; pass null for
        // senderAddress here so extractBankName only scans the SMS text and doesn't re-check sender.
        val resolvedBank = detectedBank
            ?: bankName
            ?: TransactionExtractor.extractBankName(text, senderAddress = null)

        // Build composite fullAccount label matching hardcoded pattern convention:
        // "<BANK> <accountLabelType> <last4>" e.g. "HDFC Debit Card 1234" or "SBI Acct 5678"
        val fullAccount: String? = when {
            resolvedBank != null && cardNumber != null -> {
                val label = pattern.accountLabelType ?: "Card"
                TransactionExtractor.cleanAccountName("$resolvedBank $label $cardNumber") ?: "$resolvedBank $label $cardNumber"
            }
            resolvedBank != null && account != null -> {
                val label = pattern.accountLabelType ?: "Acct"
                "$resolvedBank $label $account"
            }
            cardNumber != null -> cardNumber
            account != null -> account
            else -> null
        }

        return ExtractedTransaction(
            type = if (pattern.transactionType == "detect") null else pattern.transactionType,
            amount = amount,
            currency = currency,
            date = date,
            fullAccount = fullAccount,
            merchant = merchant,
            reference = reference,
            balance = balance,
            creditLimit = creditLimit
        )
    }
}
