package com.fintrackai.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.fintrackai.data.local.db.*
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.merchant.MerchantMappingKeys
import com.fintrackai.domain.category.BUILT_IN_CATEGORIES
import com.fintrackai.domain.category.BUILT_IN_CATEGORY_IDS
import com.fintrackai.domain.category.CUSTOM_CATEGORY_COLOR_PALETTE
import com.fintrackai.domain.category.DEFAULT_CUSTOM_CATEGORY_COLOR_HEX
import com.fintrackai.domain.model.*
import com.fintrackai.domain.sms.MappingHooks
import com.fintrackai.domain.sms.ProcessingCaches
import java.util.concurrent.ConcurrentHashMap
import com.fintrackai.domain.recurring.RecurringReminderConstants
import com.fintrackai.domain.recurring.RecurringReminderHelper
import com.fintrackai.domain.sms.SmsConstants
import com.fintrackai.domain.sms.SmsDedupeHashHelper
import com.fintrackai.domain.sms.SmsFuzzyDedupeHelper
import com.fintrackai.domain.sms.BalanceSmsParser
import com.fintrackai.domain.sms.TransactionExtractor
import com.fintrackai.domain.sms.PatternReporter
import com.fintrackai.domain.sms.SmsProcessor
import com.fintrackai.domain.sms.TransactionCategorizer
import com.fintrackai.domain.transactions.TransactionCsvHelper
import com.fintrackai.domain.transactions.TransactionImportResult
import com.fintrackai.domain.transactions.DebitCreditLinkOutcome
import com.fintrackai.domain.transactions.TransactionLinkConstants
import com.fintrackai.domain.transactions.TransactionLinkHelper
import com.fintrackai.domain.transactions.TransactionLinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.fintrackai.notification.BudgetMonitor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val customCategoryDao: CustomCategoryDao,
    private val merchantMappingDao: MerchantMappingDao,
    private val merchantNameMappingDao: MerchantNameMappingDao,
    private val accountMappingDao: AccountMappingDao,
    private val budgetDao: BudgetDao,
    private val recurringDao: RecurringDao,
    private val reminderDao: ReminderDao,
    private val budgetMonitor: BudgetMonitor,
    private val patternReporter: PatternReporter,
    private val smsPatternDao: SmsPatternDao,
    private val merchantCategorySyncRepo: MerchantCategorySyncRepository,
    private val patternSyncRepo: PatternSyncRepository,
    private val categoryChangeLogger: CategoryChangeLogger
) {
    private val smsImportMutex = Mutex()

    private suspend fun getDynamicPatterns(): List<SmsPatternEntity> =
        smsPatternDao.getAllOrdered()

    fun getAllTransactionsFlow(): Flow<List<Transaction>> =
        transactionDao.getAll().map { entities -> entities.map { it.toDomain() } }

    fun getAccountTotalsFlow(monthKey: String) = transactionDao.getAccountTotalsFlow(monthKey)

    fun getAccountMappingsFlow() = accountMappingDao.getAllFlow()

    suspend fun saveTransaction(tx: Transaction): Boolean {
        if (tx.reference != null) {
            val existing = transactionDao.findByReference(tx.reference)
            if (existing != null) {
                transactionDao.mergeMissingFieldsByReference(tx.reference, tx.merchant, tx.accounts, tx.category)
                return false
            }
        }
        if (smsDuplicateExists(tx)) return false
        val result = transactionDao.insert(tx.toEntity())
        if (result != -1L) {
            if (tx.type == "debit") budgetMonitor.checkBudgetAfterTransaction(tx.category)
            return true
        }
        return false
    }

    suspend fun saveTransactionsBulk(
        transactions: List<Transaction>,
        existingReferences: Set<String>? = null,
        existingHashes: Set<String>? = null
    ): Int {
        // Maps reference → pending entity index, so a second SMS for the same reference can merge fields.
        val pendingReferenceIndex = mutableMapOf<String, Int>()
        val pendingSmsHashes = mutableSetOf<String>()
        val pendingSmsContentKeys = mutableSetOf<Pair<String, String>>()
        val pendingFuzzyNorms = mutableMapOf<String, MutableList<String>>()
        val entities = mutableListOf<TransactionEntity>()
        for (tx in transactions) {
            if (tx.reference != null) {
                val batchIdx = pendingReferenceIndex[tx.reference]
                if (batchIdx != null) {
                    // Merge into the already-pending entity
                    val prev = entities[batchIdx]
                    entities[batchIdx] = prev.copy(
                        merchant = if (prev.merchant == "Unknown" && tx.merchant != "Unknown") tx.merchant else prev.merchant,
                        accounts = if (prev.accounts == "Unknown" && tx.accounts != "Unknown") tx.accounts else prev.accounts,
                        category = if (prev.category == "Unknown" && tx.category != "Unknown") tx.category else prev.category
                    )
                    continue
                }
                if (existingReferences != null) {
                    if (tx.reference in existingReferences) continue
                } else {
                    val existing = transactionDao.findByReference(tx.reference)
                    if (existing != null) {
                        transactionDao.mergeMissingFieldsByReference(tx.reference, tx.merchant, tx.accounts, tx.category)
                        continue
                    }
                }
                pendingReferenceIndex[tx.reference] = entities.size
            }
            if (!tx.originalSms.isNullOrBlank()) {
                val normForFuzzy = SmsDedupeHashHelper.normalizeBody(tx.originalSms)
                val fuzzyKey = SmsFuzzyDedupeHelper.batchKey(tx.smsSender, tx.date, tx.amount, tx.type)
                val pendingNormList = pendingFuzzyNorms[fuzzyKey]
                if (pendingNormList != null &&
                    pendingNormList.any { SmsFuzzyDedupeHelper.isLikelySameSmsBody(normForFuzzy, it) }
                ) {
                    continue
                }
                val h = tx.smsDedupeHash ?: SmsDedupeHashHelper.contentHash(tx.smsSender, tx.originalSms)
                val ck = (tx.smsSender ?: "") to SmsDedupeHashHelper.normalizeBody(tx.originalSms)
                if (h in pendingSmsHashes || ck in pendingSmsContentKeys) continue
                if (existingHashes != null) {
                    if (h in existingHashes) continue
                } else {
                    if (smsDuplicateExists(tx)) continue
                }
                pendingSmsHashes.add(h)
                pendingSmsContentKeys.add(ck)
                pendingFuzzyNorms.getOrPut(fuzzyKey) { mutableListOf() }.add(normForFuzzy)
            }
            entities.add(tx.toEntity())
        }
        val results = transactionDao.insertAll(entities)
        val savedCount = results.count { it != -1L }
        // Check budget alerts for categories of successfully saved debit transactions
        if (savedCount > 0) {
            val savedCategories = entities.zip(results)
                .filter { (entity, rowId) -> rowId != -1L && entity.type == "debit" }
                .map { (entity, _) -> entity.category }
                .distinct()
            for (category in savedCategories) {
                budgetMonitor.checkBudgetAfterTransaction(category)
            }
        }
        return savedCount
    }

    /**
     * Fills [TransactionEntity.smsDedupeHash] for rows imported before hashing existed.
     * Skips rows that would violate the unique index (duplicate legacy imports).
     */
    suspend fun backfillSmsDedupeHashes() = withContext(Dispatchers.IO) {
        val rows = transactionDao.listTransactionsNeedingSmsDedupeHash()
        for (row in rows) {
            val body = row.originalSms ?: continue
            val h = SmsDedupeHashHelper.contentHash(row.smsSender, body)
            if (transactionDao.findBySmsDedupeHash(h) != null) continue
            transactionDao.updateSmsDedupeHash(row.id, h)
        }
    }

    private suspend fun smsDuplicateExists(tx: Transaction): Boolean {
        if (tx.originalSms.isNullOrBlank()) return false
        val hash = tx.smsDedupeHash
            ?: SmsDedupeHashHelper.contentHash(tx.smsSender, tx.originalSms)
        if (transactionDao.findBySmsDedupeHash(hash) != null) return true
        if (smsContentDuplicateExists(tx.smsSender, tx.originalSms)) return true
        return smsFuzzyDuplicateExists(tx)
    }

    private suspend fun smsContentDuplicateExists(sender: String?, originalSms: String): Boolean {
        val norm = SmsDedupeHashHelper.normalizeBody(originalSms)
        val senderKey = sender ?: ""
        val candidates = transactionDao.listBySenderForSmsDedupe(senderKey)
        return candidates.any { existing ->
            existing.originalSms != null &&
                SmsDedupeHashHelper.normalizeBody(existing.originalSms) == norm
        }
    }

    private suspend fun smsFuzzyDuplicateExists(tx: Transaction): Boolean {
        val body = tx.originalSms ?: return false
        val normNew = SmsDedupeHashHelper.normalizeBody(body)
        val senderKey = tx.smsSender ?: ""
        val candidates = transactionDao.listForSmsFuzzyDedupe(
            sender = senderKey,
            date = tx.date,
            amount = tx.amount,
            type = tx.type
        )
        return candidates.any { row ->
            val existingBody = row.originalSms ?: return@any false
            val normExisting = SmsDedupeHashHelper.normalizeBody(existingBody)
            SmsFuzzyDedupeHelper.isLikelySameSmsBody(normNew, normExisting)
        }
    }

    /**
     * Same parsing and dedupe as manual SMS import ([saveTransactionsBulk] by [Transaction.reference]).
     * Used when new bank SMS arrive while the app is in the background.
     */
    suspend fun processIncomingSmsMessages(messages: List<SmsMessage>): Int = smsImportMutex.withLock {
        if (messages.isEmpty()) return@withLock 0
        // Process balance SMS — update existing mapped accounts by last4 only (bank/type unknown)
        for (msg in messages) {
            val result = BalanceSmsParser.parse(msg.body, msg.timestamp) ?: continue
            updateAccountBalance(result.last4Digits, result.availableBalance, result.balanceTimestamp)
        }
        val remoteMerchantCategories = merchantCategorySyncRepo.getMerchantCategories()
        val remoteWordCategories = merchantCategorySyncRepo.getWordCategories()
        val hooks = MappingHooks(
            getMerchantCategory = { getMerchantCategory(it) },
            getAccountMapping = { digits, bank -> getAccountMapping(digits, bank) },
            saveAccountMapping = { digits, bank, type, confident -> saveAccountMapping(digits, bank, type, confident) },
            getMerchantName = { getMerchantNameMapping(it) },
            onUnknownPattern = { body, sender -> patternReporter.report(body, sender) },
            remoteMerchantCategories = remoteMerchantCategories,
            remoteWordCategories = remoteWordCategories,
            getMerchantCountInStats = { getMerchantCountInStats(it) }
        )
        val (localMerchantCategories, localCountInStats) = getAllMerchantMappingsAsMaps()
        val caches = ProcessingCaches(
            accountMappings = ConcurrentHashMap(getAllAccountMappingsAsMap()),
            merchantCategories = localMerchantCategories,
            merchantCountInStats = localCountInStats,
            merchantNameCorrections = getAllMerchantNameMappingsAsMap()
        )
        val dynamicPatterns = getDynamicPatterns()
        val transactions = SmsProcessor.processSmsMessages(messages, hooks, dynamicPatterns = dynamicPatterns, caches = caches)
        if (caches.pendingAccountMappings.isNotEmpty()) {
            saveAccountMappingsBulk(caches.pendingAccountMappings.values.toList())
        }
        saveTransactionsBulk(transactions)
    }

    /**
     * Reads SMS inbox (optionally from [sinceMillisInclusive]) and saves new transactions.
     * Serialized with a mutex so launch rescan and Settings import/rescan do not run concurrently.
     */
    suspend fun scanSmsInboxAndSave(
        contentResolver: ContentResolver,
        sinceMillisInclusive: Long?,
        onProgress: ((scanned: Int, total: Int) -> Unit)? = null
    ): Pair<Int, Int> = smsImportMutex.withLock {
        withContext(Dispatchers.IO) {
            // Sync patterns + merchant categories before patterns are consumed. Launch the (network)
            // syncs concurrently with the local inbox read so their latency overlaps the read instead
            // of adding to it. Both are non-fatal on failure and short-circuit when nothing changed.
            val syncJobs = listOf(
                async { patternSyncRepo.syncPatterns() },
                async { merchantCategorySyncRepo.syncMerchantCategories() }
            )

            val messages = readSmsInbox(contentResolver, sinceMillisInclusive)

            // Ensure syncs landed in Room before the reads below load patterns/categories.
            syncJobs.awaitAll()

            // All setup reads are independent — run them in parallel.
            val remoteMerchantCategories: Map<String, String>
            val remoteWordCategories: Map<String, String>
            val localMerchantCategories: Map<String, String>
            val localCountInStats: Map<String, Boolean>
            val accountMappingsMap: Map<Pair<String, String>, AccountMapping>
            val merchantNameCorrectionsMap: Map<String, String>
            val dynamicPatterns: List<SmsPatternEntity>
            val existingReferences: HashSet<String>
            val existingHashes: HashSet<String>
            coroutineScope {
                val dRemoteCategories = async { merchantCategorySyncRepo.getMerchantCategories() }
                val dRemoteWords = async { merchantCategorySyncRepo.getWordCategories() }
                val dLocalMerchant = async { getAllMerchantMappingsAsMaps() }
                val dAccountMaps = async { getAllAccountMappingsAsMap() }
                val dNameMaps = async { getAllMerchantNameMappingsAsMap() }
                val dPatterns = async { getDynamicPatterns() }
                val dRefs = async { transactionDao.getAllReferences().toHashSet() }
                val dHashes = async { transactionDao.getAllSmsDedupeHashes().toHashSet() }
                remoteMerchantCategories = dRemoteCategories.await()
                remoteWordCategories = dRemoteWords.await()
                val localPair = dLocalMerchant.await()
                localMerchantCategories = localPair.first
                localCountInStats = localPair.second
                accountMappingsMap = dAccountMaps.await()
                merchantNameCorrectionsMap = dNameMaps.await()
                dynamicPatterns = dPatterns.await()
                existingReferences = dRefs.await()
                existingHashes = dHashes.await()
            }

            val hooks = MappingHooks(
                getMerchantCategory = { getMerchantCategory(it) },
                getAccountMapping = { digits, bank -> getAccountMapping(digits, bank) },
                saveAccountMapping = { digits, bank, type, confident -> saveAccountMapping(digits, bank, type, confident) },
                onUnknownPattern = { body, sender -> patternReporter.report(body, sender) },
                remoteMerchantCategories = remoteMerchantCategories,
                remoteWordCategories = remoteWordCategories,
                getMerchantCountInStats = { getMerchantCountInStats(it) }
            )
            val caches = ProcessingCaches(
                accountMappings = ConcurrentHashMap(accountMappingsMap),
                merchantCategories = localMerchantCategories,
                merchantCountInStats = localCountInStats,
                merchantNameCorrections = merchantNameCorrectionsMap
            )

            // Run balance-update collection concurrently with SMS processing — both are pure
            // reads over `messages` and share no mutable state with each other.
            val balanceUpdates: Map<String, BalanceSmsParser.BalanceSmsResult>
            val transactions: List<Transaction>
            coroutineScope {
                val balanceJob = async {
                    // Batch balance updates — keep only the most recent reading per account.
                    // Collected here but applied AFTER saveAccountMappingsBulk so rows exist on first scan.
                    val updates = mutableMapOf<String, BalanceSmsParser.BalanceSmsResult>()
                    for (msg in messages) {
                        // Primary: dedicated balance SMS (e.g. "Avl Bal: INR 1,23,456 in A/c XX1234")
                        val parsed = BalanceSmsParser.parse(msg.body, msg.timestamp)
                        if (parsed != null) {
                            val prev = updates[parsed.last4Digits]
                            if (prev == null || parsed.balanceTimestamp > prev.balanceTimestamp) {
                                updates[parsed.last4Digits] = parsed
                            }
                            continue
                        }
                        // Secondary: inline balance in transaction SMS (e.g. IOB "acbal:1234.56")
                        val inlineBalance = TransactionExtractor.extractBalance(msg.body) ?: continue
                        val last4 = TransactionExtractor.extract4DigitNumbers(msg.body).firstOrNull() ?: continue
                        val prev = updates[last4]
                        if (prev == null || msg.timestamp > prev.balanceTimestamp) {
                            updates[last4] = BalanceSmsParser.BalanceSmsResult(
                                last4Digits = last4,
                                availableBalance = inlineBalance,
                                balanceDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(msg.timestamp)),
                                balanceTimestamp = msg.timestamp
                            )
                        }
                    }
                    updates
                }
                val txJob = async {
                    SmsProcessor.processSmsMessages(messages, hooks, onProgress, dynamicPatterns, caches)
                }
                balanceUpdates = balanceJob.await()
                transactions = txJob.await()
            }

            if (caches.pendingAccountMappings.isNotEmpty()) {
                saveAccountMappingsBulk(caches.pendingAccountMappings.values.toList())
            }
            // Apply balance updates after mapping rows are guaranteed to exist.
            for (result in balanceUpdates.values) {
                updateAccountBalance(result.last4Digits, result.availableBalance, result.balanceTimestamp)
            }
            val saved = saveTransactionsBulk(
                transactions,
                existingReferences = existingReferences,
                existingHashes = existingHashes
            )
            messages.size to saved
        }
    }

    private fun readSmsInbox(contentResolver: ContentResolver, sinceMillisInclusive: Long?): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms/inbox")
        val selection: String?
        val selectionArgs: Array<String>?
        if (sinceMillisInclusive != null) {
            selection = "date >= ?"
            selectionArgs = arrayOf(sinceMillisInclusive.toString())
        } else {
            selection = null
            selectionArgs = null
        }
        val cursor = contentResolver.query(uri, arrayOf("body", "address", "date"), selection, selectionArgs, "date DESC")
        cursor?.use {
            val bodyIdx = it.getColumnIndex("body")
            val addressIdx = it.getColumnIndex("address")
            val dateIdx = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: continue
                val address = it.getString(addressIdx) ?: ""
                val timestamp = it.getLong(dateIdx)
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                val date = String.format(
                    "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                val time = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                messages.add(SmsMessage(body = body, timestamp = timestamp, address = address, date = date, time = time))
            }
        }
        return messages
    }

    suspend fun updateTransaction(tx: Transaction) {
        transactionDao.update(tx.toEntity())
    }

    suspend fun getTransactionById(id: String): Transaction? =
        transactionDao.getById(id)?.toDomain()

    /** Returns all hidden secondary transactions belonging to [groupId]. */
    suspend fun getSecondariesForGroup(groupId: String): List<Transaction> =
        transactionDao.listSecondariesForGroup(groupId).map { it.toDomain() }

    suspend fun getPrimaryForGroup(groupId: String): Transaction? =
        transactionDao.getPrimaryForGroup(groupId)?.toDomain()

    /**
     * Merges [ids] into a single link group. Any existing group memberships are dissolved first.
     * The transaction with the largest amount becomes the primary; the net is computed from
     * total debits minus total credits across all selected transactions.
     */
    suspend fun mergeTransactions(ids: List<String>): TransactionLinkResult {
        if (ids.size < 2) return TransactionLinkResult.InvalidPair

        // Reject if all selected transactions are the same type (need at least one debit + one credit)
        val preCheck = ids.mapNotNull { transactionDao.getById(it) }
        val types = preCheck.map { (it.linkStashedType ?: it.type).lowercase() }.toSet()
        if (types.size <= 1) return TransactionLinkResult.InvalidPair

        // Free every selected transaction from its current group (if any)
        for (id in ids) {
            val tx = transactionDao.getById(id) ?: continue
            val gid = tx.linkGroupId ?: continue
            if (tx.linkSuppressed == 0) {
                // Was a primary — free its secondaries (only those NOT in the merge set)
                val secs = transactionDao.listSecondariesForGroup(gid).filter { it.id !in ids }
                for (sec in secs) {
                    transactionDao.update(sec.copy(linkGroupId = null, linkSuppressed = 0, countInStats = 1))
                }
                transactionDao.update(
                    tx.copy(
                        amount = tx.linkStashedAmount ?: tx.amount,
                        type = tx.linkStashedType ?: tx.type,
                        linkGroupId = null, linkStashedAmount = null, linkStashedType = null,
                        linkSuppressed = 0, countInStats = 1
                    )
                )
            } else {
                // Was a secondary — free it and recompute its old group's primary net
                val primary = transactionDao.getPrimaryForGroup(gid)
                transactionDao.update(tx.copy(linkGroupId = null, linkSuppressed = 0, countInStats = 1))
                if (primary != null) {
                    val remaining = transactionDao.listSecondariesForGroup(gid).filter { it.id !in ids }
                    if (remaining.isEmpty()) {
                        transactionDao.update(
                            primary.copy(
                                amount = primary.linkStashedAmount ?: primary.amount,
                                type = primary.linkStashedType ?: primary.type,
                                linkGroupId = null, linkStashedAmount = null, linkStashedType = null,
                                linkSuppressed = 0, countInStats = 1
                            )
                        )
                    } else {
                        val origAmt = primary.linkStashedAmount ?: primary.amount
                        val origType = primary.linkStashedType ?: primary.type
                        val total = remaining.sumOf { it.amount }
                        val outcome = TransactionLinkHelper.debitCreditLinkOutcome(origType, origAmt, total, false)
                        if (outcome is DebitCreditLinkOutcome.NetMerged) {
                            transactionDao.update(primary.copy(amount = outcome.amount, type = outcome.type))
                        }
                    }
                }
            }
        }

        // Re-fetch fresh state after dissolution
        val txs = ids.mapNotNull { transactionDao.getById(it) }
        if (txs.size < 2) return TransactionLinkResult.InvalidPair

        val sumDebits = txs.filter { it.type.equals("debit", ignoreCase = true) }.sumOf { it.amount }
        val sumCredits = txs.filter { it.type.equals("credit", ignoreCase = true) }.sumOf { it.amount }
        val net = sumDebits - sumCredits
        val fuzzyTolerance = TransactionLinkConstants.AMOUNT_FUZZY_TOLERANCE_RUPEES

        val groupId = java.util.UUID.randomUUID().toString()
        val primary = txs.maxByOrNull { it.amount }!!
        val secondaries = txs.filter { it.id != primary.id }
        val debitCategory = txs.firstOrNull { it.type.equals("debit", ignoreCase = true) }?.category ?: primary.category

        if (kotlin.math.abs(net) <= fuzzyTolerance) {
            // Fuzzy cancel — all blurred
            transactionDao.update(primary.copy(linkGroupId = groupId, linkSuppressed = 0, countInStats = 0))
            for (sec in secondaries) {
                transactionDao.update(sec.copy(linkGroupId = groupId, linkSuppressed = 1, countInStats = 0))
            }
        } else {
            val netType = if (net > 0) "debit" else "credit"
            val netAmount = kotlin.math.abs(net)
            transactionDao.update(
                primary.copy(
                    amount = netAmount, type = netType, category = debitCategory,
                    linkGroupId = groupId, linkStashedAmount = primary.amount, linkStashedType = primary.type,
                    linkSuppressed = 0, countInStats = 1
                )
            )
            for (sec in secondaries) {
                transactionDao.update(sec.copy(linkGroupId = groupId, linkSuppressed = 1, countInStats = 0))
            }
        }
        return TransactionLinkResult.Success
    }

    suspend fun getLinkCandidates(anchor: Transaction): List<Transaction> {
        if (!TransactionLinkHelper.canOfferLink(anchor)) return emptyList()
        val want = TransactionLinkHelper.oppositeType(TransactionLinkHelper.anchorOriginalType(anchor))
        return transactionDao.listLinkCandidates(anchor.id, want).map { it.toDomain() }
    }

    /**
     * Links [peerId] into the same group as [primaryId].
     *
     * Fresh group (first link):
     *  - Whichever of the two has the larger original amount becomes the group primary.
     *    It receives the net (difference) amount and keeps countInStats = 1.
     *  - The smaller-amount transaction becomes the secondary: amount unchanged,
     *    countInStats = 0 (blurred in the list), link_suppressed = 1.
     *  - If amounts are within fuzzy tolerance both get countInStats = 0.
     *
     * Existing group (adding another secondary):
     *  - The already-established primary recomputes its net amount.
     *  - The new transaction joins as secondary (countInStats = 0).
     *
     * If [peerId] is itself a primary, its group is dissolved first so it re-enters
     * at its original amount.
     *
     * Result category is always the debit transaction's category.
     */
    suspend fun linkDebitCreditPair(primaryId: String, peerId: String): TransactionLinkResult {
        val anchor = transactionDao.getById(primaryId) ?: return TransactionLinkResult.NotFound
        var peer = transactionDao.getById(peerId) ?: return TransactionLinkResult.NotFound

        if (anchor.id == peer.id) return TransactionLinkResult.InvalidPair
        if (anchor.linkSuppressed == 1) return TransactionLinkResult.InvalidPair

        val anchorOrigType = anchor.linkStashedType ?: anchor.type
        val peerOrigType = peer.linkStashedType ?: peer.type
        if (anchorOrigType.equals(peerOrigType, ignoreCase = true)) return TransactionLinkResult.InvalidPair

        // If peer is already a primary of a group and anchor is unlinked, join anchor into
        // peer's existing group rather than dissolving it (preserves peer's existing secondaries).
        if (peer.linkGroupId != null && peer.linkSuppressed == 0 && anchor.linkGroupId == null) {
            val groupId = peer.linkGroupId!!
            val peerOrigType = peer.linkStashedType ?: peer.type
            val existingSecondaryTotal = transactionDao.listSecondariesForGroup(groupId).sumOf { it.amount }
            val totalSecondaryAmount = existingSecondaryTotal + anchor.amount
            val primaryOrigAmount = peer.linkStashedAmount ?: peer.amount
            val outcome = TransactionLinkHelper.debitCreditLinkOutcome(
                peerOrigType, primaryOrigAmount, totalSecondaryAmount, isFreshGroup = false
            )
            val debitCategory = if (peerOrigType.equals("debit", ignoreCase = true)) peer.category else anchor.category
            if (outcome is DebitCreditLinkOutcome.NetMerged) {
                transactionDao.update(peer.copy(amount = outcome.amount, type = outcome.type, category = debitCategory))
                transactionDao.update(anchor.copy(linkGroupId = groupId, linkSuppressed = 1, countInStats = 0))
            }
            return TransactionLinkResult.Success
        }

        // If peer is a primary of its own group and anchor is also a primary, dissolve peer's group
        // and add the now-unlinked peer into anchor's existing group.
        if (peer.linkGroupId != null && peer.linkSuppressed == 0) {
            val oldSecondaries = transactionDao.listSecondariesForGroup(peer.linkGroupId!!)
            transactionDao.update(
                peer.copy(
                    amount = peer.linkStashedAmount ?: peer.amount,
                    type = peer.linkStashedType ?: peer.type,
                    linkGroupId = null,
                    linkStashedAmount = null,
                    linkStashedType = null,
                    countInStats = 1
                )
            )
            for (sec in oldSecondaries) {
                transactionDao.update(sec.copy(linkGroupId = null, linkSuppressed = 0, countInStats = 1))
            }
            peer = transactionDao.getById(peerId) ?: return TransactionLinkResult.NotFound
        }

        val isFreshGroup = anchor.linkGroupId == null

        if (isFreshGroup) {
            // --- Fresh group: primary = bigger amount ---
            val groupId = java.util.UUID.randomUUID().toString()
            val outcome = TransactionLinkHelper.debitCreditLinkOutcome(
                anchorOrigType, anchor.amount, peer.amount, isFreshGroup = true
            )

            val debitCategory = if (anchorOrigType.equals("debit", ignoreCase = true)) anchor.category else peer.category

            when (outcome) {
                is DebitCreditLinkOutcome.NetMerged -> {
                    // Bigger-amount tx = primary (net amount, countInStats=1)
                    // Smaller-amount tx = secondary (original amount, countInStats=0)
                    val (bigger, smaller) = if (anchor.amount >= peer.amount) anchor to peer else peer to anchor
                    transactionDao.update(
                        bigger.copy(
                            amount = outcome.amount,
                            type = outcome.type,
                            category = debitCategory,
                            linkGroupId = groupId,
                            linkStashedAmount = bigger.amount,
                            linkStashedType = bigger.type,
                            linkSuppressed = 0,
                            countInStats = 1
                        )
                    )
                    transactionDao.update(
                        smaller.copy(
                            linkGroupId = groupId,
                            linkSuppressed = 1,
                            countInStats = 0
                        )
                    )
                }
                DebitCreditLinkOutcome.FuzzyCancelled -> {
                    // Same amounts — both blurred, anchor is primary by convention
                    transactionDao.update(
                        anchor.copy(
                            category = debitCategory,
                            linkGroupId = groupId,
                            linkSuppressed = 0,
                            countInStats = 0
                        )
                    )
                    transactionDao.update(
                        peer.copy(linkGroupId = groupId, linkSuppressed = 1, countInStats = 0)
                    )
                }
            }
        } else {
            // --- Existing group: anchor is already the primary ---
            val groupId = anchor.linkGroupId!!
            val existingSecondaryTotal = transactionDao.listSecondariesForGroup(groupId).sumOf { it.amount }
            val totalSecondaryAmount = existingSecondaryTotal + peer.amount
            val primaryOrigAmount = anchor.linkStashedAmount ?: anchor.amount
            val outcome = TransactionLinkHelper.debitCreditLinkOutcome(
                anchorOrigType, primaryOrigAmount, totalSecondaryAmount, isFreshGroup = false
            )
            val debitCategory = if (anchorOrigType.equals("debit", ignoreCase = true)) anchor.category else peer.category

            if (outcome is DebitCreditLinkOutcome.NetMerged) {
                transactionDao.update(anchor.copy(amount = outcome.amount, type = outcome.type, category = debitCategory))
                transactionDao.update(peer.copy(linkGroupId = groupId, linkSuppressed = 1, countInStats = 0))
            }
        }

        return TransactionLinkResult.Success
    }

    /**
     * Unlinks the transaction identified by [anyLinkedRowId] from its group.
     * - If it is a secondary and other secondaries remain: frees just this one and
     *   recomputes the primary's net amount with the remaining secondaries.
     * - If it is a secondary and it is the only one: frees it and restores the primary to original.
     * - If it is the primary: frees the entire group (all secondaries restored).
     */
    suspend fun unlinkTransactionPair(anyLinkedRowId: String) {
        val row = transactionDao.getById(anyLinkedRowId) ?: return
        val groupId = row.linkGroupId ?: return

        if (row.linkSuppressed == 1) {
            // Unlinking a secondary — keep the rest of the group intact
            val primary = transactionDao.getPrimaryForGroup(groupId) ?: return
            val remainingSecondaries = transactionDao.listSecondariesForGroup(groupId)
                .filter { it.id != anyLinkedRowId }

            // Free this secondary
            transactionDao.update(row.copy(linkGroupId = null, linkSuppressed = 0, countInStats = 1))

            if (remainingSecondaries.isEmpty()) {
                // Last secondary — restore primary to its original amount
                transactionDao.update(
                    primary.copy(
                        amount = primary.linkStashedAmount ?: primary.amount,
                        type = primary.linkStashedType ?: primary.type,
                        linkGroupId = null,
                        linkStashedAmount = null,
                        linkStashedType = null,
                        linkSuppressed = 0,
                        countInStats = 1
                    )
                )
            } else {
                // Recompute primary net with remaining secondaries
                val primaryOrigAmount = primary.linkStashedAmount ?: primary.amount
                val primaryOrigType = primary.linkStashedType ?: primary.type
                val totalRemaining = remainingSecondaries.sumOf { it.amount }
                val outcome = TransactionLinkHelper.debitCreditLinkOutcome(
                    primaryOrigType, primaryOrigAmount, totalRemaining, isFreshGroup = false
                )
                if (outcome is DebitCreditLinkOutcome.NetMerged) {
                    transactionDao.update(primary.copy(amount = outcome.amount, type = outcome.type))
                }
            }
        } else {
            // Unlinking the primary — free the entire group
            val secondaries = transactionDao.listSecondariesForGroup(groupId)
            transactionDao.update(
                row.copy(
                    amount = row.linkStashedAmount ?: row.amount,
                    type = row.linkStashedType ?: row.type,
                    linkGroupId = null,
                    linkStashedAmount = null,
                    linkStashedType = null,
                    linkSuppressed = 0,
                    countInStats = 1
                )
            )
            for (sec in secondaries) {
                transactionDao.update(sec.copy(linkGroupId = null, linkSuppressed = 0, countInStats = 1))
            }
        }
    }

    suspend fun deleteTransactionById(id: String): Boolean {
        val tx = transactionDao.getById(id) ?: return false
        val groupId = tx.linkGroupId
        if (groupId != null) {
            if (tx.linkSuppressed == 1) {
                // Deleting a secondary: recompute the primary's net without this secondary
                val primary = transactionDao.getPrimaryForGroup(groupId)
                    ?: return transactionDao.deleteById(id) > 0
                val remainingSecondaries = transactionDao.listSecondariesForGroup(groupId)
                    .filter { it.id != id }
                if (remainingSecondaries.isEmpty()) {
                    // Last secondary removed — restore primary to its original amount
                    transactionDao.update(
                        primary.copy(
                            amount = primary.linkStashedAmount ?: primary.amount,
                            type = primary.linkStashedType ?: primary.type,
                            linkGroupId = null,
                            linkStashedAmount = null,
                            linkStashedType = null
                        )
                    )
                } else {
                    val primaryOrigAmount = primary.linkStashedAmount ?: primary.amount
                    val primaryOrigType = primary.linkStashedType ?: primary.type
                    val totalRemaining = remainingSecondaries.sumOf { it.amount }
                    val outcome = TransactionLinkHelper.debitCreditLinkOutcome(
                        primaryOrigType, primaryOrigAmount, totalRemaining, isFreshGroup = false
                    )
                    if (outcome is DebitCreditLinkOutcome.NetMerged) {
                        transactionDao.update(primary.copy(amount = outcome.amount, type = outcome.type))
                    }
                }
            } else {
                // Deleting the primary — free all secondaries
                val secondaries = transactionDao.listSecondariesForGroup(groupId)
                for (sec in secondaries) {
                    transactionDao.update(sec.copy(linkGroupId = null, linkSuppressed = 0, countInStats = 1))
                }
            }
        }
        return transactionDao.deleteById(id) > 0
    }

    suspend fun getMonthlyStats(): MonthlyStats {
        val cal = Calendar.getInstance()
        val monthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val row = transactionDao.getMonthlyStats(monthKey)
        val income = row.income
        val expense = row.expense
        return MonthlyStats(income, expense, income - expense)
    }

    /** Debit totals per calendar day for [monthKey] `YYYY-MM` (for home daily chart). */
    suspend fun getDailyDebitTotalsForMonth(monthKey: String): Map<String, Double> =
        transactionDao.getDailyDebitTotalsByMonth(monthKey).associate { it.date to it.total }

    suspend fun getCategoryStats(monthKey: String? = null): List<CategoryStat> {
        val rows = if (monthKey != null) transactionDao.getCategoryStats(monthKey) else transactionDao.getCategoryStatsAll()
        return rows.map { CategoryStat(it.category, it.amount) }
    }

    suspend fun getMerchantStats(monthKey: String? = null): List<MerchantStat> {
        val rows = if (monthKey != null) transactionDao.getMerchantStats(monthKey) else transactionDao.getMerchantStatsAll()
        return rows.map { MerchantStat(it.merchant, it.amount, it.transactionCount) }
    }

    suspend fun getMerchantStatsIncludingExcluded(monthKey: String? = null): List<MerchantStat> {
        if (monthKey != null) {
            val all = transactionDao.getMerchantStatsAllCountInStats(monthKey)
            val includedNames = transactionDao.getMerchantStats(monthKey).map { it.merchant }.toSet()
            return all.map { MerchantStat(it.merchant, it.amount, it.transactionCount, excluded = it.merchant !in includedNames) }
        }
        return transactionDao.getMerchantStatsAll().map { MerchantStat(it.merchant, it.amount, it.transactionCount) }
    }

    suspend fun getMerchantMonthlyTrend(merchant: String): List<MonthTrend> {
        val firstDate = transactionDao.getMerchantFirstDate(merchant) ?: return emptyList()
        val monthMap = transactionDao.getMerchantMonthlyTotals(merchant).associate { it.monthKey to kotlin.math.round(it.total).toDouble() }
        return buildMonthlyTrend(firstDate, monthMap)
    }

    suspend fun getTransactionsByMerchant(merchant: String): List<Transaction> =
        transactionDao.getByMerchant(merchant).map { it.toDomain() }

    suspend fun getCategoryMonthlyTrend(categoryId: String): List<MonthTrend> {
        val cat = categoryId.lowercase()
        val firstDate = transactionDao.getCategoryFirstDate(cat) ?: return emptyList()
        val monthMap = transactionDao.getCategoryMonthlyTotals(cat).associate { it.monthKey to kotlin.math.round(it.total).toDouble() }
        return buildMonthlyTrend(firstDate, monthMap)
    }

    suspend fun getTransactionsByCategory(categoryId: String): List<Transaction> =
        transactionDao.getByCategory(categoryId.lowercase()).map { it.toDomain() }

    suspend fun getAllMonthsExpenseTrend(): List<MonthTrend> {
        val firstDate = transactionDao.getFirstDebitDate()
        val cal = Calendar.getInstance()
        val curY = cal.get(Calendar.YEAR)
        val curM = cal.get(Calendar.MONTH) + 1

        val startCal = Calendar.getInstance()
        if (firstDate != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            startCal.time = sdf.parse(firstDate) ?: startCal.time
        } else {
            startCal.add(Calendar.MONTH, -11)
        }
        var y = startCal.get(Calendar.YEAR)
        var m = startCal.get(Calendar.MONTH) + 1

        val data = mutableListOf<MonthTrend>()
        while (y < curY || (y == curY && m <= curM)) {
            val monthKey = String.format("%04d-%02d", y, m)
            val total = transactionDao.getMonthExpenseTotal(y.toString(), String.format("%02d", m))
            data.add(MonthTrend(
                month = "${SmsConstants.MONTH_NAMES[m - 1]} '${y.toString().takeLast(2)}",
                monthKey = monthKey,
                amount = kotlin.math.round(total).toDouble()
            ))
            m++
            if (m > 12) { m = 1; y++ }
        }
        return data.sortedByDescending { it.monthKey }
    }

    // --- Wrapped feature ---

    suspend fun getMonthlyStatsForMonth(monthKey: String): MonthlyStats {
        val row = transactionDao.getMonthlyStats(monthKey)
        return MonthlyStats(row.income, row.expense, row.income - row.expense)
    }

    suspend fun getDebitTransactionsForMonth(monthKey: String): List<Transaction> =
        transactionDao.getDebitTransactionsForMonth(monthKey).map { it.toDomain() }

    suspend fun getMostExpensiveDayInMonth(monthKey: String) =
        transactionDao.getMostExpensiveDayInMonth(monthKey)

    suspend fun getTimeDistributionForMonth(monthKey: String) =
        transactionDao.getTimeDistributionForMonth(monthKey)

    // --- end Wrapped ---

    suspend fun getVelocityData(): VelocityData {
        val stats = getMonthlyStats()
        val trend = getAllMonthsExpenseTrend()
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysElapsed = cal.get(Calendar.DAY_OF_MONTH)
        val currentMonthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val previousTotals = trend.filter { it.monthKey != currentMonthKey }.take(6).map { it.amount }
        return VelocityData(stats.expense, daysElapsed, daysInMonth, previousTotals)
    }

    suspend fun getDailySpendingComparison(): DailySpendingComparison {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        val todayAmount = transactionDao.getDaySpending(today)
        val pastWeeks = mutableListOf<Double>()
        for (w in 1..16) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -w * 7)
            val dStr = sdf.format(cal.time)
            pastWeeks.add(kotlin.math.round(transactionDao.getDaySpending(dStr)).toDouble())
        }
        return DailySpendingComparison(kotlin.math.round(todayAmount).toDouble(), filterOutliers(pastWeeks))
    }

    private fun filterOutliers(values: List<Double>): List<Double> {
        if (values.isEmpty()) return values
        val nonZero = values.filter { it > 0 }
        if (nonZero.size < 3) return values
        val sorted = nonZero.sorted()
        val q1 = sorted[(sorted.size * 0.25).toInt()]
        val q3 = sorted[(sorted.size * 0.75).toInt()]
        val iqr = q3 - q1
        val threshold = q3 + 1.5 * iqr
        return values.filter { it == 0.0 || it <= threshold }
    }

    suspend fun getWeeklyCategoryComparison(): WeeklyCategoryComparison {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val diff = cal.get(Calendar.DAY_OF_MONTH) - (dow - Calendar.MONDAY)
        val thisStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, diff); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val lastStart = (thisStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
        val thisEnd = (thisStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val thisWeek = transactionDao.getCategoryStatsInRange(sdf.format(thisStart.time), sdf.format(thisEnd.time))
            .map { CategoryStat(it.category, kotlin.math.round(it.amount).toDouble()) }
        val lastWeek = transactionDao.getCategoryStatsInRange(sdf.format(lastStart.time), sdf.format(thisStart.time))
            .map { CategoryStat(it.category, kotlin.math.round(it.amount).toDouble()) }
        val topRow = transactionDao.getTopMerchantInRange(sdf.format(thisStart.time), sdf.format(thisEnd.time))
        val topMerchant = topRow?.let { TopMerchant(it.merchant, kotlin.math.round(it.amount).toDouble()) }
        return WeeklyCategoryComparison(thisWeek, lastWeek, topMerchant)
    }

    suspend fun getRepeatedMerchantsThisMonth(): List<RepeatedMerchant> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR).toString()
        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        return transactionDao.getRepeatedMerchants(year, month).map {
            RepeatedMerchant(it.merchant, it.transactionCount, kotlin.math.round(it.totalAmount).toDouble())
        }
    }

    suspend fun getRecurringTransactions(): List<RecurringTransaction> {
        val since = RecurringReminderHelper.recurringDetectionStartDateInclusive(
            RecurringReminderConstants.RECURRING_DETECTION_CALENDAR_MONTHS
        )
        val until = RecurringReminderHelper.recurringDetectionEndDateExclusive()
        val rows = transactionDao.getDebitGroupingDataBetween(since, until)
        // Track per-key: months seen, count per month, merchant display name, last date
        data class KeyData(
            val months: MutableSet<String> = mutableSetOf(),
            val monthCount: MutableMap<String, Int> = mutableMapOf(),
            var merchant: String = "",
            var amount: Double = 0.0,
            var lastDate: String? = null
        )
        val byKey = mutableMapOf<String, KeyData>()
        for (r in rows) {
            val merchant = r.merchant.trim()
            if (merchant.isEmpty()) continue
            val month = r.month.take(7)
            if (month.length < 7) continue
            val key = "${merchant.lowercase()}|${r.amount}"
            val entry = byKey.getOrPut(key) { KeyData(merchant = merchant, amount = r.amount, lastDate = r.txDate) }
            entry.months.add(month)
            entry.monthCount[month] = (entry.monthCount[month] ?: 0) + 1
            if (r.txDate != null && (entry.lastDate == null || r.txDate > entry.lastDate!!)) {
                entry.lastDate = r.txDate
            }
        }

        val dismissed = recurringDao.getAllDismissed()
            .map { "${it.merchant.trim().lowercase()}|${it.amount}" }
            .toSet()

        val result = mutableListOf<RecurringTransaction>()
        for ((_, v) in byKey) {
            val merchant = v.merchant
            val amount = v.amount
            val lastDate = v.lastDate
            val dimKey = "${merchant.lowercase()}|$amount"
            if (dismissed.contains(dimKey)) continue
            // Require exactly one transaction per month in every month seen
            if (v.monthCount.values.any { it > 1 }) continue

            val months = v.months
            if (months.size < 2) continue

            val sorted = months.sorted()
            val ordinals = sorted.mapNotNull { monthToOrdinal(it) }
            if (ordinals.size < 2) continue
            val gaps = (1 until ordinals.size).map { ordinals[it] - ordinals[it - 1] }
            val interval = gaps[0]
            if (gaps.all { it == interval } && interval in 1..12) {
                result.add(RecurringTransaction(merchant, amount, interval, lastDate))
            }
        }
        return result.sortedByDescending { it.amount }
    }

    private fun monthToOrdinal(monthStr: String): Int? {
        if (monthStr.length < 7) return null
        val parts = monthStr.split("-")
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return y * 12 + m
    }

    suspend fun getTimeBasedPatterns(): TimeBasedPatterns {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = Calendar.getInstance()
        val end = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val start = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -30) }
        val startStr = sdf.format(start.time)
        val endStr = sdf.format(end.time)

        val total = transactionDao.getDebitTotalInRange(startStr, endStr)
        val totalAmount = total.totalAmount ?: 0.0
        val late = transactionDao.getLateNightSpending(startStr, endStr)
        val weekday = transactionDao.getWeekdaySpending(startStr, endStr)
        val weekend = transactionDao.getWeekendSpending(startStr, endStr)

        val amounts = transactionDao.getDebitAmountsSorted(startStr, endStr)
        val p95Index = maxOf(0, (amounts.size * 0.95).toInt() - 1)
        val maxAmountForPeak = if (amounts.isNotEmpty()) amounts[p95Index] else Double.MAX_VALUE

        val peak = transactionDao.getPeakSpendingHour(startStr, endStr, maxAmountForPeak)
        val dist = transactionDao.getTimeDistribution(startStr, endStr, maxAmountForPeak)

        fun pct(amt: Double?) = if (totalAmount > 0) kotlin.math.round(((amt ?: 0.0) / totalAmount) * 1000.0) / 10.0 else 0.0

        return TimeBasedPatterns(
            lateNightSpending = SpendingBucket(late.count, kotlin.math.round(late.totalAmount ?: 0.0).toDouble(), pct(late.totalAmount)),
            weekdaySpending = SpendingBucket(weekday.count, kotlin.math.round(weekday.totalAmount ?: 0.0).toDouble(), pct(weekday.totalAmount)),
            weekendSpending = SpendingBucket(weekend.count, kotlin.math.round(weekend.totalAmount ?: 0.0).toDouble(), pct(weekend.totalAmount)),
            peakSpendingHour = TimeDistributionItem(peak?.hour ?: -1, peak?.count ?: 0, kotlin.math.round(peak?.totalAmount ?: 0.0).toDouble()),
            timeDistribution = dist.map { TimeDistributionItem(it.hour, it.count, kotlin.math.round(it.totalAmount).toDouble()) }
        )
    }

    suspend fun getDateRange(): DateRange {
        val row = transactionDao.getDateRange()
        return DateRange(row.minDate ?: "N/A", row.maxDate ?: "N/A", row.count)
    }

    suspend fun dismissRecurring(merchant: String, amount: Double) {
        recurringDao.dismiss(DismissedRecurringEntity(merchant.trim(), amount))
    }

    suspend fun executeQuery(sql: String): List<Map<String, Any?>> {
        if (SmsConstants.SQL_FORBIDDEN.any { sql.uppercase().contains(it) }) {
            throw IllegalArgumentException("Forbidden SQL operation")
        }
        // Room doesn't support raw queries returning arbitrary maps easily.
        // This will be handled via SupportSQLiteDatabase in the ViewModel/Repository.
        throw UnsupportedOperationException("Raw query execution requires SupportSQLiteDatabase access")
    }

    suspend fun getAllTransactionsForExport(): List<Transaction> =
        transactionDao.getAllForExport().map { it.toDomain() }

    suspend fun exportTransactionsCsvPayload(): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        val rows = getAllTransactionsForExport()
        rows.size to TransactionCsvHelper.toCsvByteArray(rows)
    }

    suspend fun importTransactionsFromCsv(bytes: ByteArray): TransactionImportResult = withContext(Dispatchers.IO) {
        val summary = TransactionCsvHelper.parseTransactionsCsv(bytes)
        if (summary.invalidHeader) {
            return@withContext TransactionImportResult(
                dataRowsRead = summary.dataRowsRead,
                rowsParsedOk = 0,
                rowsInserted = 0,
                rowsSkippedMalformed = summary.skippedMalformedRows,
                invalidHeader = true
            )
        }
        if (summary.transactions.isEmpty()) {
            return@withContext TransactionImportResult(
                dataRowsRead = summary.dataRowsRead,
                rowsParsedOk = 0,
                rowsInserted = 0,
                rowsSkippedMalformed = summary.skippedMalformedRows,
                invalidHeader = false
            )
        }
        val inserted = saveTransactionsBulk(summary.transactions)
        val skippedParse = summary.skippedMalformedRows
        val skippedDup = summary.transactions.size - inserted
        TransactionImportResult(
            dataRowsRead = summary.dataRowsRead,
            rowsParsedOk = summary.transactions.size,
            rowsInserted = inserted,
            rowsSkippedMalformed = skippedParse + skippedDup,
            invalidHeader = false
        )
    }

    suspend fun clearAllData() {
        transactionDao.deleteAll()
        customCategoryDao.deleteAll()
        merchantMappingDao.deleteAll()
        accountMappingDao.deleteAll()
        budgetDao.deleteAll()
        recurringDao.deleteAll()
        reminderDao.deleteAll()
    }

    // --- Account Mappings ---
    suspend fun getAccountMapping(last4Digits: String, bankName: String): AccountMapping? {
        val row = accountMappingDao.get(last4Digits, bankName) ?: return null
        return AccountMapping(row.bankName, row.accountType, row.isConfident)
    }

    suspend fun saveAccountMapping(last4Digits: String, bankName: String, accountType: String, isConfident: Boolean = true) {
        accountMappingDao.upsert(AccountMappingEntity(last4Digits, bankName, accountType, System.currentTimeMillis(), isConfident = isConfident))
    }

    suspend fun updateAccountType(last4Digits: String, bankName: String, newType: String) {
        accountMappingDao.updateType(last4Digits, bankName, newType, System.currentTimeMillis())
    }

    suspend fun deleteCardMapping(last4Digits: String, bankName: String) {
        accountMappingDao.upsert(
            AccountMappingEntity(
                last4Digits = last4Digits,
                bankName = bankName,
                accountType = "hidden",
                updatedAt = System.currentTimeMillis(),
                isConfident = true
            )
        )
    }

    suspend fun getAllMappedAccounts(): List<MappedAccount> =
        accountMappingDao.getAll().map { entity ->
            MappedAccount(
                last4Digits = entity.last4Digits,
                bankName = entity.bankName,
                isCreditCard = entity.accountType == "credit_card",
                isDebitCard = entity.accountType == "debit_card",
                availableBalance = entity.availableBalance,
                balanceUpdatedAt = entity.balanceUpdatedAt,
                isConfident = entity.isConfident,
                isHidden = entity.accountType == "hidden"
            )
        }.sortedWith(compareBy({ it.bankName.lowercase(Locale.US) }, { it.last4Digits }))

    suspend fun updateAccountBalance(last4Digits: String, balance: Double, updatedAt: Long) {
        accountMappingDao.updateBalanceByDigits(last4Digits, balance, updatedAt)
    }

    suspend fun saveAccountMappingsBulk(mappings: List<AccountMappingEntity>) {
        if (mappings.isEmpty()) return
        accountMappingDao.upsertAll(mappings)
    }

    /** Loads all account mappings into a (last4Digits, bankName) → AccountMapping map for zero-query processing. */
    suspend fun getAllAccountMappingsAsMap(): Map<Pair<String, String>, AccountMapping> =
        accountMappingDao.getAll().associate { (it.last4Digits to it.bankName) to AccountMapping(it.bankName, it.accountType) }

    // --- Merchant Name Mappings (user corrections: original → corrected) ---

    /**
     * Returns the user-corrected name for [originalMerchant], or null if no correction exists.
     * Lookup is case-insensitive (key is lowercased before storage and query).
     */
    suspend fun getMerchantNameMapping(originalMerchant: String): String? {
        val key = originalMerchant.trim().lowercase()
        if (key.isEmpty()) return null
        return merchantNameMappingDao.getCorrectedName(key)
    }

    /**
     * Persists a user-driven merchant name correction ([originalName] → [correctedName]).
     * [originalName] is normalized to lowercase so future SMS lookups match any casing.
     */
    suspend fun saveMerchantNameMapping(originalName: String, correctedName: String) {
        val key = originalName.trim().lowercase()
        if (key.isEmpty() || correctedName.isBlank()) return
        merchantNameMappingDao.upsert(
            MerchantNameMappingEntity(
                originalName = key,
                correctedName = correctedName.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Renames all transactions whose merchant matches [originalName] to [newName],
     * and saves a name-mapping so future SMS imports use the corrected name.
     */
    suspend fun bulkRenameMerchant(originalName: String, newName: String) {
        if (originalName.isBlank() || newName.isBlank()) return
        transactionDao.bulkUpdateMerchantName(originalName, newName)
        saveMerchantNameMapping(originalName, newName)
    }

    /** Loads all name corrections into a normalizedKey → correctedName map for zero-query processing. */
    suspend fun getAllMerchantNameMappingsAsMap(): Map<String, String> =
        merchantNameMappingDao.getAll().associate { it.originalName.trim().lowercase() to it.correctedName }

    // --- Merchant Category Mappings ---
    suspend fun getMerchantCategory(merchant: String): String? {
        val key = MerchantMappingKeys.normalizeMerchantKey(merchant)
        if (key.isEmpty()) return null
        return merchantMappingDao.getCategory(key)
    }

    suspend fun getMerchantCountInStats(merchant: String): Boolean? {
        val key = MerchantMappingKeys.normalizeMerchantKey(merchant)
        if (key.isEmpty()) return null
        return merchantMappingDao.getCountInStats(key)?.let { it == 1 }
    }

    /** Loads all local merchant→category and merchant→countInStats into maps for zero-query processing. */
    suspend fun getAllMerchantMappingsAsMaps(): Pair<Map<String, String>, Map<String, Boolean>> {
        val all = merchantMappingDao.getAll()
        val categories = all.associate { it.merchant to it.category }
        val countInStats = all
            .filter { it.countInStats != null }
            .associate { it.merchant to (it.countInStats == 1) }
        return categories to countInStats
    }

    /**
     * Persists merchant → category for SMS auto-categorization and future imports.
     * [merchant] is normalized for storage (trim + lowercase) so lookups match varied SMS casing.
     * Preserves any existing countInStats preference across the replace.
     */
    suspend fun saveMerchantCategoryMapping(merchant: String, category: String, previousCategory: String? = null) {
        val m = MerchantMappingKeys.normalizeMerchantKey(merchant)
        if (m.isEmpty()) return
        val c = MerchantMappingKeys.normalizeCategoryKey(category)
        val now = System.currentTimeMillis()
        val existingCountInStats = merchantMappingDao.getCountInStats(m)
        merchantMappingDao.deleteByNormalizedMerchantKey(m)
        merchantMappingDao.upsert(MerchantCategoryMappingEntity(m, c, now, existingCountInStats))
        if (previousCategory != null && previousCategory != c) {
            categoryChangeLogger.log(m, previousCategory, c)
        }
    }

    suspend fun saveMerchantCountInStatsMapping(merchant: String, countInStats: Boolean) {
        val m = MerchantMappingKeys.normalizeMerchantKey(merchant)
        if (m.isEmpty()) return
        val existing = merchantMappingDao.getCategory(m)
        if (existing != null) {
            merchantMappingDao.updateCountInStats(m, if (countInStats) 1 else 0)
        } else {
            val now = System.currentTimeMillis()
            merchantMappingDao.upsert(MerchantCategoryMappingEntity(m, "others", now, if (countInStats) 1 else 0))
        }
    }

    suspend fun getMerchantTransactionCount(merchant: String, excludeId: String? = null): Int =
        if (excludeId != null) transactionDao.getMerchantTransactionCountExcluding(merchant, excludeId)
        else transactionDao.getMerchantTransactionCount(merchant)

    suspend fun updateMerchantTransactionsCategory(merchant: String, category: String) {
        transactionDao.updateMerchantCategory(merchant, category)
    }

    suspend fun updateMerchantTransactionsCountInStats(merchant: String, countInStats: Boolean) {
        transactionDao.updateMerchantCountInStats(merchant, if (countInStats) 1 else 0)
        saveMerchantCountInStatsMapping(merchant, countInStats)
    }

    // --- Dynamic pattern reprocessing ---

    data class ReprocessResult(val examined: Int, val updated: Int, val skipped: Int)

    suspend fun reprocessUnmatchedTransactions(
        dynamicPatterns: List<SmsPatternEntity>,
        fullScan: Boolean = false
    ): ReprocessResult = withContext(Dispatchers.IO) {
        val candidates = if (fullScan) transactionDao.listAllWithOriginalSms()
                         else transactionDao.listNeedingReprocess()

        var updated = 0
        var skipped = 0

        for (entity in candidates) {
            val sms = entity.originalSms ?: continue
            val sender = entity.smsSender ?: ""

            val extracted = TransactionExtractor.extractTransactionData(sms, dynamicPatterns, sender)

            // Safety: skip if amount is missing or changed
            if (extracted.amount == null || extracted.amount <= 0.0 || extracted.amount != entity.amount) {
                transactionDao.clearReprocessFlag(entity.id)
                skipped++
                continue
            }

            // Only update when a dynamic pattern (not the generic fallback) improved the merchant
            val isImprovement = extracted.patternIndex != 7 &&
                !extracted.merchant.isNullOrBlank() &&
                extracted.merchant != entity.merchant

            if (isImprovement) {
                val categorized = TransactionCategorizer.categorizeTransaction(
                    extracted = extracted,
                    getMerchantCategory = { merchant -> getMerchantCategory(merchant) }
                )
                transactionDao.updateReprocessedFields(
                    id = entity.id,
                    merchant = categorized.merchant ?: entity.merchant,
                    category = categorized.category ?: entity.category,
                    type = extracted.type ?: entity.type,
                    accounts = extracted.fullAccount ?: entity.accounts
                )
                updated++
            } else {
                transactionDao.clearReprocessFlag(entity.id)
                skipped++
            }
        }

        ReprocessResult(examined = candidates.size, updated = updated, skipped = skipped)
    }

    // Entity <-> Domain mapping
    private fun TransactionEntity.toDomain() = Transaction(
        id = id, merchant = merchant, amount = amount, type = type,
        category = category, date = date, time = time, accounts = accounts,
        reference = reference, countInStats = countInStats == 1,
        originalSms = originalSms, smsSender = smsSender,
        smsDedupeHash = smsDedupeHash,
        linkGroupId = linkGroupId,
        linkSuppressed = linkSuppressed == 1,
        linkStashedAmount = linkStashedAmount,
        linkStashedType = linkStashedType
    )

    private fun Transaction.toEntity() = TransactionEntity(
        id = id, merchant = merchant, amount = amount, type = type,
        category = category, date = date, time = time, accounts = accounts,
        reference = reference, countInStats = if (countInStats) 1 else 0,
        originalSms = originalSms, smsSender = smsSender,
        smsDedupeHash = smsDedupeHash,
        linkGroupId = linkGroupId,
        linkSuppressed = if (linkSuppressed) 1 else 0,
        linkStashedAmount = linkStashedAmount,
        linkStashedType = linkStashedType,
        needsReprocess = if (isWeakMatch) 1 else 0
    )

    // --- Budget ---
    suspend fun getMonthlyBudget(): Double? = budgetDao.getMonthlyBudget()
    suspend fun setMonthlyBudget(amount: Double) = budgetDao.upsert(BudgetSettingsEntity("monthly", amount))
    suspend fun removeMonthlyBudget() = budgetDao.removeMonthlyBudget()
    suspend fun getCategoryBudgets(): Map<String, Double> {
        return budgetDao.getCategoryBudgets().associate {
            it.key.removePrefix("category_") to it.value
        }
    }
    suspend fun setCategoryBudget(categoryId: String, amount: Double) =
        budgetDao.upsert(BudgetSettingsEntity("category_$categoryId", amount))
    suspend fun removeCategoryBudget(categoryId: String) =
        budgetDao.remove("category_$categoryId")

    // --- Reminders ---
    fun getRemindersFlow(): Flow<List<Reminder>> =
        reminderDao.getAll().map { entities ->
            entities.map { it.toDomainReminder() }
        }

    suspend fun createReminder(
        type: String, amount: Double, category: String, merchant: String,
        frequency: String, reminderDate: String
    ): Reminder {
        val id = "reminder_${System.currentTimeMillis()}_${(1..9).map { ('a'..'z').random() }.joinToString("")}"
        val createdAt = System.currentTimeMillis()
        val entity = ReminderEntity(id, type, amount, category, merchant, frequency, reminderDate, createdAt)
        reminderDao.upsert(entity)
        return Reminder(id, type, amount, category, merchant, frequency, reminderDate, createdAt)
    }

    suspend fun getReminderById(id: String): Reminder? =
        reminderDao.getById(id)?.toDomainReminder()

    suspend fun deleteReminder(id: String): Boolean = reminderDao.delete(id) > 0

    suspend fun updateReminder(
        id: String,
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        frequency: String,
        reminderDate: String
    ) {
        val existing = reminderDao.getById(id) ?: return
        reminderDao.upsert(
            ReminderEntity(
                id = id,
                type = type,
                amount = amount,
                category = category,
                merchant = merchant,
                frequency = frequency,
                reminderDate = reminderDate,
                createdAt = existing.createdAt,
                paidOn = existing.paidOn
            )
        )
    }

    suspend fun markReminderPaid(id: String, paidOn: String) =
        reminderDao.setPaidOn(id, paidOn)

    suspend fun clearReminderPaid(id: String) =
        reminderDao.setPaidOn(id, null)

    /**
     * For each monthly reminder that is not yet marked paid this month,
     * checks if a matching debit transaction (same merchant + amount) exists
     * for [monthKey]. If found, auto-marks the reminder paid with today's date.
     */
    suspend fun hasMatchingTransactionInMonth(merchant: String, amount: Double, monthKey: String): Boolean =
        transactionDao.countMatchingTransactionInMonth(merchant, amount, monthKey) > 0

    suspend fun autoMarkRemindersIfTransactionFound(monthKey: String) {
        val today = java.time.LocalDate.now().toString()
        val reminders = reminderDao.getAllOnce()
        for (entity in reminders) {
            if (entity.frequency != "monthly") continue
            // Skip if already paid this month
            val paidOn = entity.paidOn
            if (paidOn != null) {
                val paidDate = runCatching { java.time.LocalDate.parse(paidOn) }.getOrNull()
                if (paidDate != null &&
                    paidDate.year.toString().padStart(4, '0') + "-" +
                    paidDate.monthValue.toString().padStart(2, '0') == monthKey
                ) continue
            }
            val count = transactionDao.countMatchingTransactionInMonth(entity.merchant, entity.amount, monthKey)
            if (count > 0) {
                reminderDao.setPaidOn(entity.id, today)
            }
        }
    }

    private fun ReminderEntity.toDomainReminder() = Reminder(
        id = id, type = type, amount = amount, category = category,
        merchant = merchant, frequency = frequency, reminder_date = reminderDate,
        created_at = createdAt, paid_on = paidOn
    )

    // --- Custom Categories ---
    fun getCustomCategoriesFlow(): Flow<List<CustomCategory>> =
        customCategoryDao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getCustomCategories(): List<CustomCategory> =
        customCategoryDao.getAllOnce().map { it.toDomain() }

    suspend fun saveCustomCategory(category: CustomCategory) {
        customCategoryDao.upsert(CustomCategoryEntity(category.id, category.name, category.icon, category.color, System.currentTimeMillis()))
    }

    /** @return New category id, or null if [name] is blank after trim. */
    suspend fun addCustomCategoryFromInput(name: String, emoji: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val icon = emoji.trim().ifEmpty { "📦" }
        val existingCategories = getCustomCategories()
        val existingIds = existingCategories.map { it.id }.toSet()
        val usedColors = existingCategories.map { it.color }.toSet()
        val availableColors = CUSTOM_CATEGORY_COLOR_PALETTE.filter { it !in usedColors }
        val color = if (availableColors.isNotEmpty()) {
            availableColors.random()
        } else {
            CUSTOM_CATEGORY_COLOR_PALETTE.random()
        }
        val id = CategoryCatalogHelper.uniqueCustomId(trimmed, existingIds)
        saveCustomCategory(
            CustomCategory(
                id = id,
                name = trimmed,
                icon = icon,
                color = color,
                createdAt = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun deleteCustomCategory(id: String) = customCategoryDao.delete(id)

    suspend fun deleteCustomCategoryAndRecategorize(id: String) {
        transactionDao.bulkUpdateCategory(id, "others")
        if (id.lowercase() in BUILT_IN_CATEGORY_IDS) {
            // Can't remove built-in from code, so store a tombstone row
            val existing = customCategoryDao.getById(id)
            val builtIn = BUILT_IN_CATEGORIES.find { it.first == id.lowercase() }
            customCategoryDao.upsert(
                CustomCategoryEntity(
                    id = id.lowercase(),
                    name = existing?.name ?: builtIn?.first?.replaceFirstChar { it.titlecase() } ?: id,
                    icon = existing?.icon ?: builtIn?.second ?: "❓",
                    color = existing?.color ?: builtIn?.third ?: DEFAULT_CUSTOM_CATEGORY_COLOR_HEX,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    hidden = 1
                )
            )
        } else {
            customCategoryDao.delete(id)
        }
    }

    suspend fun editCustomCategory(id: String, name: String, emoji: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val icon = emoji.trim().ifEmpty { "❓" }
        val existing = customCategoryDao.getById(id)
        if (existing != null) {
            customCategoryDao.upsert(existing.copy(name = trimmed, icon = icon, hidden = 0))
        } else {
            // Editing a built-in category for the first time — create an override entry
            val builtIn = BUILT_IN_CATEGORIES.find { it.first == id.lowercase() }
            val color = builtIn?.third ?: DEFAULT_CUSTOM_CATEGORY_COLOR_HEX
            customCategoryDao.upsert(
                CustomCategoryEntity(
                    id = id.lowercase(),
                    name = trimmed,
                    icon = icon,
                    color = color,
                    createdAt = System.currentTimeMillis(),
                    hidden = 0
                )
            )
        }
    }

    private fun CustomCategoryEntity.toDomain() = CustomCategory(id, name, icon, color, createdAt, hidden = hidden == 1)

    private fun buildMonthlyTrend(firstDate: String, monthMap: Map<String, Double>): List<MonthTrend> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val first = sdf.parse(firstDate) ?: return emptyList()
        val firstCal = Calendar.getInstance().apply { time = first }
        var y = firstCal.get(Calendar.YEAR)
        var m = firstCal.get(Calendar.MONTH) + 1
        val now = Calendar.getInstance()
        val curY = now.get(Calendar.YEAR)
        val curM = now.get(Calendar.MONTH) + 1
        val data = mutableListOf<MonthTrend>()
        while (y < curY || (y == curY && m <= curM)) {
            val monthKey = String.format("%04d-%02d", y, m)
            data.add(MonthTrend(
                month = "${SmsConstants.MONTH_NAMES[m - 1]} '${y.toString().takeLast(2)}",
                monthKey = monthKey,
                amount = monthMap[monthKey] ?: 0.0
            ))
            m++
            if (m > 12) { m = 1; y++ }
        }
        return data
    }
}
