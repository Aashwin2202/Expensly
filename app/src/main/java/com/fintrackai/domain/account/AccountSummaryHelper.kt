package com.fintrackai.domain.account

import com.fintrackai.data.local.db.AccountMappingEntity
import com.fintrackai.data.local.db.AccountTotalsRow
import com.fintrackai.domain.model.AccountSummary
import com.fintrackai.domain.model.MappedAccount
import com.fintrackai.domain.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccountSummaryHelper {

    fun extractLast4Digits(account: String): String? {
        val digits = account.filter { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    fun normalizeDisplayTitle(rawAccount: String): String {
        val t = rawAccount.trim()
        if (t.isEmpty()) return t
        if (Regex("""^[*Xx0-9]+$""").matches(t)) {
            val digits = t.replace(Regex("""[*Xx]"""), "")
            return "A/C $digits"
        }
        val last4 = extractLast4Digits(t)
        if (last4 != null) {
            val noiseWords = setOf("bank", "debit", "credit", "card", "a/c", "ac", "acct", "account")
            val lower = t.lowercase()
            val isCard = lower.contains("card")
            val cleaned = t.replace("*", "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            val idx = cleaned.lastIndexOf(last4)
            val prefix = if (idx >= 0) cleaned.substring(0, idx).trim() else cleaned
            val bankPart = prefix.split(Regex("""\s+"""))
                .filter { it.isNotEmpty() && it.lowercase() !in noiseWords }
                .joinToString(" ")
                .trim()
            val label = if (isCard) "Card" else "A/C"
            return if (bankPart.isNotEmpty()) "$bankPart $label ($last4)" else "$label ($last4)"
        }
        return t.replace("*", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace(Regex("""\bA\s*/\s*C\b""", RegexOption.IGNORE_CASE), "A/C")
    }

    fun accountKeyFrom(rawAccount: String): String {
        val display = normalizeDisplayTitle(rawAccount)
        val last4 = extractLast4Digits(rawAccount)
        return last4 ?: display.lowercase().replace(Regex("""[^a-z0-9]"""), "")
    }

    fun normalizeAccountKeyForMatch(rawAccount: String): String {
        val last4 = extractLast4Digits(rawAccount)
        if (last4 != null) return last4
        return normalizeDisplayTitle(rawAccount)
            .lowercase()
            .replace(Regex("""[^a-z0-9]"""), "")
    }

    /**
     * Split summed accounts into bank accounts, credit cards, and debit cards.
     *
     * Classification order:
     *  1. Explicit mapping with accountType == "credit_card" → credit cards
     *  2. Explicit mapping with accountType == "debit_card"  → debit cards
     *  3. No mapping but title contains "credit card"        → credit cards
     *  4. No mapping but title contains "debit card" or
     *     "debit" or withdrawal context                      → debit cards
     *  5. No mapping but title contains "card" (generic)     → credit cards (legacy)
     *  6. Everything else                                     → bank accounts
     */
    fun splitAccountsAndCards(
        transactions: List<Transaction>,
        mappings: List<MappedAccount>
    ): Triple<List<AccountSummary>, List<AccountSummary>, List<AccountSummary>> {
        val mapByLast4 = mappings.associateBy { it.last4Digits }
        val cal = java.util.Calendar.getInstance()
        val currentYearMonth = "%04d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        // Pass 1: register every account that has ever had a countInStats transaction (all-time).
        // Pass 2: only add to totalAmount if the transaction is in the current month.
        val summaries = mutableMapOf<String, AccountSummaryBuilder>()
        for (tx in transactions) {
            if (tx.amount <= 0 || !tx.countInStats) continue
            if (!tx.type.equals("debit", ignoreCase = true)) continue
            val raw = tx.accounts.trim()
            if (raw.isEmpty()) continue
            val title = normalizeDisplayTitle(raw)
            val key = accountKeyFrom(raw)
            val builder = summaries.getOrPut(key) { AccountSummaryBuilder(key, title) }
            builder.rawStrings.add(raw)
            builder.allTimeTotal += kotlin.math.abs(tx.amount)
            if (tx.date.startsWith(currentYearMonth)) {
                builder.totalAmount += kotlin.math.abs(tx.amount)
            }
        }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val accounts = mutableListOf<AccountSummary>()
        val creditCards = mutableListOf<AccountSummary>()
        val debitCards = mutableListOf<AccountSummary>()
        for (b in summaries.values) {
            val last4 = b.accountKey.takeIf { it.length == 4 && it.all(Char::isDigit) }
                ?: extractLast4Digits(b.title)
            val mapping = last4?.let { mapByLast4[it] }
            if (mapping?.isHidden == true) continue
            val balanceDate = mapping?.balanceUpdatedAt?.let { runCatching { dateFmt.format(Date(it)) }.getOrNull() }
            val summary = b.toSummary().copy(
                availableBalance = mapping?.availableBalance,
                balanceUpdatedDate = balanceDate,
                isConfident = mapping?.isConfident ?: true
            )
            val combinedRaw = b.rawStrings.joinToString(" ").lowercase()
            when {
                mapping?.isCreditCard == true ->
                    creditCards.add(summary)
                mapping?.isDebitCard == true ->
                    debitCards.add(summary)
                mapping == null && combinedRaw.contains("credit") ->
                    creditCards.add(summary)
                mapping == null && combinedRaw.contains("debit") ->
                    debitCards.add(summary)
                mapping == null && combinedRaw.contains("card") ->
                    creditCards.add(summary)
                else ->
                    accounts.add(summary)
            }
        }
        accounts.sortByDescending { it.totalAmount }
        creditCards.sortByDescending { it.totalAmount }
        debitCards.sortByDescending { it.totalAmount }
        return Triple(accounts, creditCards, debitCards)
    }

    fun splitFromTotals(
        totals: List<AccountTotalsRow>,
        mappingEntities: List<AccountMappingEntity>
    ): Triple<List<AccountSummary>, List<AccountSummary>, List<AccountSummary>> {
        val mapByLast4 = mappingEntities.associateBy { it.last4Digits }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Merge rows that share the same last4 (e.g. "ICICI Bank Card 0000" + "ICICI Card 0000").
        // Also accumulate raw strings from all merged rows for classification heuristics.
        data class MergedRow(
            val key: String,
            val title: String,
            val last4: String?,
            var monthTotal: Double,
            var allTimeTotal: Double,
            val rawStrings: MutableList<String> = mutableListOf()
        )
        val merged = linkedMapOf<String, MergedRow>()
        for (row in totals) {
            val raw = row.accounts.trim()
            if (raw.isEmpty()) continue
            val last4 = extractLast4Digits(raw)
            val mergeKey = last4 ?: accountKeyFrom(raw)
            val existing = merged[mergeKey]
            if (existing != null) {
                existing.monthTotal += row.monthTotal
                existing.allTimeTotal += row.allTimeTotal
                existing.rawStrings.add(raw)
            } else {
                merged[mergeKey] = MergedRow(
                    key = last4 ?: accountKeyFrom(raw),
                    title = normalizeDisplayTitle(raw),
                    last4 = last4,
                    monthTotal = row.monthTotal,
                    allTimeTotal = row.allTimeTotal,
                    rawStrings = mutableListOf(raw)
                )
            }
        }

        val accounts = mutableListOf<AccountSummary>()
        val creditCards = mutableListOf<AccountSummary>()
        val debitCards = mutableListOf<AccountSummary>()
        for (m in merged.values) {
            val mapping = m.last4?.let { mapByLast4[it] }
            if (mapping?.accountType == "hidden") continue
            val balanceDate = mapping?.balanceUpdatedAt?.let { runCatching { dateFmt.format(Date(it)) }.getOrNull() }
            val summary = AccountSummary(
                accountKey = m.key,
                title = m.title,
                totalAmount = m.monthTotal,
                allTimeTotal = m.allTimeTotal,
                availableBalance = mapping?.availableBalance,
                balanceUpdatedDate = balanceDate,
                isConfident = mapping?.isConfident ?: true
            )
            // Use combined raw strings for classification — normalized titles no longer contain "credit"/"debit"
            val combinedRaw = m.rawStrings.joinToString(" ").lowercase()
            when {
                mapping?.accountType == "credit_card" -> creditCards.add(summary)
                mapping?.accountType == "debit_card" -> debitCards.add(summary)
                mapping == null && combinedRaw.contains("credit") -> creditCards.add(summary)
                mapping == null && combinedRaw.contains("debit") -> debitCards.add(summary)
                mapping == null && combinedRaw.contains("card") -> creditCards.add(summary)
                else -> accounts.add(summary)
            }
        }
        accounts.sortByDescending { it.totalAmount }
        creditCards.sortByDescending { it.totalAmount }
        debitCards.sortByDescending { it.totalAmount }
        return Triple(accounts, creditCards, debitCards)
    }

    private class AccountSummaryBuilder(
        val accountKey: String,
        val title: String
    ) {
        var totalAmount: Double = 0.0
        var allTimeTotal: Double = 0.0
        val rawStrings: MutableList<String> = mutableListOf()
        fun toSummary() = AccountSummary(accountKey, title, totalAmount, allTimeTotal)
    }
}
