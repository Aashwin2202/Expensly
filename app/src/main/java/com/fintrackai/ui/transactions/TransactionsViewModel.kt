package com.fintrackai.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.data.repository.WrongSmsRepository
import com.fintrackai.domain.transactions.TransactionLinkHelper
import com.fintrackai.domain.transactions.TransactionLinkResult
import com.fintrackai.domain.account.AccountSummaryHelper
import com.fintrackai.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import javax.inject.Inject

data class CategorySpendRow(
    val category: String,
    val amount: Double,
    val transactionCount: Int
)

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredDebitTotal: Double = 0.0,
    val filteredCreditTotal: Double = 0.0,
    val categoryStatsForMonth: List<CategoryStat> = emptyList(),
    val merchantStatsForMonth: List<MerchantStat> = emptyList(),
    val displayCategoryStats: List<CategoryStat> = emptyList(),
    val displayCategoryRows: List<CategorySpendRow> = emptyList(),
    val displayMerchants: List<MerchantStat> = emptyList(),
    val merchantSearchQuery: String = "",
    val merchantSortBy: String = "amount", // "amount" or "frequency"
    val monthlyTrend: List<MonthTrend> = emptyList(),
    val availableMonthKeys: List<String> = emptyList(),
    val selectedMonthKey: String? = null,
    val customDateStart: String? = null,
    val customDateEnd: String? = null,
    val searchQuery: String = "",
    val headerAccountTitle: String? = null,
    val selectedAccountKey: String? = null,
    val filterType: String? = null,
    val filterCategory: String? = null,
    val filterMinAmount: Double? = null,
    val filterMaxAmount: Double? = null,
    val sortBy: String = "date_desc",
    val activeTab: Int = 0,
    val showMonthlyGraph: Boolean = true,
    val loading: Boolean = true,
    val customCategories: List<CustomCategory> = emptyList(),
    /** Min / max `YYYY-MM-DD` across loaded transactions, for date-range presets. */
    val dataDateMin: String? = null,
    val dataDateMax: String? = null,
    /** Non-null while user is picking the other leg of a debit/credit combine on this screen. */
    val linkSelectionAnchor: Transaction? = null,
    /** True while combine picking is active (includes loading anchor from navigation). */
    val linkSelectionActive: Boolean = false,
    /** IDs of transactions selected via long-press for bulk actions. */
    val selectedTransactionIds: Set<String> = emptySet(),
    val isInSelectionMode: Boolean = false,
    val isCard: Boolean = false,
    val knownAccountOptions: List<String> = emptyList()
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val wrongSmsRepo: WrongSmsRepository,
    private val analytics: AnalyticsHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var allTransactionsCache: List<Transaction> = emptyList()

    private var linkSelectionAnchorId: String? = null
    private var linkSelectionAnchorSnapshot: Transaction? = null
    private var linkSelectionOpenedFromExternalNav: Boolean = false
    private var linkSelectionPrevFilterType: String? = null

    companion object {
        private val MONTH_KEY_REGEX = Regex("""^(\d{4}-\d{2})-\d{2}$""")
        private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
    }

    init {
        val rawKey = savedStateHandle.get<String>("accountKey").orEmpty()
        val rawTitle = savedStateHandle.get<String>("accountTitle").orEmpty()
        val rawType = savedStateHandle.get<String>("typeFilter").orEmpty()
        val rawDateStart = savedStateHandle.get<String>("dateStart").orEmpty()
        val rawDateEnd = savedStateHandle.get<String>("dateEnd").orEmpty()
        val titleDecoded = runCatching {
            URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawTitle).takeIf { it.isNotBlank() }
        val ds = runCatching { URLDecoder.decode(rawDateStart, StandardCharsets.UTF_8.name()) }.getOrDefault(rawDateStart)
        val de = runCatching { URLDecoder.decode(rawDateEnd, StandardCharsets.UTF_8.name()) }.getOrDefault(rawDateEnd)
        val dateStartOk = ds.takeIf { it.matches(Regex("""^\d{4}-\d{2}-\d{2}$""")) }
        val dateEndOk = de.takeIf { it.matches(Regex("""^\d{4}-\d{2}-\d{2}$""")) }
        val customPair: Pair<String, String>? = if (dateStartOk != null && dateEndOk != null) {
            if (dateStartOk <= dateEndOk) dateStartOk to dateEndOk else dateEndOk to dateStartOk
        } else null
        val isCard = savedStateHandle.get<Boolean>("isCard") ?: false
        val initialTab = when (rawType) {
            "__tab_categories__" -> 1
            "__tab_merchants__" -> 2
            else -> 0
        }
        val effectiveFilterType = rawType.takeIf { it.isNotBlank() && !it.startsWith("__tab_") }
            ?: if (customPair != null || rawType.startsWith("__tab_")) "debit" else null
        val rawCategoryFilter = savedStateHandle.get<String>("categoryFilter").orEmpty()
        val effectiveCategoryFilter = rawCategoryFilter.takeIf { it.isNotBlank() }
        _uiState.value = _uiState.value.copy(
            selectedAccountKey = rawKey.takeIf { it.isNotBlank() },
            headerAccountTitle = titleDecoded,
            filterType = effectiveFilterType,
            filterCategory = effectiveCategoryFilter,
            customDateStart = customPair?.first,
            customDateEnd = customPair?.second,
            selectedMonthKey = customPair?.first?.take(7),
            isCard = isCard,
            activeTab = initialTab,
            showMonthlyGraph = true
        )

        val rawLinkAnchor = savedStateHandle.get<String>("linkAnchorId")?.trim().orEmpty()
        if (rawLinkAnchor.isNotEmpty()) {
            linkSelectionOpenedFromExternalNav = true
            linkSelectionAnchorId = rawLinkAnchor
            viewModelScope.launch {
                val loaded = repo.getTransactionById(rawLinkAnchor)
                linkSelectionAnchorSnapshot = loaded
                if (loaded == null) clearLinkSelectionState()
                recomputeDerived()
            }
        }

        viewModelScope.launch {
            repo.getAllTransactionsFlow().collect { all ->
                allTransactionsCache = all
                val months = extractMonthKeys(all)
                val opts = all.mapNotNull { tx -> tx.accounts.trim().takeIf { it.isNotEmpty() } }.distinct().sorted()
                val cur = _uiState.value
                val monthKey = when {
                    cur.customDateStart != null && cur.customDateEnd != null ->
                        cur.customDateStart!!.take(7)
                    cur.selectedMonthKey != null -> cur.selectedMonthKey
                    else -> run {
                        val current = currentMonthKey()
                        when {
                            months.contains(current) -> current
                            months.isNotEmpty() -> months.first()
                            else -> current
                        }
                    }
                }
                val cat = repo.getCategoryStats(monthKey)
                val merch = repo.getMerchantStatsIncludingExcluded(monthKey)
                _uiState.value = cur.copy(
                    selectedMonthKey = monthKey,
                    availableMonthKeys = months,
                    categoryStatsForMonth = cat,
                    merchantStatsForMonth = merch,
                    knownAccountOptions = opts
                )
                recomputeDerived()
            }
        }
        viewModelScope.launch {
            repo.getCustomCategoriesFlow().collect { custom ->
                _uiState.value = _uiState.value.copy(customCategories = custom)
            }
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
        recomputeDerived()
    }

    fun setMerchantSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(merchantSearchQuery = q)
        recomputeDerived()
    }

    fun setMerchantSortBy(sort: String) {
        _uiState.value = _uiState.value.copy(merchantSortBy = sort)
        recomputeDerived()
    }

    fun setShowMonthlyGraph(show: Boolean) {
        _uiState.value = _uiState.value.copy(showMonthlyGraph = show)
    }

    fun setActiveTab(tab: Int) {
        analytics.logTransactionTabChanged(tab)
        val prev = _uiState.value
        _uiState.value = prev.copy(
            activeTab = tab,
            showMonthlyGraph = false
        )
        if (tab == 1 || tab == 2) {
            viewModelScope.launch { refreshCategoryMerchantStatsForSelectedPeriod() }
        }
    }

    fun selectMonth(monthKey: String) {
        viewModelScope.launch {
            val cat = repo.getCategoryStats(monthKey)
            val merch = repo.getMerchantStatsIncludingExcluded(monthKey)
            _uiState.value = _uiState.value.copy(
                selectedMonthKey = monthKey,
                customDateStart = null,
                customDateEnd = null,
                categoryStatsForMonth = cat,
                merchantStatsForMonth = merch
            )
            recomputeDerived()
        }
    }

    fun setCustomDateRange(start: String, end: String) {
        val range = if (start <= end) Pair(start, end) else Pair(end, start)
        _uiState.value = _uiState.value.copy(
            customDateStart = range.first,
            customDateEnd = range.second,
            showMonthlyGraph = false
        )
        recomputeDerived()
    }

    fun clearCustomDateRange() {
        _uiState.value = _uiState.value.copy(customDateStart = null, customDateEnd = null)
        recomputeDerived()
    }

    fun clearAccountNavArgs() {
        _uiState.value = _uiState.value.copy(selectedAccountKey = null, headerAccountTitle = null)
        recomputeDerived()
    }

    fun applyFilter(type: String?, category: String?, min: Double?, max: Double?) {
        analytics.logTransactionFilterApplied(type, category != null, min != null || max != null)
        _uiState.value = _uiState.value.copy(
            filterType = type,
            filterCategory = category,
            filterMinAmount = min,
            filterMaxAmount = max
        )
        recomputeDerived()
    }

    fun resetFilters() {
        _uiState.value = _uiState.value.copy(
            filterType = null,
            filterCategory = null,
            filterMinAmount = null,
            filterMaxAmount = null,
            searchQuery = ""
        )
        recomputeDerived()
    }

    fun setSort(sort: String) {
        analytics.logTransactionSortChanged(sort)
        _uiState.value = _uiState.value.copy(sortBy = sort)
        recomputeDerived()
    }

    suspend fun countTransactionsForMerchant(merchant: String): Int {
        val m = merchant.trim()
        if (m.isBlank()) return 0
        return repo.getMerchantTransactionCount(m)
    }

    fun updateTransactionCategory(tx: Transaction, category: String, mode: MerchantCategoryApplyMode) {
        analytics.logTransactionCategoryChanged(
            from = tx.category,
            to = category,
            appliedToAll = mode == MerchantCategoryApplyMode.ALL_FOR_MERCHANT
        )
        val isInvestment = category.equals("investment", ignoreCase = true)
        // Optimistic update: patch cache immediately so the list re-renders without waiting for DB
        allTransactionsCache = allTransactionsCache.map { t ->
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY ->
                    if (t.id == tx.id) t.copy(
                        category = category,
                        countInStats = if (isInvestment) false else t.countInStats
                    ) else t
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT ->
                    if (t.merchant.equals(tx.merchant, ignoreCase = true)) t.copy(
                        category = category,
                        countInStats = if (isInvestment) false else t.countInStats
                    ) else t
            }
        }
        recomputeDerived()
        viewModelScope.launch {
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY -> {
                    val updated = tx.copy(
                        category = category,
                        countInStats = if (isInvestment) false else tx.countInStats
                    )
                    repo.updateTransaction(updated)
                    if (tx.merchant.isNotBlank()) {
                        repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    }
                }
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT -> {
                    repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    repo.updateMerchantTransactionsCategory(tx.merchant, category)
                    if (isInvestment) {
                        repo.updateMerchantTransactionsCountInStats(tx.merchant, false)
                    }
                }
            }
            val mk = _uiState.value.selectedMonthKey ?: return@launch
            val cat = repo.getCategoryStats(mk)
            val merch = repo.getMerchantStatsIncludingExcluded(mk)
            _uiState.value = _uiState.value.copy(categoryStatsForMonth = cat, merchantStatsForMonth = merch)
            recomputeDerived()
        }
    }

    fun updateTransactionCountInStats(tx: Transaction, countInStats: Boolean, mode: MerchantCategoryApplyMode) {
        viewModelScope.launch {
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY -> {
                    repo.updateTransaction(tx.copy(countInStats = countInStats))
                }
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT -> {
                    repo.updateMerchantTransactionsCountInStats(tx.merchant, countInStats)
                }
            }
            val mk = _uiState.value.selectedMonthKey ?: return@launch
            val cat = repo.getCategoryStats(mk)
            val merch = repo.getMerchantStatsIncludingExcluded(mk)
            _uiState.value = _uiState.value.copy(categoryStatsForMonth = cat, merchantStatsForMonth = merch)
            recomputeDerived()
        }
    }

    fun saveTransaction(tx: Transaction) {
        viewModelScope.launch {
            repo.updateTransaction(tx)
            if (tx.merchant.isNotBlank()) {
                repo.saveMerchantCategoryMapping(tx.merchant, tx.category)
            }
            val mk = _uiState.value.selectedMonthKey ?: return@launch
            val cat = repo.getCategoryStats(mk)
            val merch = repo.getMerchantStatsIncludingExcluded(mk)
            _uiState.value = _uiState.value.copy(categoryStatsForMonth = cat, merchantStatsForMonth = merch)
            recomputeDerived()
        }
    }

    fun renameMerchant(originalName: String, newName: String, applyToAll: Boolean) {
        analytics.logTransactionMerchantRenamed(applyToAll)
        viewModelScope.launch {
            if (applyToAll) {
                repo.bulkRenameMerchant(originalName, newName)
            } else {
                repo.saveMerchantNameMapping(originalName, newName)
            }
        }
    }

    fun deleteTransaction(id: String) {
        analytics.logTransactionDeleted()
        viewModelScope.launch {
            repo.deleteTransactionById(id)
            val s = _uiState.value
            val mk = s.selectedMonthKey
            if (mk != null) {
                val cat = repo.getCategoryStats(mk)
                val merch = repo.getMerchantStatsIncludingExcluded(mk)
                _uiState.value = s.copy(categoryStatsForMonth = cat, merchantStatsForMonth = merch)
            }
            recomputeDerived()
        }
    }

    fun reportWrongDetection(tx: Transaction, reason: String, comments: String) {
        analytics.logTransactionWrongDetectionReported()
        val rawSms = tx.originalSms ?: return
        viewModelScope.launch {
            wrongSmsRepo.report(
                rawSms = rawSms,
                smsSender = tx.smsSender,
                reason = reason,
                comments = comments,
                detectedMerchant = tx.merchant.takeIf { it.isNotBlank() },
                detectedAmount = tx.amount,
                detectedType = tx.type.takeIf { it.isNotBlank() },
                detectedCategory = tx.category.takeIf { it.isNotBlank() },
                detectedDate = tx.date.takeIf { it.isNotBlank() },
                detectedTime = tx.time.takeIf { it.isNotBlank() },
                detectedAccounts = tx.accounts.takeIf { it.isNotBlank() },
                detectedReference = tx.reference
            )
        }
    }

    suspend fun getSecondariesForGroup(groupId: String): List<Transaction> = repo.getSecondariesForGroup(groupId)

    suspend fun getPrimaryForGroup(groupId: String): Transaction? = repo.getPrimaryForGroup(groupId)

    fun toggleSelection(id: String) {
        val current = _uiState.value.selectedTransactionIds
        val updated = if (id in current) current - id else current + id
        _uiState.value = _uiState.value.copy(
            selectedTransactionIds = updated,
            isInSelectionMode = updated.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTransactionIds = emptySet(),
            isInSelectionMode = false
        )
    }

    fun deleteSelected() {
        val count = _uiState.value.selectedTransactionIds.size
        analytics.logTransactionBulkDeleted(count)
        viewModelScope.launch {
            val ids = _uiState.value.selectedTransactionIds.toList()
            for (id in ids) repo.deleteTransactionById(id)
            clearSelection()
            refreshMonthStatsFromRepo()
        }
    }

    fun mergeSelected(onFinished: (TransactionLinkResult) -> Unit) {
        analytics.logTransactionMerged()
        viewModelScope.launch {
            val ids = _uiState.value.selectedTransactionIds.toList()
            val result = repo.mergeTransactions(ids)
            if (result == TransactionLinkResult.Success) {
                clearSelection()
                refreshMonthStatsFromRepo()
            }
            onFinished(result)
        }
    }

    fun unmergeSelected() {
        analytics.logTransactionUnmerged()
        viewModelScope.launch {
            val ids = _uiState.value.selectedTransactionIds.toList()
            val seenGroups = mutableSetOf<String>()
            for (id in ids) {
                val tx = allTransactionsCache.find { it.id == id } ?: continue
                val gid = tx.linkGroupId ?: continue
                if (seenGroups.add(gid)) {
                    repo.unlinkTransactionPair(id)
                }
            }
            clearSelection()
            refreshMonthStatsFromRepo()
        }
    }

    /** @return true if the host should [popBackStack] (opened from Home / detail via navigation). */
    fun cancelLinkSelection(): Boolean {
        analytics.logTransactionLinkCancelled()
        val shouldPop = linkSelectionOpenedFromExternalNav
        clearLinkSelectionState()
        recomputeDerived()
        return shouldPop
    }

    fun linkPairFromSelection(
        anchor: Transaction,
        peer: Transaction,
        onFinished: (TransactionLinkResult, shouldPopBack: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val r = repo.linkDebitCreditPair(anchor.id, peer.id)
            if (r == TransactionLinkResult.Success) {
                analytics.logTransactionLinkCompleted()
                refreshMonthStatsFromRepo()
            }
            val shouldPop = if (r == TransactionLinkResult.Success) {
                val p = linkSelectionOpenedFromExternalNav
                clearLinkSelectionState()
                recomputeDerived()
                p
            } else false
            onFinished(r, shouldPop)
        }
    }

    private fun clearLinkSelectionState() {
        _uiState.value = _uiState.value.copy(filterType = linkSelectionPrevFilterType)
        linkSelectionAnchorId = null
        linkSelectionAnchorSnapshot = null
        linkSelectionOpenedFromExternalNav = false
        linkSelectionPrevFilterType = null
    }

    private fun resolveLinkSelectionAnchor(): Transaction? {
        val id = linkSelectionAnchorId ?: return null
        return allTransactionsCache.find { it.id == id }
            ?: linkSelectionAnchorSnapshot?.takeIf { it.id == id }
    }

    fun unsplitPair(visibleRowId: String) {
        viewModelScope.launch {
            repo.unlinkTransactionPair(visibleRowId)
            refreshMonthStatsFromRepo()
        }
    }

    private suspend fun refreshMonthStatsFromRepo() {
        val mk = _uiState.value.selectedMonthKey
        if (mk != null) {
            val cat = repo.getCategoryStats(mk)
            val merch = repo.getMerchantStatsIncludingExcluded(mk)
            _uiState.value = _uiState.value.copy(categoryStatsForMonth = cat, merchantStatsForMonth = merch)
        }
        recomputeDerived()
    }

    fun saveCustomCategory(name: String, emoji: String, onSaved: (String) -> Unit) {
        analytics.logCustomCategoryCreated()
        viewModelScope.launch {
            val id = repo.addCustomCategoryFromInput(name, emoji) ?: return@launch
            onSaved(id)
        }
    }

    fun deleteCustomCategory(id: String) {
        analytics.logCustomCategoryDeleted()
        viewModelScope.launch { repo.deleteCustomCategoryAndRecategorize(id) }
    }

    fun editCustomCategory(id: String, name: String, emoji: String) {
        analytics.logCustomCategoryEdited()
        viewModelScope.launch { repo.editCustomCategory(id, name, emoji) }
    }

    private suspend fun refreshCategoryMerchantStatsForSelectedPeriod() {
        val s = _uiState.value
        when {
            s.customDateStart != null && s.customDateEnd != null -> {
                _uiState.value = s.copy(categoryStatsForMonth = emptyList(), merchantStatsForMonth = emptyList())
            }
            s.selectedMonthKey != null -> {
                val cat = repo.getCategoryStats(s.selectedMonthKey)
                val merch = repo.getMerchantStatsIncludingExcluded(s.selectedMonthKey)
                _uiState.value = s.copy(categoryStatsForMonth = cat, merchantStatsForMonth = merch)
            }
            else -> { /* account-only: lists are derived from filtered transactions */ }
        }
        recomputeDerived()
    }

    private fun recomputeDerived() {
        viewModelScope.launch(Dispatchers.Default) {
            val cur = _uiState.value
            val linkAnchor = resolveLinkSelectionAnchor()
            val inLinkPickMode = linkSelectionAnchorId != null
            val s = cur.copy(
                linkSelectionAnchor = linkAnchor,
                activeTab = if (inLinkPickMode) 0 else cur.activeTab
            )
            val filteredBase = filterTransactions(allTransactionsCache, s)
            val sortedTx = sortTransactions(filteredBase, s.sortBy)
            val useDerivedCats = s.selectedAccountKey != null ||
                (s.customDateStart != null && s.customDateEnd != null) ||
                s.filterType == "credit"
            val (pieCats, catRows, merchants) = if (useDerivedCats) {
                buildCategoryAndMerchantFromTransactions(filteredBase, s.filterType)
            } else {
                // DAO stats exclude investment (countInStats=0); merge it in from cache
                val mk = s.selectedMonthKey
                val daoStats = if (mk != null) {
                    val investmentAmount = allTransactionsCache
                        .filter { it.date.startsWith(mk) && it.type == "debit" && it.amount > 0 && it.category.lowercase() == "investment" }
                        .sumOf { it.amount }
                    if (investmentAmount > 0) {
                        val existing = s.categoryStatsForMonth.toMutableList()
                        existing.removeAll { it.category.lowercase() == "investment" }
                        (existing + CategoryStat("investment", investmentAmount)).sortedByDescending { it.amount }
                    } else {
                        s.categoryStatsForMonth
                    }
                } else {
                    s.categoryStatsForMonth
                }
                Triple(
                    daoStats,
                    buildCategoryRowsFromMonth(s),
                    s.merchantStatsForMonth
                )
            }
            val graphTrend = reorderTrendForNewestOnRight(buildMonthlyBarTrend(s))
            val (dataMin, dataMax) = transactionDateExtent(allTransactionsCache, s.selectedAccountKey)
            val searchedMerchants = if (s.merchantSearchQuery.isBlank()) merchants
                else merchants.filter { it.merchant.contains(s.merchantSearchQuery.trim(), ignoreCase = true) }
            val filteredMerchants = when (s.merchantSortBy) {
                "frequency" -> searchedMerchants.sortedByDescending { it.transactionCount }
                else -> searchedMerchants.sortedByDescending { it.amount }
            }
            val debitTotal = sortedTx.filter { it.type.equals("debit", ignoreCase = true) }.sumOf { it.amount }
            val creditTotal = sortedTx.filter { it.type.equals("credit", ignoreCase = true) }.sumOf { it.amount }
            _uiState.value = s.copy(
                transactions = sortedTx,
                filteredDebitTotal = debitTotal,
                filteredCreditTotal = creditTotal,
                displayCategoryStats = pieCats,
                displayCategoryRows = catRows.distinctBy { it.category },
                displayMerchants = filteredMerchants,
                monthlyTrend = graphTrend,
                dataDateMin = dataMin,
                dataDateMax = dataMax,
                linkSelectionAnchor = linkAnchor,
                linkSelectionActive = linkSelectionAnchorId != null,
                loading = false
            )
        }
    }

    private fun transactionDateExtent(
        transactions: List<Transaction>,
        selectedAccountKey: String?
    ): Pair<String?, String?> {
        var min: String? = null
        var max: String? = null
        for (t in transactions) {
            if (!DATE_REGEX.matches(t.date)) continue
            if (selectedAccountKey != null) {
                val txKey = AccountSummaryHelper.normalizeAccountKeyForMatch(t.accounts)
                if (txKey != selectedAccountKey) continue
            }
            if (min == null || t.date < min) min = t.date
            if (max == null || t.date > max) max = t.date
        }
        return min to max
    }

    /**
     * Monthly totals for the bar chart: respects account, search/filters (not month), and custom date range.
     * Always computes net (credit − debit) regardless of active tab; tab selection only affects the list/stats below the chart.
     * Net mode is suppressed for card accounts, explicit type filters, or custom date ranges (spending-focused contexts).
     */
    private fun buildMonthlyBarTrend(s: TransactionsUiState): List<MonthTrend> {
        val (rangeStart, rangeEnd) = graphMonthRange(s)
        val monthKeys = TransactionsGraphHelper.enumerateMonthsInclusive(rangeStart, rangeEnd)
        if (monthKeys.isEmpty()) return emptyList()

        val sums = monthKeys.associateWith { 0.0 }.toMutableMap()
        // The graph always shows net (credit − debit) regardless of the selected tab — the tab only
        // controls the list/stats below the chart. Net mode is suppressed only for card accounts,
        // explicit type filters, or custom date ranges (spending-focused contexts).
        val graphState = if (s.activeTab != 0) s.copy(activeTab = 0) else s
        val netCreditMinusDebit = s.filterType == null && !s.isCard && s.customDateStart == null
        for (tx in allTransactionsCache) {
            if (!matchesGraphTransaction(tx, graphState)) continue
            val mk = tx.date.take(7)
            if (mk.length != 7 || !sums.containsKey(mk)) continue
            if (netCreditMinusDebit) {
                when {
                    tx.type.equals("credit", ignoreCase = true) ->
                        sums[mk] = (sums[mk] ?: 0.0) + tx.amount
                    tx.type.equals("debit", ignoreCase = true) ->
                        sums[mk] = (sums[mk] ?: 0.0) - tx.amount
                }
            } else {
                sums[mk] = (sums[mk] ?: 0.0) + tx.amount
            }
        }
        return monthKeys.map { mk -> TransactionsGraphHelper.monthTrendRow(mk, sums[mk] ?: 0.0) }
    }

    private fun graphMonthRange(s: TransactionsUiState): Pair<String, String> {
        if (s.customDateStart != null && s.customDateEnd != null) {
            val a = s.customDateStart!!.take(7)
            val b = s.customDateEnd!!.take(7)
            return if (a <= b) a to b else b to a
        }
        val keys = extractMonthKeys(allTransactionsCache)
        if (keys.isEmpty()) {
            val c = currentMonthKey()
            return c to c
        }
        val sorted = keys.sorted()
        return sorted.first() to sorted.last()
    }

    private fun matchesGraphTransaction(tx: Transaction, s: TransactionsUiState): Boolean {
        if (tx.amount <= 0 || !tx.countInStats) return false

        val txKey = AccountSummaryHelper.normalizeAccountKeyForMatch(tx.accounts)
        if (s.selectedAccountKey != null && txKey != s.selectedAccountKey) return false

        if (s.customDateStart != null && s.customDateEnd != null) {
            if (tx.date < s.customDateStart!! || tx.date > s.customDateEnd!!) return false
        }

        return when (s.activeTab) {
            1, 2 -> tx.type == "debit"
            else -> {
                val matchesSearch = s.searchQuery.isBlank() ||
                    tx.merchant.contains(s.searchQuery, ignoreCase = true)
                val matchesCategory = s.filterCategory == null ||
                    tx.category.equals(s.filterCategory, ignoreCase = true)
                // For card accounts with no explicit type filter, graph shows debit spending only.
                val matchesType = when {
                    s.filterType != null -> tx.type == s.filterType
                    s.isCard -> tx.type == "debit"
                    else -> true
                }
                val matchesMin = s.filterMinAmount == null || tx.amount >= s.filterMinAmount
                val matchesMax = s.filterMaxAmount == null || tx.amount <= s.filterMaxAmount
                matchesSearch && matchesCategory && matchesType && matchesMin && matchesMax
            }
        }
    }

    private fun buildCategoryRowsFromMonth(s: TransactionsUiState): List<CategorySpendRow> {
        val mk = s.selectedMonthKey ?: return emptyList()
        val targetType = s.filterType ?: "debit"
        val map = mutableMapOf<String, Pair<Double, Int>>()
        for (t in allTransactionsCache) {
            if (!t.date.startsWith(mk)) continue
            if (t.type != targetType) continue
            if (t.amount <= 0) continue
            val cat = t.category.lowercase().ifBlank { "others" }
            if (targetType == "debit") {
                // Include investment regardless of countInStats; skip other non-counting transactions
                if (!t.countInStats && cat != "investment") continue
            } else {
                if (!t.countInStats) continue
            }
            val p = map.getOrDefault(cat, 0.0 to 0)
            map[cat] = (p.first + t.amount) to (p.second + 1)
        }
        return map.entries.map { e ->
            CategorySpendRow(e.key, e.value.first, e.value.second)
        }.sortedByDescending { it.amount }
    }

    private fun buildCategoryAndMerchantFromTransactions(
        filtered: List<Transaction>,
        filterType: String? = null
    ): Triple<List<CategoryStat>, List<CategorySpendRow>, List<MerchantStat>> {
        val targetType = filterType ?: "debit"
        val catMap = mutableMapOf<String, Pair<Double, Int>>()
        val merchMap = mutableMapOf<String, Pair<Double, Int>>()
        val merchantIncluded = mutableSetOf<String>()
        for (t in filtered) {
            if (t.amount <= 0) continue
            if (t.type == targetType) {
                val cat = t.category.lowercase().ifBlank { "others" }
                val countable = if (targetType == "debit") t.countInStats || cat == "investment"
                               else t.countInStats
                if (countable) {
                    val p = catMap.getOrDefault(cat, 0.0 to 0)
                    catMap[cat] = (p.first + t.amount) to (p.second + 1)
                }
            }
            if (t.type == targetType) {
                val m = t.merchant.trim()
                if (m.isNotEmpty()) {
                    val p = merchMap.getOrDefault(m, 0.0 to 0)
                    merchMap[m] = (p.first + t.amount) to (p.second + 1)
                    if (t.countInStats) merchantIncluded.add(m)
                }
            }
        }
        val pie = catMap.entries.map { CategoryStat(it.key, it.value.first) }.sortedByDescending { it.amount }
        val rows = catMap.entries.map { e ->
            CategorySpendRow(e.key, e.value.first, e.value.second)
        }.sortedByDescending { it.amount }
        val merch = merchMap.entries.map { e ->
            MerchantStat(e.key, e.value.first, e.value.second, excluded = e.key !in merchantIncluded)
        }.sortedByDescending { it.amount }
        return Triple(pie, rows, merch)
    }

    private fun filterTransactions(all: List<Transaction>, s: TransactionsUiState): List<Transaction> {
        val linkAnchor = if (linkSelectionAnchorId != null) s.linkSelectionAnchor else null
        val forcedType = linkAnchor?.let {
            TransactionLinkHelper.oppositeType(TransactionLinkHelper.anchorOriginalType(it))
        }

        return all.filter { transaction ->
            if (transaction.amount <= 0) return@filter false

            // In link-pick mode exclude the anchor itself and non-eligible rows
            if (linkAnchor != null) {
                if (transaction.id == linkAnchor.id) return@filter false
                if (!TransactionLinkHelper.canOfferLink(transaction)) return@filter false
            }

            val matchesSearch = s.searchQuery.isBlank() ||
                transaction.merchant.contains(s.searchQuery, ignoreCase = true)

            val txKey = AccountSummaryHelper.normalizeAccountKeyForMatch(transaction.accounts)
            val matchesAccount = s.selectedAccountKey == null || txKey == s.selectedAccountKey

            val matchesCategory = s.filterCategory == null ||
                transaction.category.equals(s.filterCategory, ignoreCase = true)
            // In link-pick mode force the opposite type; otherwise respect the active filter
            val matchesType = transaction.type.equals(forcedType ?: s.filterType ?: transaction.type, ignoreCase = true)
            val matchesMin = s.filterMinAmount == null || transaction.amount >= s.filterMinAmount
            val matchesMax = s.filterMaxAmount == null || transaction.amount <= s.filterMaxAmount

            val matchesDate = when {
                s.customDateStart != null && s.customDateEnd != null ->
                    transaction.date >= s.customDateStart && transaction.date <= s.customDateEnd
                s.selectedMonthKey != null ->
                    transaction.date.startsWith(s.selectedMonthKey)
                else -> true
            }

            matchesSearch && matchesAccount && matchesCategory && matchesType &&
                matchesMin && matchesMax && matchesDate
        }
    }

    private fun sortTransactions(list: List<Transaction>, sortBy: String): List<Transaction> {
        return when (sortBy) {
            "date_asc" -> list.sortedWith(compareBy({ it.date }, { it.time }))
            "amount_desc" -> list.sortedByDescending { it.amount }
            "amount_asc" -> list.sortedBy { it.amount }
            "merchant_asc" -> list.sortedBy { it.merchant.lowercase() }
            else -> list.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.time })
        }
    }

    private fun extractMonthKeys(transactions: List<Transaction>): List<String> {
        val set = mutableSetOf<String>()
        for (t in transactions) {
            val m = MONTH_KEY_REGEX.find(t.date) ?: continue
            val y = m.groupValues[1].substring(0, 4).toIntOrNull() ?: continue
            val mo = m.groupValues[1].substring(5, 7).toIntOrNull() ?: continue
            if (y in 2000..2100 && mo in 1..12) set.add(m.groupValues[1])
        }
        return set.sortedDescending()
    }

    private fun currentMonthKey(): String {
        val cal = Calendar.getInstance()
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    /** Oldest → newest so the bar chart scrolls with latest on the right (RN behavior). */
    private fun reorderTrendForNewestOnRight(trend: List<MonthTrend>): List<MonthTrend> {
        return trend.sortedBy { it.monthKey }
    }
}
