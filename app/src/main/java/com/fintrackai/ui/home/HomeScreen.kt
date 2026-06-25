package com.fintrackai.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.model.CategoryStat
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.MerchantCategoryApplyMode
import com.fintrackai.domain.model.MerchantStat
import com.fintrackai.domain.model.Transaction
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.components.FinCard
import com.fintrackai.ui.components.MerchantCategoryChangeDialog
import com.fintrackai.ui.components.MerchantCountInStatsChangeDialog
import com.fintrackai.ui.components.SectionHeader
import com.fintrackai.ui.components.TransactionCard
import com.fintrackai.ui.components.TransactionDeleteConfirmDialog
import com.fintrackai.ui.components.TransactionDetailSheet
import com.fintrackai.ui.components.HeaderTitle
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import com.fintrackai.ui.transactions.PendingMerchantCategoryChange
import com.fintrackai.ui.transactions.PendingMerchantCountInStatsChange
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToTransactions: (accountKey: String, accountTitle: String, typeFilter: String, dateStart: String, dateEnd: String, linkAnchorId: String, isCard: Boolean, fromViewAll: Boolean) -> Unit,
    onNavigateToCategory: (id: String, monthKey: String) -> Unit,
    onNavigateToMerchant: (name: String, monthKey: String) -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToWeeklySummary: () -> Unit = {},
    showTutorial: Boolean = false,
    onTutorialDone: () -> Unit = {},
    showCategoryTip: Boolean = false,
    onCategoryTipDismissed: () -> Unit = {},
    showTxDetailTip: Boolean = false,
    onTxDetailTipDismissed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = LocalExtendedColors.current
    val formatter = remember { NumberFormat.getNumberInstance(Locale("en", "IN")) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showImportDialog by remember { mutableStateOf(false) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true
        hasSmsPermission = granted
        if (granted) showImportDialog = true
    }
    val currentMonthTitle = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }
    val currentMonthKey = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault()))
    }
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var detailLinkedPeers by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var txForCategory by remember { mutableStateOf<Transaction?>(null) }
    var pendingMerchantCategory by remember { mutableStateOf<PendingMerchantCategoryChange?>(null) }
    var pendingMerchantCountInStats by remember { mutableStateOf<PendingMerchantCountInStatsChange?>(null) }
    val categoryScope = rememberCoroutineScope()
    val horizontalPadding = Modifier.padding(horizontal = Spacing.xl)

    var heroCardBounds by remember { mutableStateOf<Rect?>(null) }
    var firstTxBounds by remember { mutableStateOf<Rect?>(null) }

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

    if (selectedTx != null) {
        val originalTx = selectedTx!!
        TransactionDetailSheet(
            transaction = originalTx,
            linkedPeers = detailLinkedPeers,
            onDismiss = { selectedTx = null },
            onSave = { tx ->
                if (tx.countInStats != originalTx.countInStats && originalTx.merchant.isNotBlank()) {
                    categoryScope.launch {
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
                    categoryScope.launch {
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

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import previous transactions?") },
            text = { Text("Would you like to scan your SMS inbox for past bank transactions?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    viewModel.rescanFullInboxFromHome(context.contentResolver)
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    viewModel.rescanSmsFromHome(context.contentResolver)
                }) { Text("Skip") }
            }
        )
    }

    if (state.smsRescanRunning || state.smsRescanDone) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (state.smsRescanDone) "Import complete" else "Scanning SMS…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (state.smsRescanDone) {
                        Text("Added ${state.smsRescanSaved} new transactions from your inbox.")
                    } else {
                        Text("Scanning your SMS inbox for bank transactions…")
                        Spacer(Modifier.height(Spacing.sm))
                        LinearProgressIndicator(
                            progress = { state.smsRescanPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${state.smsRescanPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalExtendedColors.current.textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                if (state.smsRescanDone) {
                    TextButton(onClick = { viewModel.clearSmsRescanDone() }) { Text("Done") }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, "Add Transaction")
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                item(key = "header") {
                    HeaderTitle(
                        "Monthly Summary",
                        subtitle = currentMonthTitle,
                        modifier = horizontalPadding
                    )
                }

                item(key = "hero_card") {
                    HeroSummaryCard(
                        expense = state.monthlyStats.expense,
                        income = state.monthlyStats.income,
                        formatter = formatter,
                        ext = ext,
                        onExpenseClick = {
                            onNavigateToTransactions("", "", "debit", "", "", "", false, true)
                        },
                        onIncomeClick = {
                            onNavigateToTransactions("", "", "credit", "", "", "", false, true)
                        },
                        onPositioned = if (showTutorial) {
                            { bounds -> heroCardBounds = bounds }
                        } else null,
                        modifier = horizontalPadding
                    )
                }

                if (!hasSmsPermission) {
                    item(key = "sms_permission_banner") {
                        SmsBanner(
                            onEnable = {
                                smsPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS
                                    )
                                )
                            },
                            modifier = horizontalPadding
                        )
                    }
                }

                if (state.dailyExpenseDays.isNotEmpty()) {
                    item(key = "daily_chart") {
                        FinCard(
                            modifier = horizontalPadding,
                            cornerRadius = AppShape.large,
                            contentPadding = Spacing.lg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleDailyChart() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Daily Expenses",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ext.text
                                )
                                Icon(
                                    if (state.showDailyChart) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (state.showDailyChart) "Hide chart" else "Show chart",
                                    tint = ext.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (state.showDailyChart) {
                                Spacer(Modifier.height(Spacing.sm))
                                HomeDailyExpenseBarChart(
                                    days = state.dailyExpenseDays,
                                    ext = ext,
                                    onDayClick = { dateKey ->
                                        onNavigateToTransactions("", "", "debit", dateKey, dateKey, "", false, false)
                                    }
                                )
                            }
                        }
                    }
                }

                item(key = "recent_tx_header") {
                    SectionHeader(
                        title = "Recent transactions",
                        actionLabel = if (state.recentTransactions.isNotEmpty()) "View all" else null,
                        onAction = {
                            onNavigateToTransactions("", "", "", "", "", "", false, true)
                        },
                        modifier = horizontalPadding
                    )
                }

                if (state.recentTransactions.isEmpty()) {
                    item(key = "empty_tx") {
                        Text(
                            "No transactions yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ext.textSecondary,
                            modifier = horizontalPadding
                        )
                    }
                } else {
                    item(key = "tx_list") {
                        FinCard(
                            modifier = horizontalPadding,
                            contentPadding = 0.dp,
                            cornerRadius = AppShape.large
                        ) {
                            state.recentTransactions.forEachIndexed { index, tx ->
                                TransactionCard(
                                    transaction = tx,
                                    customCategories = state.customCategories,
                                    onClick = { selectedTx = tx },
                                    onCategoryClick = { txForCategory = tx },
                                    modifier = if (showTutorial && index == 0)
                                        Modifier.onGloballyPositioned { coords -> firstTxBounds = coords.boundsInRoot() }
                                    else Modifier
                                )
                                if (index < state.recentTransactions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Spacing.lg),
                                        color = ext.border.copy(alpha = 0.5f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.topCategories.isNotEmpty()) {
                    item(key = "spend_by_category_header") {
                        SectionHeader(
                            title = "Spend by Category",
                            actionLabel = "View all",
                            onAction = {
                                onNavigateToTransactions("", "", "__tab_categories__", "", "", "", false, false)
                            },
                            modifier = horizontalPadding
                        )
                    }
                    item(key = "spend_by_category_list") {
                        SpendByCategoryCard(
                            categories = state.topCategories,
                            customCategories = state.customCategories,
                            formatter = formatter,
                            onCategoryClick = { categoryId ->
                                onNavigateToCategory(categoryId, currentMonthKey)
                            },
                            modifier = horizontalPadding
                        )
                    }
                }

                if (state.topMerchants.isNotEmpty()) {
                    item(key = "spend_by_merchant_header") {
                        SectionHeader(
                            title = "Spend by Merchant",
                            actionLabel = "View all",
                            onAction = {
                                onNavigateToTransactions("", "", "__tab_merchants__", "", "", "", false, false)
                            },
                            modifier = horizontalPadding
                        )
                    }
                    item(key = "spend_by_merchant_list") {
                        SpendByMerchantCard(
                            merchants = state.topMerchants,
                            formatter = formatter,
                            onMerchantClick = { merchantName ->
                                onNavigateToMerchant(merchantName, currentMonthKey)
                            },
                            modifier = horizontalPadding
                        )
                    }
                }

                item(key = "weekly_summary_button") {
                    OutlinedButton(
                        onClick = onNavigateToWeeklySummary,
                        modifier = horizontalPadding.fillMaxWidth()
                    ) {
                        Text("Weekly Summary")
                    }
                }

            }
        }
    }

    if (showTutorial) {
        HomeTutorialOverlay(
            heroCardBounds = heroCardBounds,
            firstTxBounds = firstTxBounds,
            onDone = onTutorialDone
        )
    }
    } // end Box
}

@Composable
private fun SpendByCategoryCard(
    categories: List<CategoryStat>,
    customCategories: List<CustomCategory>,
    formatter: NumberFormat,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    
    // Optimization: Memoize the total calculation
    val totalSpend = remember(categories) {
        categories.filter { !it.category.equals("investment", ignoreCase = true) }
            .sumOf { it.amount }
            .takeIf { it > 0 } ?: 1.0
    }

    FinCard(
        modifier = modifier,
        contentPadding = 0.dp,
        cornerRadius = AppShape.large
    ) {
        Column {
            categories.forEachIndexed { index, stat ->
                val pct = (stat.amount / totalSpend).coerceIn(0.0, 1.0)
                val icon = CategoryCatalogHelper.categoryIcon(stat.category, customCategories)
                val label = CategoryCatalogHelper.categoryLabel(stat.category, customCategories)
                val colorHex = CategoryCatalogHelper.categoryColor(stat.category, customCategories)
                
                val barColor = remember(colorHex) {
                    runCatching {
                        val cleaned = colorHex.trimStart('#')
                        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
                        Color(argb.toLong(16).toInt())
                    }.getOrElse { Color(0xFF3B82F6.toInt()) }
                }
                
                val isInvestment = stat.category.equals("investment", ignoreCase = true)
                val contentAlpha = if (isInvestment) 0.45f else 1f
                
                Column(
                    modifier = Modifier
                        .clickable { onCategoryClick(stat.category) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(icon, fontSize = 20.sp, color = Color.Unspecified.copy(alpha = contentAlpha))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = ext.text.copy(alpha = contentAlpha)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val formattedAmt = remember(stat.amount) { formatter.format(stat.amount.toLong()) }
                            Text(
                                "₹$formattedAmt",
                                style = MaterialTheme.typography.bodyMedium,
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
                    Spacer(Modifier.height(Spacing.xs))
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
                    } // end if (!isInvestment)
                }
                if (index < categories.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        color = ext.border.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendByMerchantCard(
    merchants: List<MerchantStat>,
    formatter: NumberFormat,
    onMerchantClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    FinCard(
        modifier = modifier,
        contentPadding = 0.dp,
        cornerRadius = AppShape.large
    ) {
        Column {
            merchants.forEachIndexed { index, stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMerchantClick(stat.merchant) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stat.merchant.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ext.text
                        )
                        Text(
                            "${stat.transactionCount} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
                        )
                    }
                    val formattedAmt = remember(stat.amount) { formatter.format(stat.amount.toLong()) }
                    Text(
                        "₹$formattedAmt",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ext.text
                    )
                }
                if (index < merchants.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        color = ext.border.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsBanner(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppShape.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = Icons.Outlined.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Auto-detect transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.text
                )
                Text(
                    "Allow SMS access to automatically import bank transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
            }
            Button(
                onClick = onEnable,
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                shape = RoundedCornerShape(AppShape.medium)
            ) {
                Text("Enable", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(
    expense: Double,
    income: Double,
    formatter: NumberFormat,
    ext: com.fintrackai.ui.theme.ExtendedColors,
    onExpenseClick: () -> Unit,
    onIncomeClick: () -> Unit,
    onPositioned: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val labelColor = ext.textSecondary
    val dividerColor = ext.border
    val expenseAmountColor = ext.error
    val incomeAmountColor = ext.success

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onPositioned != null)
                    Modifier.onGloballyPositioned { coords -> onPositioned(coords.boundsInRoot()) }
                else Modifier
            ),
        shape = RoundedCornerShape(AppShape.extraLarge),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onExpenseClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "DEBIT",
                        color = labelColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowOutward,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                val formattedExpense = remember(expense) { formatter.format(expense.toLong()) }
                Text(
                    "₹$formattedExpense",
                    color = expenseAmountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(dividerColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onIncomeClick),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CallReceived,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "CREDIT",
                        color = labelColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                val formattedIncome = remember(income) { formatter.format(income.toLong()) }
                Text(
                    "₹$formattedIncome",
                    color = incomeAmountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp
                )
            }
        }
    }
}
