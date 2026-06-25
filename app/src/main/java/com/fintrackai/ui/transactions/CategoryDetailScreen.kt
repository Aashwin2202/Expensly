package com.fintrackai.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.data.repository.WrongSmsRepository
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.MerchantCategoryApplyMode
import com.fintrackai.domain.model.MonthTrend
import com.fintrackai.domain.model.Transaction
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.components.MerchantCategoryChangeDialog
import com.fintrackai.ui.components.MerchantCountInStatsChangeDialog
import com.fintrackai.ui.components.MonthlyExpenseBarChart
import com.fintrackai.ui.components.SortSheet
import com.fintrackai.ui.components.TransactionCard
import com.fintrackai.ui.components.TransactionDeleteConfirmDialog
import com.fintrackai.ui.components.TransactionDetailSheet
import com.fintrackai.ui.components.TransactionLongPressSheet
import com.fintrackai.ui.theme.LocalExtendedColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

data class CategoryDetailState(
    val category: String = "",
    val scopeMonthKey: String = "",
    val filterType: String = "",
    val customDateStart: String? = null,
    val customDateEnd: String? = null,
    val dataDateMin: String? = null,
    val dataDateMax: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val trend: List<MonthTrend> = emptyList(),
    val totalSpent: Double = 0.0,
    val customCategories: List<CustomCategory> = emptyList(),
    val knownAccountOptions: List<String> = emptyList(),
    val loading: Boolean = true,
    val initialized: Boolean = false
)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val wrongSmsRepo: WrongSmsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryDetailState())
    val uiState: StateFlow<CategoryDetailState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getCustomCategoriesFlow().collect { custom ->
                _uiState.value = _uiState.value.copy(customCategories = custom)
            }
        }
        viewModelScope.launch {
            repo.getAllTransactionsFlow().collect { list ->
                val opts = list.mapNotNull { tx -> tx.accounts.trim().takeIf { it.isNotEmpty() } }.distinct().sorted()
                _uiState.value = _uiState.value.copy(knownAccountOptions = opts)
            }
        }
    }

    suspend fun countTransactionsForMerchant(merchant: String): Int {
        val m = merchant.trim()
        if (m.isBlank()) return 0
        return repo.getMerchantTransactionCount(m)
    }

    fun load(category: String, monthScope: String = "") {
        val isFirstLoad = !_uiState.value.initialized
        _uiState.value = _uiState.value.copy(category = category, loading = isFirstLoad)
        viewModelScope.launch {
            val txnsAll = repo.getTransactionsByCategory(category)
            val cur = _uiState.value
            val isCredit = cur.filterType == "credit"
            val trend = if (isCredit) {
                buildCreditTrendFromTransactions(txnsAll)
            } else {
                buildDebitTrendFromTransactions(txnsAll)
            }
            val dateRange = repo.getDateRange()
            val dataMin = dateRange.minDate.takeIf { it != "N/A" }
            val dataMax = dateRange.maxDate.takeIf { it != "N/A" }
            val dateFiltered = when {
                cur.customDateStart != null && cur.customDateEnd != null ->
                    txnsAll.filter { it.date >= cur.customDateStart!! && it.date <= cur.customDateEnd!! }
                monthScope.isNotBlank() -> txnsAll.filter { it.date.startsWith(monthScope) }
                else -> txnsAll
            }
            val filtered = if (isCredit) {
                dateFiltered.filter { it.type == "credit" }
            } else {
                dateFiltered.filter { it.type == "debit" }
            }
            val total = if (isCredit) {
                filtered.filter { it.countInStats }.sumOf { it.amount }
            } else {
                filtered.filter { it.type == "debit" && (it.countInStats || it.category.equals("investment", ignoreCase = true)) }.sumOf { it.amount }
            }
            _uiState.value = CategoryDetailState(
                category = category,
                scopeMonthKey = monthScope,
                filterType = cur.filterType,
                customDateStart = cur.customDateStart,
                customDateEnd = cur.customDateEnd,
                dataDateMin = dataMin,
                dataDateMax = dataMax,
                transactions = filtered,
                trend = trend,
                totalSpent = total,
                customCategories = cur.customCategories,
                knownAccountOptions = cur.knownAccountOptions,
                loading = false,
                initialized = true
            )
        }
    }

    private fun buildDebitTrendFromTransactions(txns: List<Transaction>): List<MonthTrend> {
        val monthMap = mutableMapOf<String, Double>()
        for (t in txns) {
            if (t.type != "debit" || t.amount <= 0) continue
            val mk = t.date.take(7)
            monthMap[mk] = (monthMap[mk] ?: 0.0) + t.amount
        }
        if (monthMap.isEmpty()) return emptyList()
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return monthMap.entries
            .map { (mk, amt) ->
                val parts = mk.split("-")
                val label = try { "${months[parts[1].toInt() - 1]} '${parts[0].takeLast(2)}" } catch (_: Exception) { mk }
                MonthTrend(month = label, monthKey = mk, amount = kotlin.math.round(amt).toDouble())
            }
            .sortedBy { it.monthKey }
    }

    private fun buildCreditTrendFromTransactions(txns: List<Transaction>): List<MonthTrend> {
        val monthMap = mutableMapOf<String, Double>()
        for (t in txns) {
            if (t.type != "credit" || t.amount <= 0) continue
            val mk = t.date.take(7)
            monthMap[mk] = (monthMap[mk] ?: 0.0) + t.amount
        }
        if (monthMap.isEmpty()) return emptyList()
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return monthMap.entries
            .map { (mk, amt) ->
                val parts = mk.split("-")
                val label = try { "${months[parts[1].toInt() - 1]} '${parts[0].takeLast(2)}" } catch (_: Exception) { mk }
                MonthTrend(month = label, monthKey = mk, amount = kotlin.math.round(amt).toDouble())
            }
            .sortedBy { it.monthKey }
    }

    fun initFilterType(filterType: String) {
        if (_uiState.value.filterType != filterType) {
            _uiState.value = _uiState.value.copy(filterType = filterType)
        }
    }

    fun setCustomDateRange(start: String, end: String) {
        _uiState.value = _uiState.value.copy(customDateStart = start, customDateEnd = end, scopeMonthKey = "")
        load(_uiState.value.category, "")
    }

    fun clearCustomDateRange() {
        _uiState.value = _uiState.value.copy(customDateStart = null, customDateEnd = null)
        load(_uiState.value.category, _uiState.value.scopeMonthKey)
    }

    fun updateTransactionCategory(tx: Transaction, category: String, mode: MerchantCategoryApplyMode) {
        viewModelScope.launch {
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY -> {
                    repo.updateTransaction(tx.copy(category = category))
                    if (tx.merchant.isNotBlank()) {
                        repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    }
                }
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT -> {
                    repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    repo.updateMerchantTransactionsCategory(tx.merchant, category)
                }
            }
            load(_uiState.value.category, _uiState.value.scopeMonthKey)
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
            load(_uiState.value.category, _uiState.value.scopeMonthKey)
        }
    }

    fun saveTransaction(tx: Transaction, categoryId: String) {
        viewModelScope.launch {
            repo.updateTransaction(tx)
            if (tx.merchant.isNotBlank()) {
                repo.saveMerchantCategoryMapping(tx.merchant, tx.category)
            }
            load(categoryId, _uiState.value.scopeMonthKey)
        }
    }

    fun renameMerchant(originalName: String, newName: String, applyToAll: Boolean) {
        viewModelScope.launch {
            if (applyToAll) {
                repo.bulkRenameMerchant(originalName, newName)
            } else {
                repo.saveMerchantNameMapping(originalName, newName)
            }
        }
    }

    fun saveCustomCategory(name: String, emoji: String, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.addCustomCategoryFromInput(name, emoji) ?: return@launch
            onSaved(id)
        }
    }

    fun deleteCustomCategory(id: String) {
        viewModelScope.launch { repo.deleteCustomCategoryAndRecategorize(id) }
    }

    fun editCustomCategory(id: String, name: String, emoji: String) {
        viewModelScope.launch { repo.editCustomCategory(id, name, emoji) }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repo.deleteTransactionById(id)
            load(_uiState.value.category, _uiState.value.scopeMonthKey)
        }
    }

    suspend fun getSecondariesForGroup(groupId: String): List<Transaction> = repo.getSecondariesForGroup(groupId)

    suspend fun getPrimaryForGroup(groupId: String): Transaction? = repo.getPrimaryForGroup(groupId)

    fun unsplitPair(visibleRowId: String) {
        viewModelScope.launch {
            repo.unlinkTransactionPair(visibleRowId)
            load(_uiState.value.category, _uiState.value.scopeMonthKey)
        }
    }

    fun reportWrongDetection(tx: Transaction, reason: String, comments: String) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryId: String,
    monthKey: String = "",
    filterType: String = "",
    onBack: () -> Unit,
    onOpenTransactionsToCombine: (anchorTransactionId: String) -> Unit = {},
    showTxDetailTip: Boolean = false,
    onTxDetailTipDismissed: () -> Unit = {},
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var detailLinkedPeers by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var txForCategory by remember { mutableStateOf<Transaction?>(null) }
    var pendingMerchantCategory by remember { mutableStateOf<PendingMerchantCategoryChange?>(null) }
    var pendingMerchantCountInStats by remember { mutableStateOf<PendingMerchantCountInStatsChange?>(null) }
    val categoryChoiceScope = rememberCoroutineScope()
    var actionTx by remember { mutableStateOf<Transaction?>(null) }
    var showMonthlyGraph by remember(categoryId) { mutableStateOf(true) }
    var sortBy by remember { mutableStateOf("date_desc") }
    var showSort by remember { mutableStateOf(false) }
    val txListState = rememberLazyListState()

    LaunchedEffect(sortBy) {
        txListState.scrollToItem(0)
    }

    LaunchedEffect(selectedTx?.id, selectedTx?.linkGroupId) {
        detailLinkedPeers = emptyList()
        val st = selectedTx ?: return@LaunchedEffect
        val groupId = st.linkGroupId ?: return@LaunchedEffect
        if (st.linkSuppressed) {
            viewModel.getPrimaryForGroup(groupId)?.let { detailLinkedPeers = listOf(it) }
        } else {
            detailLinkedPeers = viewModel.getSecondariesForGroup(groupId)
        }
    }

    var showDateRangeDialog by remember { mutableStateOf(false) }

    val chartListState = rememberLazyListState()
    val chartSelectedMonth = state.scopeMonthKey.takeIf { it.isNotBlank() }
    val monthToggleLabel = when {
        state.customDateStart != null && state.customDateEnd != null -> {
            fun fmt(d: String) = try {
                val p = d.split("-")
                val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                "${p[2].trimStart('0').ifEmpty{"0"}} ${months[p[1].toInt()-1]} ${p[0]}"
            } catch (_: Exception) { d }
            if (state.customDateStart == state.customDateEnd) fmt(state.customDateStart!!)
            else "${fmt(state.customDateStart!!)} – ${fmt(state.customDateEnd!!)}"
        }
        state.scopeMonthKey.isNotBlank() -> TransactionsGraphHelper.formatMonthKeyForDisplay(state.scopeMonthKey)
        else -> DetailScreensConstants.ALL_TIME_MONTH_LABEL
    }

    LaunchedEffect(state.trend.size, showMonthlyGraph) {
        if (showMonthlyGraph && state.trend.isNotEmpty()) {
            chartListState.scrollToItem((state.trend.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(categoryId, monthKey, filterType) {
        viewModel.initFilterType(filterType)
        viewModel.load(categoryId, monthKey)
    }

    if (showDateRangeDialog) {
        DateRangePickerDialog(
            initialStart = state.customDateStart,
            initialEnd = state.customDateEnd,
            dataDateMin = state.dataDateMin,
            dataDateMax = state.dataDateMax,
            onDismiss = { showDateRangeDialog = false },
            onApply = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showDateRangeDialog = false
            },
            onClear = {
                viewModel.clearCustomDateRange()
                showDateRangeDialog = false
            }
        )
    }

    if (selectedTx != null) {
        val originalTx = selectedTx!!
        TransactionDetailSheet(
            transaction = originalTx,
            linkedPeers = detailLinkedPeers,
            onDismiss = { selectedTx = null },
            onSave = { tx ->
                if (tx.countInStats != originalTx.countInStats && originalTx.merchant.isNotBlank()) {
                    categoryChoiceScope.launch {
                        val n = viewModel.countTransactionsForMerchant(originalTx.merchant)
                        when {
                            n <= 1 -> viewModel.updateTransactionCountInStats(tx, tx.countInStats, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                            else -> {
                                viewModel.saveTransaction(tx, categoryId)
                                pendingMerchantCountInStats = PendingMerchantCountInStatsChange(tx, tx.countInStats, n)
                            }
                        }
                    }
                } else {
                    viewModel.saveTransaction(tx, categoryId)
                }
            },
            onRenameMerchant = { orig, new, all -> viewModel.renameMerchant(orig, new, all) },
            onDelete = { txToDelete = selectedTx; selectedTx = null },
            onReportWrongDetection = { reason, comments ->
                selectedTx?.let { viewModel.reportWrongDetection(it, reason, comments) }
            },
            onUnsplit = selectedTx?.takeIf { it.linkGroupId != null }?.let { st ->
                { viewModel.unsplitPair(st.id) }
            },
            showTip = showTxDetailTip,
            onTipDismissed = onTxDetailTipDismissed
        )
    }
    actionTx?.let { tx ->
        TransactionLongPressSheet(
            transaction = tx,
            onDismiss = { actionTx = null },
            onDelete = { txToDelete = tx },
            onLink = { onOpenTransactionsToCombine(tx.id) },
            onUnsplit = { viewModel.unsplitPair(tx.id) }
        )
    }
    txToDelete?.let { pendingDelete ->
        TransactionDeleteConfirmDialog(
            merchantLabel = pendingDelete.merchant.ifBlank { "—" },
            onDismiss = { txToDelete = null },
            onConfirm = {
                viewModel.deleteTransaction(pendingDelete.id)
                txToDelete = null
            }
        )
    }
    pendingMerchantCategory?.let { pending ->
        MerchantCategoryChangeDialog(
            merchant = pending.transaction.merchant.trim().ifBlank { "—" },
            categoryLabel = CategoryCatalogHelper.categoryLabel(pending.categoryId, state.customCategories),
            merchantOccurrenceCount = pending.merchantOccurrenceCount,
            onDismiss = { pendingMerchantCategory = null },
            onThisTransactionOnly = {
                viewModel.updateTransactionCategory(pending.transaction, pending.categoryId, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                pendingMerchantCategory = null
            },
            onAllForMerchant = {
                viewModel.updateTransactionCategory(pending.transaction, pending.categoryId, MerchantCategoryApplyMode.ALL_FOR_MERCHANT)
                pendingMerchantCategory = null
            }
        )
    }
    pendingMerchantCountInStats?.let { pending ->
        MerchantCountInStatsChangeDialog(
            merchant = pending.transaction.merchant.trim().ifBlank { "—" },
            countInStats = pending.countInStats,
            txType = pending.transaction.type,
            merchantOccurrenceCount = pending.merchantOccurrenceCount,
            onDismiss = { pendingMerchantCountInStats = null },
            onThisTransactionOnly = { pendingMerchantCountInStats = null },
            onAllForMerchant = {
                viewModel.updateTransactionCountInStats(pending.transaction, pending.countInStats, MerchantCategoryApplyMode.ALL_FOR_MERCHANT)
                pendingMerchantCountInStats = null
            }
        )
    }
    if (txForCategory != null) {
        CategoryPickerSheet(
            selectedCategory = txForCategory!!.category.lowercase(),
            customCategories = state.customCategories,
            onSaveCustomCategory = viewModel::saveCustomCategory,
            onDeleteCustomCategory = viewModel::deleteCustomCategory,
            onEditCustomCategory = viewModel::editCustomCategory,
            onSelect = { cat ->
                val tx = txForCategory!!
                txForCategory = null
                if (tx.merchant.isBlank()) {
                    viewModel.updateTransactionCategory(tx, cat, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                } else {
                    categoryChoiceScope.launch {
                        val n = viewModel.countTransactionsForMerchant(tx.merchant)
                        when {
                            n <= 0 -> viewModel.updateTransactionCategory(tx, cat, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                            n <= 1 -> viewModel.updateTransactionCategory(tx, cat, MerchantCategoryApplyMode.ALL_FOR_MERCHANT)
                            else -> pendingMerchantCategory = PendingMerchantCategoryChange(tx, cat, n)
                        }
                    }
                }
            },
            onDismiss = { txForCategory = null }
        )
    }
    if (showSort) {
        SortSheet(
            currentSort = sortBy,
            onSelect = { sortBy = it; showSort = false },
            onDismiss = { showSort = false }
        )
    }

    val sortedTransactions = remember(state.transactions, sortBy) {
        when (sortBy) {
            "date_asc" -> state.transactions.sortedBy { it.date + it.time }
            "amount_desc" -> state.transactions.sortedByDescending { it.amount }
            "amount_asc" -> state.transactions.sortedBy { it.amount }
            "merchant_asc" -> state.transactions.sortedBy { it.merchant.lowercase() }
            else -> state.transactions.sortedByDescending { it.date + it.time }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            CategoryCatalogHelper.categoryLabel(categoryId, state.customCategories),
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        )
                        if (state.trend.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showMonthlyGraph = !showMonthlyGraph }
                            ) {
                                Text(monthToggleLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Icon(
                                    if (showMonthlyGraph) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showDateRangeDialog = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Date range")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Fixed header: total spent row + chart
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val totalLabel = if (state.filterType == "credit") "Total Credit" else "Total Spent"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$totalLabel: ₹${formatter.format(state.totalSpent.toLong())}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ext.text)
                            Text("${state.transactions.size} transactions", color = ext.textSecondary)
                        }
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Sort", tint = ext.textSecondary)
                        }
                    }
                    if (showMonthlyGraph && state.trend.isNotEmpty()) {
                        MonthlyExpenseBarChart(
                            months = state.trend,
                            selectedMonthKey = chartSelectedMonth,
                            listState = chartListState,
                            ext = ext,
                            onSelectMonth = { viewModel.load(categoryId, it) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ext.border)
                }
                // Scrollable transaction list
                LazyColumn(
                    state = txListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedTransactions) { tx ->
                        TransactionCard(
                            transaction = tx,
                            customCategories = state.customCategories,
                            onClick = { selectedTx = tx },
                            onCategoryClick = { txForCategory = tx },
                            onLongClick = { actionTx = tx }
                        )
                    }
                }
            }
        }
    }
}
