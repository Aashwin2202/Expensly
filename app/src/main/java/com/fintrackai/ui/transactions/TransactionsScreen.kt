package com.fintrackai.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.category.CategoryCatalogHelper
import kotlinx.coroutines.delay
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.MerchantCategoryApplyMode
import com.fintrackai.domain.model.Transaction
import com.fintrackai.domain.transactions.TransactionLinkConstants
import com.fintrackai.domain.transactions.TransactionLinkHelper
import com.fintrackai.domain.transactions.TransactionLinkResult
import com.fintrackai.ui.components.*
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.ExtendedColors
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onNavigateToMerchant: (String, String, String) -> Unit,
    onNavigateToCategory: (String, String, String) -> Unit,
    showTutorial: Boolean = false,
    onTutorialDone: () -> Unit = {},
    showCategoryTip: Boolean = false,
    onCategoryTipDismissed: () -> Unit = {},
    showTxDetailTip: Boolean = false,
    onTxDetailTipDismissed: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var detailLinkedPeers by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var txForCategory by remember { mutableStateOf<Transaction?>(null) }
    var pendingMerchantCategory by remember { mutableStateOf<PendingMerchantCategoryChange?>(null) }
    var pendingMerchantCountInStats by remember { mutableStateOf<PendingMerchantCountInStatsChange?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    val graphListState = rememberLazyListState()
    val txListState = rememberLazyListState()
    val merchantListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(
        state.activeTab,
        state.sortBy,
        state.filterType,
        state.filterCategory,
        state.filterMinAmount,
        state.filterMaxAmount,
        state.searchQuery,
        state.selectedMonthKey,
        state.customDateStart,
        state.customDateEnd,
        state.selectedAccountKey
    ) {
        if (state.activeTab != 0) return@LaunchedEffect
        delay(80)
        txListState.scrollToItem(0)
    }

    LaunchedEffect(state.merchantSortBy, state.merchantSearchQuery) {
        merchantListState.scrollToItem(0)
    }

    LaunchedEffect(state.monthlyTrend.size, state.showMonthlyGraph) {
        if (state.showMonthlyGraph && state.monthlyTrend.isNotEmpty()) {
            graphListState.scrollToItem((state.monthlyTrend.size - 1).coerceAtLeast(0))
        }
    }

    if (selectedTx != null) {
        val originalTx = selectedTx!!
        TransactionDetailSheet(
            transaction = originalTx,
            linkedPeers = detailLinkedPeers,
            onDismiss = { selectedTx = null },
            onSave = { tx ->
                if (tx.countInStats != originalTx.countInStats && originalTx.merchant.isNotBlank()) {
                    scope.launch {
                        val n = viewModel.countTransactionsForMerchant(originalTx.merchant)
                        when {
                            n <= 1 -> viewModel.updateTransactionCountInStats(tx, tx.countInStats, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                            else -> {
                                viewModel.saveTransaction(tx)
                                pendingMerchantCountInStats = PendingMerchantCountInStatsChange(tx, tx.countInStats, n)
                            }
                        }
                    }
                } else {
                    viewModel.saveTransaction(tx)
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
            showLongPressTip = showCategoryTip,
            onLongPressTipDismissed = onCategoryTipDismissed,
            onSelect = { cat ->
                val tx = txForCategory!!
                txForCategory = null
                if (tx.merchant.isBlank()) {
                    viewModel.updateTransactionCategory(tx, cat, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                } else {
                    scope.launch {
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
    if (showFilter) {
        FilterSheet(
            selectedType = state.filterType,
            selectedCategory = state.filterCategory,
            customCategories = state.customCategories,
            minAmount = state.filterMinAmount?.toString() ?: "",
            maxAmount = state.filterMaxAmount?.toString() ?: "",
            onApply = { t, c, min, max -> viewModel.applyFilter(t, c, min, max) },
            onReset = {
                viewModel.resetFilters()
                viewModel.clearAccountNavArgs()
            },
            onDismiss = { showFilter = false }
        )
    }
    if (showSort) {
        SortSheet(
            currentSort = state.sortBy,
            onSelect = viewModel::setSort,
            onDismiss = { showSort = false }
        )
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

    val dayDisplayFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    val monthLabel = remember(state.selectedMonthKey, state.customDateStart, state.customDateEnd) {
        fun formatDate(raw: String) = try {
            LocalDate.parse(raw).format(dayDisplayFormatter)
        } catch (_: Exception) { raw }
        when {
            state.customDateStart != null && state.customDateEnd != null ->
                if (state.customDateStart == state.customDateEnd) formatDate(state.customDateStart!!)
                else "${formatDate(state.customDateStart!!)} – ${formatDate(state.customDateEnd!!)}"
            state.selectedMonthKey != null -> TransactionsGraphHelper.formatMonthKeyForDisplay(state.selectedMonthKey!!)
            else -> ""
        }
    }

    val drillDownMonthKey = remember(state.selectedMonthKey, state.customDateStart, state.customDateEnd) {
        TransactionsGraphHelper.drillDownMonthKeyFromPeriod(
            state.customDateStart,
            state.customDateEnd,
            state.selectedMonthKey
        )
    }

    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            state.headerAccountTitle ?: "All accounts",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            maxLines = 1
                        )
                        val graphToggleLocked = state.customDateStart != null
                        if (graphToggleLocked) {
                            Text(
                                monthLabel,
                                fontSize = 14.sp,
                                color = ext.textSecondary
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.setShowMonthlyGraph(!state.showMonthlyGraph) }
                            ) {
                                Text(monthLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Icon(
                                    if (state.showMonthlyGraph) Icons.Default.KeyboardArrowUp
                                    else Icons.Default.KeyboardArrowDown,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangeDialog = true }) {
                        Icon(Icons.Default.CalendarMonth, "Date range")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row — minimal, clean
            TabRow(
                selectedTabIndex = state.activeTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {
                    HorizontalDivider(color = ext.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            ) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = {
                        Text(
                            "Transactions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (state.activeTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    enabled = !state.linkSelectionActive,
                    text = {
                        Text(
                            "Categories",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (state.activeTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
                Tab(
                    selected = state.activeTab == 2,
                    onClick = { viewModel.setActiveTab(2) },
                    enabled = !state.linkSelectionActive,
                    text = {
                        Text(
                            "Merchants",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (state.activeTab == 2) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
            }

            // Selection / link banners
            if (state.isInSelectionMode) {
                val selectedTxs = state.transactions.filter { it.id in state.selectedTransactionIds }
                val anyLinked = selectedTxs.any { it.linkGroupId != null }
                val onMergeClick: () -> Unit = {
                    viewModel.mergeSelected { result: TransactionLinkResult ->
                        val msg = TransactionLinkUiMessages.message(result)
                        if (msg != null) scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Text(
                            "${state.selectedTransactionIds.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row {
                            if (anyLinked) {
                                TextButton(onClick = { viewModel.unmergeSelected() }) {
                                    Text("Unmerge", color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                TextButton(onClick = onMergeClick) {
                                    Text("Merge", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            TextButton(onClick = { viewModel.deleteSelected() }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            } else if (state.linkSelectionActive) {
                val anchor = state.linkSelectionAnchor
                val anchorOrigType = anchor?.let { TransactionLinkHelper.anchorOriginalType(it) }
                val bannerBody = if (anchorOrigType.equals("debit", ignoreCase = true)) {
                    TransactionLinkConstants.LINK_SELECTION_BANNER_DEBIT
                } else {
                    TransactionLinkConstants.LINK_SELECTION_BANNER_CREDIT
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bannerBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "\u201c${anchor?.merchant?.trim()?.ifBlank { "—" }}\u201d",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(
                            onClick = {
                                if (viewModel.cancelLinkSelection()) onBack()
                            }
                        ) {
                            Text(TransactionLinkConstants.CANCEL_COMBINE)
                        }
                    }
                }
            }

            if (state.showMonthlyGraph && state.customDateStart == null) {
                MonthlyExpenseBarChart(
                    months = state.monthlyTrend,
                    selectedMonthKey = state.selectedMonthKey,
                    listState = graphListState,
                    ext = ext,
                    onSelectMonth = viewModel::selectMonth,
                    forcePositive = state.isCard
                )
            }

            when (state.activeTab) {
                0 -> {
                    // Search bar — clean, borderless feel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search…", color = ext.textSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = RoundedCornerShape(AppShape.medium),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = ext.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = ext.border.copy(alpha = 0.5f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Default.FilterAlt, "Filter", tint = ext.textSecondary)
                        }
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.Default.SwapVert, "Sort", tint = ext.textSecondary)
                        }
                    }
                    FilterChipRow(state, state.customCategories, viewModel)
                    if (state.customDateStart != null && !state.loading) {
                        DateRangeTotalRow(
                            debitTotal = state.filteredDebitTotal,
                            creditTotal = state.filteredCreditTotal,
                            formatter = formatter,
                            ext = ext
                        )
                    }
                    if (state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        LazyColumn(
                            state = txListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = Spacing.xxl)
                        ) {
                            items(state.transactions, key = { it.id }) { tx ->
                                val isSelected = tx.id in state.selectedTransactionIds
                                TransactionCard(
                                    transaction = tx,
                                    customCategories = state.customCategories,
                                    isSelected = isSelected,
                                    onClick = {
                                        when {
                                            state.isInSelectionMode -> viewModel.toggleSelection(tx.id)
                                            state.linkSelectionActive -> {
                                                val anchor = state.linkSelectionAnchor
                                                if (anchor != null &&
                                                    tx.id != anchor.id &&
                                                    TransactionLinkHelper.canOfferLink(tx) &&
                                                    tx.type.equals(
                                                        TransactionLinkHelper.oppositeType(TransactionLinkHelper.anchorOriginalType(anchor)),
                                                        ignoreCase = true
                                                    )
                                                ) {
                                                    viewModel.linkPairFromSelection(anchor, tx) { result, shouldPop ->
                                                        TransactionLinkUiMessages.message(result)?.let { msg ->
                                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                                        }
                                                        if (shouldPop) onBack()
                                                    }
                                                } else if (anchor != null) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(TransactionLinkConstants.ERR_INVALID_COMBINE_TAP)
                                                    }
                                                }
                                            }
                                            else -> selectedTx = tx
                                        }
                                    },
                                    onCategoryClick = if (state.linkSelectionActive || state.isInSelectionMode) {
                                        null
                                    } else {
                                        { txForCategory = tx }
                                    },
                                    onLongClick = if (state.linkSelectionActive) {
                                        null
                                    } else {
                                        { viewModel.toggleSelection(tx.id) }
                                    }
                                )
                                HorizontalDivider(
                                    Modifier.padding(horizontal = Spacing.lg),
                                    color = ext.border.copy(alpha = 0.4f),
                                    thickness = 0.5.dp
                                )
                            }
                            if (state.transactions.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.huge),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No transactions match your filters.",
                                            color = ext.textSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    } else {
                        val needSelection = state.selectedMonthKey == null &&
                            state.customDateStart == null &&
                            state.selectedAccountKey == null
                        if (needSelection) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Select a month from the chart or pick a date range.",
                                    color = ext.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            val topCategoryIsOthers = state.displayCategoryRows
                                .firstOrNull()?.category?.lowercase() == "others"
                            val categoryTotal = remember(state.displayCategoryRows) {
                                state.displayCategoryRows.filter { !it.category.equals("investment", ignoreCase = true) }.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0
                            }
                            LazyColumn(
                                contentPadding = PaddingValues(Spacing.lg),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                if (topCategoryIsOthers) {
                                    item {
                                        FinCard(cornerRadius = AppShape.large, contentPadding = Spacing.lg) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                                Text(
                                                    "Assign categories to get smarter insights and better tracking.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = ext.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                                if (state.displayCategoryStats.isNotEmpty() && state.displayCategoryStats.sumOf { it.amount } > 0) {
                                    item {
                                        PieChartCard(stats = state.displayCategoryStats, customCategories = state.customCategories)
                                    }
                                } else {
                                    item {
                                        Text("No category data for this period.", color = ext.textSecondary)
                                    }
                                }
                                items(state.displayCategoryRows, key = { "cat_${it.category}" }) { row ->
                                    val pct = (row.amount / categoryTotal).coerceIn(0.0, 1.0)
                                    val colorHex = CategoryCatalogHelper.categoryColor(row.category, state.customCategories)
                                    val barColor = runCatching {
                                        val cleaned = colorHex.trimStart('#')
                                        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
                                        Color(argb.toLong(16).toInt())
                                    }.getOrElse { Color(0xFF3B82F6.toInt()) }
                                    val isInvestment = row.category.equals("investment", ignoreCase = true)
                                    val contentAlpha = if (isInvestment) 0.45f else 1f
                                    FinCard(
                                        cornerRadius = AppShape.medium,
                                        onClick = { onNavigateToCategory(row.category, drillDownMonthKey, state.filterType ?: "") }
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                                ) {
                                                    Text(
                                                        CategoryCatalogHelper.categoryIcon(row.category, state.customCategories),
                                                        fontSize = 18.sp
                                                    )
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                                        ) {
                                                            Text(
                                                                CategoryCatalogHelper.categoryLabel(row.category, state.customCategories),
                                                                color = ext.text.copy(alpha = contentAlpha),
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                        Spacer(Modifier.height(Spacing.xxs))
                                                        Text(
                                                            "${row.transactionCount} transactions",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = ext.textSecondary.copy(alpha = contentAlpha)
                                                        )
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        "₹${formatter.format(row.amount.toLong())}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = ext.text.copy(alpha = contentAlpha)
                                                    )
                                                    Text(
                                                        if (isInvestment) "-" else "${(pct * 100).toInt()}%",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = ext.textSecondary.copy(alpha = contentAlpha)
                                                    )
                                                }
                                            }
                                            if (!isInvestment) {
                                            Spacer(Modifier.height(Spacing.sm))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(ext.border.copy(alpha = 0.3f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(pct.toFloat())
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(barColor.copy(alpha = contentAlpha))
                                                )
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    } else {
                        val needSelection = state.selectedMonthKey == null &&
                            state.customDateStart == null &&
                            state.selectedAccountKey == null
                        if (needSelection) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Select a month or date range.",
                                    color = ext.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            var showMerchantSortMenu by remember { mutableStateOf(false) }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = state.merchantSearchQuery,
                                        onValueChange = viewModel::setMerchantSearchQuery,
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("Search merchants…", color = ext.textSecondary) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                        shape = RoundedCornerShape(AppShape.medium),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = ext.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = ext.border.copy(alpha = 0.5f),
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Box {
                                        IconButton(onClick = { showMerchantSortMenu = true }) {
                                            Icon(
                                                Icons.Default.SwapVert,
                                                contentDescription = "Sort merchants",
                                                tint = if (state.merchantSortBy == "frequency")
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    ext.textSecondary
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMerchantSortMenu,
                                            onDismissRequest = { showMerchantSortMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Sort by Amount",
                                                        fontWeight = if (state.merchantSortBy == "amount") FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (state.merchantSortBy == "amount") MaterialTheme.colorScheme.primary else ext.text
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setMerchantSortBy("amount")
                                                    showMerchantSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Sort by Frequency",
                                                        fontWeight = if (state.merchantSortBy == "frequency") FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (state.merchantSortBy == "frequency") MaterialTheme.colorScheme.primary else ext.text
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setMerchantSortBy("frequency")
                                                    showMerchantSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                LazyColumn(
                                    state = merchantListState,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    items(state.displayMerchants, key = { it.merchant }) { ms ->
                                        val contentAlpha = if (ms.excluded) 0.45f else 1f
                                        FinCard(
                                            cornerRadius = AppShape.medium,
                                            onClick = { onNavigateToMerchant(ms.merchant, drillDownMonthKey, state.filterType ?: "") }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        ms.merchant,
                                                        color = ext.text.copy(alpha = contentAlpha),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(Modifier.height(Spacing.xxs))
                                                    Text(
                                                        "${ms.transactionCount} transactions",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = ext.textSecondary.copy(alpha = contentAlpha)
                                                    )
                                                }
                                                Text(
                                                    "₹${formatter.format(ms.amount.toLong())}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ext.textSecondary.copy(alpha = contentAlpha)
                                                )
                                            }
                                        }
                                    }
                                    if (state.displayMerchants.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(Spacing.xxl),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    if (state.merchantSearchQuery.isBlank()) "No merchants for this period."
                                                    else "No merchants match your search.",
                                                    color = ext.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTutorial) {
        TransactionsTutorialOverlay(onDone = onTutorialDone)
    }
    } // end Box
}

@Composable
private fun DateRangeTotalRow(
    debitTotal: Double,
    creditTotal: Double,
    formatter: java.text.NumberFormat,
    ext: ExtendedColors,
) {
    val hasDebit = debitTotal > 0
    val hasCredit = creditTotal > 0
    if (!hasDebit && !hasCredit) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = if (hasDebit && hasCredit) Arrangement.SpaceBetween else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasDebit) {
            Column {
                Text(
                    "Spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary
                )
                Text(
                    "₹${formatter.format(debitTotal.toLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        if (hasCredit) {
            Column(horizontalAlignment = if (hasDebit) Alignment.End else Alignment.Start) {
                Text(
                    "Received",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary
                )
                Text(
                    "₹${formatter.format(creditTotal.toLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    state: TransactionsUiState,
    customCategories: List<CustomCategory>,
    viewModel: TransactionsViewModel
) {
    val chips = remember(
        state.filterCategory,
        state.filterType,
        state.filterMinAmount,
        state.filterMaxAmount,
        customCategories
    ) {
        buildList {
            state.filterCategory?.let { id ->
                add("category" to CategoryCatalogHelper.categoryLabel(id, customCategories))
            }
            state.filterType?.let { add("type" to if (it == "debit") "Debit" else "Credit") }
            state.filterMinAmount?.let { add("min" to "Min ₹${it.toLong()}") }
            state.filterMaxAmount?.let { add("max" to "Max ₹${it.toLong()}") }
        }
    }
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        chips.forEach { (key, label) ->
            AssistChip(
                onClick = {
                    when (key) {
                        "category" -> viewModel.applyFilter(state.filterType, null, state.filterMinAmount, state.filterMaxAmount)
                        "type" -> viewModel.applyFilter(null, state.filterCategory, state.filterMinAmount, state.filterMaxAmount)
                        "min" -> viewModel.applyFilter(state.filterType, state.filterCategory, null, state.filterMaxAmount)
                        "max" -> viewModel.applyFilter(state.filterType, state.filterCategory, state.filterMinAmount, null)
                    }
                },
                label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                },
                shape = RoundedCornerShape(AppShape.small)
            )
        }
    }
}

private enum class QuickRange { NONE, LAST_3, LAST_6, ALL_TIME }

// Format YYYY-MM-DD → "12 Jan 2025" for display
private fun ymdToReadable(ymd: String): String = try {
    val parts = ymd.split("-")
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    "${parts[2].trimStart('0').ifEmpty { "0" }} ${months[parts[1].toInt() - 1]} ${parts[0]}"
} catch (_: Exception) { ymd }

@Composable
internal fun DateRangePickerDialog(
    initialStart: String?,
    initialEnd: String?,
    dataDateMin: String?,
    dataDateMax: String?,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
    onClear: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Internal state in YYYY-MM-DD
    var start by remember {
        mutableStateOf(initialStart ?: run {
            val c = java.util.Calendar.getInstance()
            String.format("%04d-%02d-01", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
        })
    }
    var end by remember {
        mutableStateOf(initialEnd ?: run {
            val c = java.util.Calendar.getInstance()
            String.format(
                "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH)
            )
        })
    }
    var activeQuick by remember { mutableStateOf(QuickRange.NONE) }
    val allTimeEnabled = dataDateMin != null && dataDateMax != null

    fun openStartPicker() {
        val parts = start.split("-")
        val cal = java.util.Calendar.getInstance()
        if (parts.size == 3) {
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                start = String.format("%04d-%02d-%02d", y, m + 1, d)
                activeQuick = QuickRange.NONE
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openEndPicker() {
        val parts = end.split("-")
        val cal = java.util.Calendar.getInstance()
        if (parts.size == 3) {
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                end = String.format("%04d-%02d-%02d", y, m + 1, d)
                activeQuick = QuickRange.NONE
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom date range") },
        shape = RoundedCornerShape(AppShape.extraLarge),
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    val chipModifier = Modifier.weight(1f)
                    FilterChip(
                        selected = activeQuick == QuickRange.LAST_3,
                        onClick = {
                            val (a, b) = DateRangePickerHelper.lastNCalendarMonthsThroughToday(3)
                            start = a; end = b
                            activeQuick = QuickRange.LAST_3
                        },
                        label = { Text(DateRangePickerConstants.LAST_3_MONTHS, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(AppShape.small),
                        modifier = chipModifier
                    )
                    FilterChip(
                        selected = activeQuick == QuickRange.LAST_6,
                        onClick = {
                            val (a, b) = DateRangePickerHelper.lastNCalendarMonthsThroughToday(6)
                            start = a; end = b
                            activeQuick = QuickRange.LAST_6
                        },
                        label = { Text(DateRangePickerConstants.LAST_6_MONTHS, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(AppShape.small),
                        modifier = chipModifier
                    )
                    FilterChip(
                        selected = activeQuick == QuickRange.ALL_TIME,
                        enabled = allTimeEnabled,
                        onClick = {
                            if (allTimeEnabled) {
                                start = dataDateMin!!; end = dataDateMax!!
                                activeQuick = QuickRange.ALL_TIME
                            }
                        },
                        label = { Text(DateRangePickerConstants.ALL_TIME, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(AppShape.small),
                        modifier = chipModifier
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                InputField(
                    value = ymdToReadable(start),
                    onValueChange = {},
                    label = "Start date",
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = ::openStartPicker) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick start date")
                        }
                    }
                )
                Spacer(Modifier.height(Spacing.sm))
                InputField(
                    value = ymdToReadable(end),
                    onValueChange = {},
                    label = "End date",
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = ::openEndPicker) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick end date")
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (start.matches(Regex("""\d{4}-\d{2}-\d{2}""")) && end.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    onApply(start, end)
                }
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
