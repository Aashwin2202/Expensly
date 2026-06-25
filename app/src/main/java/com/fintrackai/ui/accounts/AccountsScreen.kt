package com.fintrackai.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.model.RecurringTransaction
import com.fintrackai.domain.model.Reminder
import com.fintrackai.ui.components.HeaderTitle
import com.fintrackai.ui.components.SectionHeader
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

// parseMappingKey is defined in AccountsScreenHelpers.kt (package-internal)

@Composable
fun AccountsScreen(
    onNavigateToTransactions: (accountKey: String, accountTitle: String, typeFilter: String, dateStart: String, dateEnd: String, linkAnchorId: String, isCard: Boolean) -> Unit,
    onNavigateToAllAccounts: (section: String) -> Unit,
    onNavigateToSetReminder: (merchant: String, amount: Double, type: String, reminderId: String, defaultDate: String) -> Unit,
    onNavigateToAllReminders: () -> Unit,
    onNavigateToMerchant: (name: String, monthKey: String) -> Unit = { _, _ -> },
    showTutorial: Boolean = false,
    onTutorialDone: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    var creditCardBounds by remember { mutableStateOf<Rect?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            HeaderTitle(
                "Wallet",
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )
        }

        // ── Accounts ──────────────────────────────────────────────────────
        item {
            SectionHeader(
                title = "Accounts",
                actionLabel = if (state.accountSummaries.size > 1) "View All" else null,
                onAction = { onNavigateToAllAccounts("accounts") },
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )
        }
        if (state.accountSummaries.isEmpty()) {
            item {
                Text(
                    "No accounts yet — import SMS or add transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        } else {
            item(key = "accounts_pager") {
                val pagerState = rememberPagerState { state.accountSummaries.size }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val acc = state.accountSummaries[page]
                        Box(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                        AccountSummaryCard(
                            summary = acc,
                            formatter = formatter,
                            ext = ext,
                            onClick = { onNavigateToTransactions(acc.accountKey, acc.title, "debit", "", "", "", false) }
                        )
                        }
                    }
                    if (state.accountSummaries.size > 1) {
                        PagerDots(
                            count = state.accountSummaries.size,
                            currentPage = pagerState.currentPage,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        // ── Credit Cards ──────────────────────────────────────────────────
        item {
            SectionHeader(
                title = "Credit Cards",
                actionLabel = if (state.creditCardSummaries.size > 1) "View All" else null,
                onAction = { onNavigateToAllAccounts("credit_cards") },
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )
        }
        if (state.creditCardSummaries.isEmpty()) {
            item {
                Text(
                    "No credit card spend detected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        } else {
            item(key = "credit_cards_pager") {
                val pagerState = rememberPagerState { state.creditCardSummaries.size }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val card = state.creditCardSummaries[page]
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.xl)
                                .then(
                                    if (showTutorial && page == 0)
                                        Modifier.onGloballyPositioned { coords ->
                                            creditCardBounds = coords.boundsInRoot()
                                        }
                                    else Modifier
                                )
                        ) {
                        CreditCardVisual(
                            index = page,
                            summary = card,
                            formatter = formatter,
                            onClick = { onNavigateToTransactions(card.accountKey, card.title, "debit", "", "", "", true) },
                            onChangeType = { newType ->
                                parseMappingKey(card)?.let { (last4, bank) ->
                                    viewModel.changeCardType(last4, bank, newType)
                                }
                            },
                            onDelete = {
                                parseMappingKey(card)?.let { (last4, bank) ->
                                    viewModel.deleteCard(last4, bank)
                                }
                            }
                        )
                        }
                    }
                    if (state.creditCardSummaries.size > 1) {
                        PagerDots(
                            count = state.creditCardSummaries.size,
                            currentPage = pagerState.currentPage,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        // ── Debit Cards ───────────────────────────────────────────────────
        item {
            SectionHeader(
                title = "Debit Cards",
                actionLabel = if (state.debitCardSummaries.size > 1) "View All" else null,
                onAction = { onNavigateToAllAccounts("debit_cards") },
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )
        }
        if (state.debitCardSummaries.isEmpty()) {
            item {
                Text(
                    "No debit card transactions detected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        } else {
            item(key = "debit_cards_pager") {
                val pagerState = rememberPagerState { state.debitCardSummaries.size }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val card = state.debitCardSummaries[page]
                        Box(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                        CreditCardVisual(
                            index = page + state.creditCardSummaries.size,
                            summary = card,
                            formatter = formatter,
                            onClick = { onNavigateToTransactions(card.accountKey, card.title, "debit", "", "", "", true) },
                            onChangeType = { newType ->
                                parseMappingKey(card)?.let { (last4, bank) ->
                                    viewModel.changeCardType(last4, bank, newType)
                                }
                            },
                            onDelete = {
                                parseMappingKey(card)?.let { (last4, bank) ->
                                    viewModel.deleteCard(last4, bank)
                                }
                            }
                        )
                        }
                    }
                    if (state.debitCardSummaries.size > 1) {
                        PagerDots(
                            count = state.debitCardSummaries.size,
                            currentPage = pagerState.currentPage,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        // ── Dues & Reminders ──────────────────────────────────────────────
        if (state.recurringTransactions.isNotEmpty() || state.reminders.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Dues & Reminders",
                    actionLabel = "View All",
                    onAction = onNavigateToAllReminders,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
            if (state.recurringTransactions.isNotEmpty()) {
                item(key = "dues_row") {
                    val currentMonthKey = remember {
                        val now = java.time.LocalDate.now()
                        String.format("%04d-%02d", now.year, now.monthValue)
                    }
                    DuesRow(
                        recurringList = state.recurringTransactions,
                        reminders = state.reminders,
                        formatter = formatter,
                        onSetReminder = { rt ->
                            onNavigateToSetReminder(rt.merchant, rt.amount, "debit", "", rt.lastDate ?: "")
                        },
                        onEditReminder = { _, reminder ->
                            onNavigateToSetReminder(reminder.merchant, reminder.amount, reminder.type, reminder.id, "")
                        },
                        onDismiss = { rt -> viewModel.dismissRecurring(rt.merchant, rt.amount) },
                        onNavigateToMerchant = { name -> onNavigateToMerchant(name, currentMonthKey) },
                        onMarkPaid = { reminder -> viewModel.markReminderPaid(reminder.id) },
                        currentMonthKey = currentMonthKey
                    )
                }
            }
        }
    }

    if (showTutorial) {
        WalletTutorialOverlay(
            creditCardBounds = creditCardBounds,
            onDone = onTutorialDone
        )
    }
    } // end Box
}

@Composable
private fun PagerDots(
    count: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 5.dp)
                    .clip(CircleShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1))
                }
            }
        }
    }
}

@Composable
private fun DuesRow(
    recurringList: List<RecurringTransaction>,
    reminders: List<Reminder>,
    formatter: NumberFormat,
    onSetReminder: (RecurringTransaction) -> Unit,
    onEditReminder: (RecurringTransaction, Reminder) -> Unit,
    onDismiss: (RecurringTransaction) -> Unit,
    onNavigateToMerchant: (name: String) -> Unit,
    onMarkPaid: (Reminder) -> Unit,
    currentMonthKey: String
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(horizontal = Spacing.xl)
    ) {
        items(recurringList, key = { "${it.merchant}|${it.amount}" }) { rt ->
            val matchedReminder = reminders.firstOrNull { r ->
                r.merchant.trim().equals(rt.merchant.trim(), ignoreCase = true) &&
                        r.amount == rt.amount
            }
            DueCard(
                recurring = rt,
                matchedReminder = matchedReminder,
                formatter = formatter,
                onSetReminder = { onSetReminder(rt) },
                onEditReminder = { rem -> onEditReminder(rt, rem) },
                onDismiss = { onDismiss(rt) },
                onNavigateToMerchant = { onNavigateToMerchant(rt.merchant) },
                onMarkPaid = { rem -> onMarkPaid(rem) },
                currentMonthKey = currentMonthKey
            )
        }
    }
}

private fun dayOrdinal(day: Int): String {
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

private fun reminderScheduleLabel(frequency: String, reminderDate: String): String {
    val date = runCatching { LocalDate.parse(reminderDate) }.getOrNull()
    return when (frequency) {
        "monthly" -> if (date != null) "${dayOrdinal(date.dayOfMonth)} every month" else "Monthly"
        "quarterly" -> if (date != null) "${dayOrdinal(date.dayOfMonth)} every quarter" else "Quarterly"
        "half_yearly" -> if (date != null) "${dayOrdinal(date.dayOfMonth)} every 6 months" else "Every 6 months"
        "yearly" -> if (date != null) {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")
            "${date.format(fmt)} every year"
        } else "Yearly"
        else -> frequency.replaceFirstChar { it.uppercaseChar() }
    }
}

private enum class ReminderStatus { PAID, UPCOMING, OVERDUE }

private fun reminderStatus(reminder: Reminder): ReminderStatus {
    // paid_on is only valid for the current calendar month — resets in a new month
    if (reminder.paid_on != null) {
        val paidDate = runCatching { LocalDate.parse(reminder.paid_on) }.getOrNull()
        val today = LocalDate.now()
        if (paidDate != null &&
            paidDate.year == today.year &&
            paidDate.monthValue == today.monthValue
        ) return ReminderStatus.PAID
    }
    val due = runCatching { LocalDate.parse(reminder.reminder_date) }.getOrNull()
        ?: return ReminderStatus.UPCOMING
    return if (LocalDate.now().isAfter(due)) ReminderStatus.OVERDUE else ReminderStatus.UPCOMING
}

@Composable
private fun DueCard(
    recurring: RecurringTransaction,
    matchedReminder: Reminder?,
    formatter: NumberFormat,
    onSetReminder: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToMerchant: () -> Unit,
    onMarkPaid: (Reminder) -> Unit,
    currentMonthKey: String
) {
    val ext = LocalExtendedColors.current
    val reminderSet = matchedReminder != null
    var showDismissConfirm by remember { mutableStateOf(false) }

    val status = matchedReminder?.let { reminderStatus(it) }
    val borderColor = when (status) {
        ReminderStatus.PAID -> ext.success.copy(alpha = 0.5f)
        ReminderStatus.UPCOMING -> Color(0xFFF59E0B).copy(alpha = 0.6f)   // amber
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        null -> ext.border
    }
    val statusColor = when (status) {
        ReminderStatus.PAID -> ext.success
        ReminderStatus.UPCOMING -> Color(0xFFF59E0B)
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.error
        null -> ext.border
    }
    val statusLabel = when (status) {
        ReminderStatus.PAID -> "Paid"
        ReminderStatus.UPCOMING -> "Upcoming"
        ReminderStatus.OVERDUE -> "Overdue"
        null -> null
    }

    if (showDismissConfirm) {
        AlertDialog(
            onDismissRequest = { showDismissConfirm = false },
            title = { Text("Remove reminder?") },
            text = { Text("Are you sure you don't want a reminder for ${recurring.merchant.ifBlank { "this due" }}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDismissConfirm = false
                    onDismiss()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDismissConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        modifier = Modifier
            .width(172.dp)
            .wrapContentHeight()
            .clickable(onClick = { onNavigateToMerchant() }),
        shape = RoundedCornerShape(AppShape.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(width = 0.5.dp, color = borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Header: merchant + pencil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = recurring.merchant.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (reminderSet && matchedReminder != null) {
                    IconButton(
                        onClick = { onEditReminder(matchedReminder) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit reminder",
                            tint = ext.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Amount
            Text(
                text = "₹${formatter.format(recurring.amount.toLong())}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (reminderSet && matchedReminder != null) {
                // Schedule label
                Text(
                    reminderScheduleLabel(matchedReminder.frequency, matchedReminder.reminder_date),
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary,
                    maxLines = 1
                )
                // Status badge + Paid button row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    statusLabel?.let { label ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (status == ReminderStatus.UPCOMING || status == ReminderStatus.OVERDUE) {
                        TextButton(
                            onClick = { onMarkPaid(matchedReminder) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Paid", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                // No reminder yet — Yes/No prompt
                Text(
                    "Set a reminder?",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedButton(
                        onClick = { showDismissConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(AppShape.medium)
                    ) {
                        Text("No", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onSetReminder,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(AppShape.medium)
                    ) {
                        Text("Yes", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
