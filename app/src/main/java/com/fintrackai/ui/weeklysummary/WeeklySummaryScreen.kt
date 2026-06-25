package com.fintrackai.ui.weeklysummary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.format.AmountCompactFormatHelper
import com.fintrackai.domain.model.CategoryStat
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.DailyExpenseDay
import com.fintrackai.domain.model.MerchantCategoryApplyMode
import com.fintrackai.domain.model.Transaction
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.components.FinCard
import com.fintrackai.ui.components.MerchantCategoryChangeDialog
import com.fintrackai.ui.components.MerchantCountInStatsChangeDialog
import com.fintrackai.ui.components.PieChartCard
import com.fintrackai.ui.components.SectionHeader
import com.fintrackai.ui.components.TransactionCard
import com.fintrackai.ui.components.TransactionDetailSheet
import com.fintrackai.ui.home.HomeDailyExpenseBarChart
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import com.fintrackai.ui.transactions.PendingMerchantCategoryChange
import com.fintrackai.ui.transactions.PendingMerchantCountInStatsChange
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklySummaryScreen(
    weekStart: String? = null,
    onBack: () -> Unit,
    onNavigateToTransactions: (dateStart: String, dateEnd: String) -> Unit = { _, _ -> },
    onNavigateToTransactionsFiltered: (dateStart: String, dateEnd: String, category: String) -> Unit = { _, _, _ -> },
    onNavigateToMerchant: (name: String, monthKey: String) -> Unit = { _, _ -> },
    showCategoryTip: Boolean = false,
    onCategoryTipDismissed: () -> Unit = {},
    showTxDetailTip: Boolean = false,
    onTxDetailTipDismissed: () -> Unit = {},
    viewModel: WeeklySummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var txForCategory by remember { mutableStateOf<Transaction?>(null) }
    var pendingMerchantCategory by remember { mutableStateOf<PendingMerchantCategoryChange?>(null) }
    var pendingMerchantCountInStats by remember { mutableStateOf<PendingMerchantCountInStatsChange?>(null) }
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    val categoryScope = rememberCoroutineScope()

    LaunchedEffect(weekStart) {
        viewModel.load(weekStart)
    }

    TransactionDetailSheet(
        transaction = selectedTx,
        onDismiss = { selectedTx = null },
        onSave = { tx ->
            val original = selectedTx
            if (original != null && tx.countInStats != original.countInStats && original.merchant.isNotBlank()) {
                categoryScope.launch {
                    val n = viewModel.countTransactionsForMerchant(original.merchant)
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
        showTip = showTxDetailTip,
        onTipDismissed = onTxDetailTipDismissed
    )

    val pending = pendingMerchantCategory
    if (pending != null) {
        MerchantCategoryChangeDialog(
            merchant = pending.transaction.merchant,
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
    pendingMerchantCountInStats?.let { pcs ->
        MerchantCountInStatsChangeDialog(
            merchant = pcs.transaction.merchant.trim().ifBlank { "—" },
            countInStats = pcs.countInStats,
            txType = pcs.transaction.type,
            merchantOccurrenceCount = pcs.merchantOccurrenceCount,
            onDismiss = { pendingMerchantCountInStats = null },
            onThisTransactionOnly = { pendingMerchantCountInStats = null },
            onAllForMerchant = {
                viewModel.updateTransactionCountInStats(pcs.transaction, pcs.countInStats, MerchantCategoryApplyMode.ALL_FOR_MERCHANT)
                pendingMerchantCountInStats = null
            }
        )
    }
    val txCat = txForCategory
    if (txCat != null) {
        CategoryPickerSheet(
            selectedCategory = txCat.category.lowercase(),
            customCategories = state.customCategories,
            onSaveCustomCategory = viewModel::saveCustomCategory,
            onDeleteCustomCategory = viewModel::deleteCustomCategory,
            onEditCustomCategory = viewModel::editCustomCategory,
            showLongPressTip = showCategoryTip,
            onLongPressTipDismissed = onCategoryTipDismissed,
            onSelect = { cat ->
                txForCategory = null
                if (txCat.merchant.isBlank()) {
                    viewModel.updateTransactionCategory(txCat, cat, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                } else {
                    categoryScope.launch {
                        val n = viewModel.countTransactionsForMerchant(txCat.merchant)
                        when {
                            n <= 0 -> viewModel.updateTransactionCategory(txCat, cat, MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY)
                            n <= 1 -> viewModel.updateTransactionCategory(txCat, cat, MerchantCategoryApplyMode.ALL_FOR_MERCHANT)
                            else -> pendingMerchantCategory = PendingMerchantCategoryChange(txCat, cat, n)
                        }
                    }
                }
            },
            onDismiss = { txForCategory = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Summary", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (!state.hasData) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No spending this week", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nothing to show yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalExtendedColors.current.textSecondary
                    )
                }
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            WeekRangeHeader(state)
            Spacer(Modifier.height(Spacing.lg))

            TotalSpendHeroCard(state) { start, end ->
                viewModel.logWeeklyInsightCardClicked()
                onNavigateToTransactions(start, end)
            }
            Spacer(Modifier.height(Spacing.md))

            DailyBarChartCard(
                state = state,
                onDayClick = { date -> onNavigateToTransactions(date, date) }
            )
            Spacer(Modifier.height(Spacing.md))

            StatsRow(
                state = state,
                onTopCategoryClick = { category ->
                    onNavigateToTransactionsFiltered("", "", category)
                },
                onTopMerchantClick = { merchant ->
                    onNavigateToMerchant(merchant, state.weekStart.take(7))
                }
            )
            Spacer(Modifier.height(Spacing.md))

            val pieCategories = state.categoryBreakdown
                .filter { it.category.lowercase() != "unknown" }
                .take(8)
            if (pieCategories.isNotEmpty()) {
                CategoryBreakdownCard(pieCategories, state.totalSpend, state.customCategories)
                Spacer(Modifier.height(Spacing.md))
            }

            UncategorizedSection(
                state = state,
                onViewAll = {
                    onNavigateToTransactionsFiltered(state.weekStart, state.weekEnd, "others")
                },
                onTransactionClick = { tx -> selectedTx = tx },
                onCategoryClick = { tx -> txForCategory = tx }
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun WeekRangeHeader(state: WeeklySummaryUiState) {
    val ext = LocalExtendedColors.current
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val start = runCatching { LocalDate.parse(state.weekStart).format(fmt) }.getOrElse { state.weekStart }
    val end = runCatching { LocalDate.parse(state.weekEnd).format(fmt) }.getOrElse { state.weekEnd }
    val year = runCatching { LocalDate.parse(state.weekStart).year.toString() }.getOrElse { "" }

    Text(
        "$start – $end, $year",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = ext.text
    )
}

// ─── Hero card ───────────────────────────────────────────────────────────────

@Composable
private fun TotalSpendHeroCard(
    state: WeeklySummaryUiState,
    onNavigateToTransactions: (String, String) -> Unit
) {
    val ext = LocalExtendedColors.current

    FinCard(
        tonalElevation = 1.dp,
        onClick = { onNavigateToTransactions(state.weekStart, state.weekEnd) }
    ) {
        Text("Total spent", style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            AmountCompactFormatHelper.formatCompactWithRupee(state.totalSpend),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            ),
            color = ext.text
        )
        if (state.percentChange != null) {
            Spacer(Modifier.height(6.dp))
            val pct = abs(state.percentChange).roundToInt()
            val (label, color) = if (state.percentChange >= 0) {
                "↑ $pct% vs last week" to ext.warning
            } else {
                "↓ $pct% vs last week" to ext.success
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${state.txCount} transactions · ${state.merchantCount} merchants",
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
    }
}

// ─── Bar chart (same as home screen) ─────────────────────────────────────────

@Composable
private fun DailyBarChartCard(
    state: WeeklySummaryUiState,
    onDayClick: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    val today = LocalDate.now()

    // Build DailyExpenseDay list for Mon–Sun
    val days = (0..6).map { offset ->
        val date = LocalDate.parse(state.weekStart).plusDays(offset.toLong())
        val total = state.dailyTotals.find { it.date == date.toString() }?.total ?: 0.0
        DailyExpenseDay(
            dayOfMonth = date.dayOfMonth,
            dateKey = date.toString(),
            amount = total,
            isFuture = date.isAfter(today),
            isToday = date == today
        )
    }

    val listState = rememberLazyListState()

    Column {
        Text(
            "Daily spending",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ext.text,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        HomeDailyExpenseBarChart(
            days = days,
            ext = ext,
            onDayClick = onDayClick,
            listState = listState
        )
    }
}

// ─── Stats Row (top category + top merchant) ──────────────────────────────────

@Composable
private fun StatsRow(
    state: WeeklySummaryUiState,
    onTopCategoryClick: (String) -> Unit,
    onTopMerchantClick: (String) -> Unit
) {
    val topCategoryAmount = state.topCategory?.let { cat ->
        state.categoryBreakdown.firstOrNull { it.category.equals(cat, ignoreCase = true) }?.amount ?: 0.0
    } ?: 0.0

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (state.topCategory != null) {
            TopCategoryCard(
                category = state.topCategory,
                amount = topCategoryAmount,
                customCategories = state.customCategories,
                onClick = { onTopCategoryClick(state.topCategory.lowercase()) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        if (state.topMerchant != null) {
            TopMerchantCard(
                merchant = state.topMerchant,
                amount = state.topMerchantAmount,
                onClick = { onTopMerchantClick(state.topMerchant) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TopCategoryCard(
    category: String,
    amount: Double,
    customCategories: List<CustomCategory>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    val icon = CategoryCatalogHelper.categoryIcon(category.lowercase(), customCategories)
    val label = CategoryCatalogHelper.categoryLabel(category.lowercase(), customCategories)

    FinCard(tonalElevation = 1.dp, modifier = modifier, onClick = onClick) {
        Text("Top category", style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(Spacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ext.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            AmountCompactFormatHelper.formatCompactWithRupee(amount),
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
    }
}

@Composable
private fun TopMerchantCard(merchant: String, amount: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ext = LocalExtendedColors.current
    FinCard(tonalElevation = 1.dp, modifier = modifier, onClick = onClick) {
        Text("Top merchant", style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            merchant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ext.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            AmountCompactFormatHelper.formatCompactWithRupee(amount),
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
    }
}

// ─── Category Breakdown (Pie chart) ─────────────────────────────────────────

@Composable
private fun CategoryBreakdownCard(categories: List<com.fintrackai.data.local.db.CategoryStatRow>, totalSpend: Double, customCategories: List<CustomCategory>) {
    val stats = categories.map { CategoryStat(category = it.category, amount = it.amount) }
    PieChartCard(stats = stats, customCategories = customCategories, totalOverride = totalSpend)
}

// ─── Uncategorized Transactions Section ──────────────────────────────────────

@Composable
private fun UncategorizedSection(
    state: WeeklySummaryUiState,
    onViewAll: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onCategoryClick: (Transaction) -> Unit
) {
    if (state.uncategorizedTransactions.isEmpty()) return

    SectionHeader(
        title = "Uncategorised",
        actionLabel = "View all",
        onAction = onViewAll
    )
    Spacer(Modifier.height(Spacing.sm))

    FinCard(
        contentPadding = 0.dp,
        cornerRadius = AppShape.large
    ) {
        Column {
            state.uncategorizedTransactions.forEachIndexed { index, tx ->
                TransactionCard(
                    transaction = tx,
                    customCategories = state.customCategories,
                    onClick = { onTransactionClick(tx) },
                    onCategoryClick = { onCategoryClick(tx) }
                )
                if (index < state.uncategorizedTransactions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        color = LocalExtendedColors.current.border.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
