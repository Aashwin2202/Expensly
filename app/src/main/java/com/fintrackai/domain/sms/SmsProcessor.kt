package com.fintrackai.domain.sms

import android.util.Log
import com.fintrackai.data.local.db.AccountMappingEntity
import com.fintrackai.data.local.db.SmsPatternEntity
import com.fintrackai.domain.model.AccountMapping
import com.fintrackai.domain.model.SmsMessage
import com.fintrackai.domain.model.Transaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

private const val CONCURRENCY = 16
private const val TAG = "SmsProcessor"

data class MappingHooks(
    val getMerchantCategory: suspend (String) -> String?,
    val getAccountMapping: suspend (String, String) -> AccountMapping?,
    val saveAccountMapping: suspend (String, String, String, Boolean) -> Unit,
    /** Returns the user-corrected merchant name for [originalName], or null if no correction exists. */
    val getMerchantName: suspend (String) -> String? = { null },
    /**
     * Called when an SMS from a known bank sender could not be parsed into a
     * transaction (amount was null/zero after all extraction patterns).
     * Intended for privacy-safe pattern telemetry — implementations must not
     * transmit the raw [body].
     */
    val onUnknownPattern: ((body: String, sender: String) -> Unit)? = null,
    /** Remote merchant→category map synced from Supabase, cached in Room. */
    val remoteMerchantCategories: Map<String, String> = emptyMap(),
    /** Remote word→category map synced from Supabase, cached in Room. */
    val remoteWordCategories: Map<String, String> = emptyMap(),
    /** Returns the user-saved countInStats preference for a merchant, or null if not set. */
    val getMerchantCountInStats: suspend (String) -> Boolean? = { null }
)

/**
 * Pre-loaded caches of all DB lookup tables. Passed into [SmsProcessor.processSmsMessages]
 * to eliminate per-message DB round-trips during batch processing.
 *
 * [pendingAccountMappings] collects new (digits, bank) → entity entries discovered during
 * processing; the caller flushes them to the DB in one bulk write after processing completes.
 */
data class ProcessingCaches(
    /** (last4Digits, bankName) → AccountMapping for all known accounts. */
    val accountMappings: ConcurrentHashMap<Pair<String, String>, AccountMapping>,
    /** normalizedMerchantKey → category for local user mappings. */
    val merchantCategories: Map<String, String>,
    /** normalizedMerchantKey → countInStats for local user preferences. */
    val merchantCountInStats: Map<String, Boolean>,
    /** normalizedKey → correctedName for user-saved merchant name corrections. */
    val merchantNameCorrections: Map<String, String>,
    /** Collects newly discovered account mappings for a single bulk DB write after processing. */
    val pendingAccountMappings: ConcurrentHashMap<Pair<String, String>, AccountMappingEntity> = ConcurrentHashMap()
)

object SmsProcessor {

    suspend fun processSmsMessages(
        messages: List<SmsMessage>,
        hooks: MappingHooks,
        onProgress: ((scanned: Int, total: Int) -> Unit)? = null,
        dynamicPatterns: List<SmsPatternEntity> = emptyList(),
        caches: ProcessingCaches? = null
    ): List<Transaction> {
        val startMs = System.currentTimeMillis()
        val filtered = SmsFilter.filterTransactionSMS(messages)
        Log.d(TAG, "Filtered ${filtered.size} transaction SMS from ${messages.size} messages")
        val total = filtered.size

        val transactions = mutableListOf<Transaction>()
        for (i in filtered.indices step CONCURRENCY) {
            coroutineScope { ensureActive() }
            val chunk = filtered.subList(i, minOf(i + CONCURRENCY, total))
            val results = coroutineScope {
                chunk.mapIndexed { j, msg ->
                    async { processOneMessage(msg, hooks, i + j, dynamicPatterns, caches) }
                }.awaitAll()
            }
            results.filterNotNull().forEach { transactions.add(it) }
            val scanned = minOf(i + CONCURRENCY, total)
            onProgress?.invoke(scanned, total)
        }

        val processMs = System.currentTimeMillis() - startMs
        Log.d(TAG, "Processed ${filtered.size} SMS in ${processMs}ms -> ${transactions.size} transactions")
        return transactions
    }

    private suspend fun processOneMessage(
        msg: SmsMessage,
        hooks: MappingHooks,
        index: Int,
        dynamicPatterns: List<SmsPatternEntity> = emptyList(),
        caches: ProcessingCaches? = null
    ): Transaction? {
        return try {
            val extracted = TransactionExtractor.extractTransactionData(msg.body, dynamicPatterns, msg.address)
            if (extracted.amount == null || extracted.amount <= 0) {
                return null
            }

            // Pattern 7 (generic fallback) succeeded — report skeleton for telemetry
            val isWeakMatch = extracted.patternIndex == 7
            if (isWeakMatch) {
                hooks.onUnknownPattern?.invoke(msg.body, msg.address)
            }

            // Apply user-saved merchant name correction if one exists
            val resolvedMerchant = if (caches != null) {
                extracted.merchant?.let { caches.merchantNameCorrections[it.trim().lowercase()] }
            } else {
                extracted.merchant?.let { hooks.getMerchantName(it) }
            }
            val extractedResolved = if (resolvedMerchant != null) extracted.copy(merchant = resolvedMerchant) else extracted

            val getMerchantCategory: suspend (String) -> String? = if (caches != null) {
                { merchant -> caches.merchantCategories[merchant] ?: hooks.getMerchantCategory(merchant) }
            } else {
                { merchant -> hooks.getMerchantCategory(merchant) }
            }

            val categorized = TransactionCategorizer.categorizeTransaction(
                extractedResolved,
                getMerchantCategory = getMerchantCategory,
                remoteMerchantCategories = hooks.remoteMerchantCategories,
                remoteWordCategories = hooks.remoteWordCategories
            )
            if (categorized.amount == null || categorized.amount <= 0) return null

            val tsMs = if (msg.timestamp < 1_000_000_000_000L) msg.timestamp * 1000 else msg.timestamp
            val messageDate = java.util.Date(tsMs)
            val cal = java.util.Calendar.getInstance().apply { time = messageDate }
            val hasLocalDate = msg.date?.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) == true
            val hasLocalTime = msg.time?.matches(Regex("^\\d{2}:\\d{2}$")) == true
            val dateStr = if (hasLocalDate) msg.date!! else {
                String.format("%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            }
            val timeStr = if (hasLocalTime) msg.time!! else {
                String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            }

            val getAccountMapping: suspend (String, String) -> AccountMapping? = if (caches != null) {
                { digits, bank -> caches.accountMappings[digits to bank] }
            } else {
                hooks.getAccountMapping
            }

            val saveAccountMapping: suspend (String, String, String, Boolean) -> Unit = if (caches != null) {
                { digits, bank, type, confident ->
                    val entity = AccountMappingEntity(digits, bank, type, System.currentTimeMillis(), isConfident = confident)
                    caches.pendingAccountMappings[digits to bank] = entity
                    // Also update the live cache so subsequent messages in this batch see the new mapping
                    caches.accountMappings[digits to bank] = AccountMapping(bank, type, confident)
                }
            } else {
                hooks.saveAccountMapping
            }

            val getMerchantCountInStats: suspend (String) -> Boolean? = if (caches != null) {
                { merchant -> caches.merchantCountInStats[merchant] }
            } else {
                hooks.getMerchantCountInStats
            }

            TransactionCategorizer.convertToAppTransaction(
                extracted = categorized,
                time = timeStr,
                smsBody = msg.body,
                senderAddress = msg.address,
                originalSms = msg.body,
                getAccountMapping = getAccountMapping,
                saveAccountMapping = saveAccountMapping,
                messageDateStr = dateStr,
                getMerchantCountInStats = getMerchantCountInStats
            )?.copy(isWeakMatch = isWeakMatch)
        } catch (e: Exception) {
            Log.w(TAG, "SMS $index parse error", e)
            null
        }
    }
}
