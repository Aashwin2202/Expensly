@file:OptIn(ExperimentalMaterial3Api::class)

package com.fintrackai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fintrackai.domain.model.RecurringTransaction
import com.fintrackai.domain.model.Reminder
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

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

internal fun reminderScheduleLabel(frequency: String, reminderDate: String): String {
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

/**
 * Unified item for the "all reminders" list.
 * Either a fully-saved [Reminder] or a detected [RecurringTransaction] with no reminder yet.
 */
private sealed interface ReminderListEntry {
    data class Set(val reminder: Reminder) : ReminderListEntry
    data class Unset(val recurring: RecurringTransaction) : ReminderListEntry
}

@Composable
fun AllRemindersScreen(
    reminders: List<Reminder>,
    recurringTransactions: List<RecurringTransaction>,
    onDeleteReminder: (id: String) -> Unit,
    onMarkReminderPaid: (id: String) -> Unit,
    onDismissRecurring: (merchant: String, amount: Double) -> Unit,
    onBack: () -> Unit,
    onNavigateToSetReminder: (merchant: String, amount: Double, type: String, reminderId: String, defaultDate: String) -> Unit,
    onNavigateToAddReminder: () -> Unit,
    onNavigateToMerchant: (name: String, monthKey: String) -> Unit = { _, _ -> }
) {
    val ext = LocalExtendedColors.current
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var recurringToDismiss by remember { mutableStateOf<RecurringTransaction?>(null) }
    val currentMonthKey = remember {
        val now = LocalDate.now()
        String.format("%04d-%02d", now.year, now.monthValue)
    }

    // Build a unified list: set reminders first, then unset recurring that don't have a reminder
    val entries: List<ReminderListEntry> = remember(reminders, recurringTransactions) {
        val setEntries = reminders.map { ReminderListEntry.Set(it) }
        val unsetEntries = recurringTransactions
            .filter { rt ->
                reminders.none { r ->
                    r.merchant.trim().equals(rt.merchant.trim(), ignoreCase = true) && r.amount == rt.amount
                }
            }
            .map { ReminderListEntry.Unset(it) }
        setEntries + unsetEntries
    }

    val setRemindersTotal = remember(reminders) {
        reminders.sumOf { it.amount }
    }

    reminderToDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("Delete reminder?") },
            text = { Text("Remove the reminder for ${r.merchant.ifBlank { "this item" }}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteReminder(r.id)
                    reminderToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) { Text("Cancel") }
            }
        )
    }

    recurringToDismiss?.let { rt ->
        AlertDialog(
            onDismissRequest = { recurringToDismiss = null },
            title = { Text("Remove due?") },
            text = { Text("Hide ${rt.merchant.ifBlank { "this recurring due" }} from your list?") },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRecurring(rt.merchant, rt.amount)
                    recurringToDismiss = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recurringToDismiss = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Dues & Reminders", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onNavigateToAddReminder) {
                        Icon(Icons.Default.Add, "Add reminder")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No dues or reminders yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ext.textSecondary
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = onNavigateToAddReminder) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Add Reminder")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // ── Total sum header ──────────────────────────────────────
                if (reminders.isNotEmpty()) {
                    item(key = "total_header") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(com.fintrackai.ui.theme.AppShape.large),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Total dues",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textSecondary
                                )
                                Text(
                                    "₹${formatter.format(setRemindersTotal.toLong())}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                items(entries, key = { entry ->
                    when (entry) {
                        is ReminderListEntry.Set -> "set_${entry.reminder.id}"
                        is ReminderListEntry.Unset -> "unset_${entry.recurring.merchant}|${entry.recurring.amount}"
                    }
                }) { entry ->
                    when (entry) {
                        is ReminderListEntry.Set -> ReminderListItem(
                            reminder = entry.reminder,
                            formatter = formatter,
                            onClick = { onNavigateToMerchant(entry.reminder.merchant, currentMonthKey) },
                            onEdit = {
                                onNavigateToSetReminder(
                                    entry.reminder.merchant,
                                    entry.reminder.amount,
                                    entry.reminder.type,
                                    entry.reminder.id,
                                    ""
                                )
                            },
                            onDelete = { reminderToDelete = entry.reminder },
                            onMarkPaid = { onMarkReminderPaid(entry.reminder.id) }
                        )
                        is ReminderListEntry.Unset -> UnsetReminderListItem(
                            recurring = entry.recurring,
                            formatter = formatter,
                            onClick = { onNavigateToMerchant(entry.recurring.merchant, currentMonthKey) },
                            onSetReminder = {
                                onNavigateToSetReminder(
                                    entry.recurring.merchant,
                                    entry.recurring.amount,
                                    "debit",
                                    "",
                                    entry.recurring.lastDate ?: ""
                                )
                            },
                            onDismiss = { recurringToDismiss = entry.recurring }
                        )
                    }
                }
            }
        }
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
private fun ReminderListItem(
    reminder: Reminder,
    formatter: NumberFormat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkPaid: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val categoryLabel = REMINDER_CATEGORIES.firstOrNull { it.first == reminder.category }?.second
        ?: reminder.category.replaceFirstChar { it.uppercase() }
    val scheduleLabel = reminderScheduleLabel(reminder.frequency, reminder.reminder_date)
    val status = reminderStatus(reminder)

    val borderColor = when (status) {
        ReminderStatus.PAID -> ext.success.copy(alpha = 0.5f)
        ReminderStatus.UPCOMING -> Color(0xFFF59E0B).copy(alpha = 0.6f)
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    }
    val statusColor = when (status) {
        ReminderStatus.PAID -> ext.success
        ReminderStatus.UPCOMING -> Color(0xFFF59E0B)
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.error
    }
    val statusLabel = when (status) {
        ReminderStatus.PAID -> "Paid"
        ReminderStatus.UPCOMING -> "Upcoming"
        ReminderStatus.OVERDUE -> "Overdue"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppShape.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.merchant.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "₹${formatter.format(reminder.amount.toLong())} · $categoryLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
                Text(
                    text = scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                // Status badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            // Paid button (only when not already paid)
            if (status != ReminderStatus.PAID) {
                TextButton(
                    onClick = onMarkPaid,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Paid", style = MaterialTheme.typography.labelSmall, color = ext.success)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = ext.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ext.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun UnsetReminderListItem(
    recurring: RecurringTransaction,
    formatter: NumberFormat,
    onClick: () -> Unit,
    onSetReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppShape.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, ext.border)
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.lg)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recurring.merchant.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "₹${formatter.format(recurring.amount.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ext.textSecondary.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "No reminder set",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            TextButton(onClick = onSetReminder) {
                Text(
                    text = "Set reminder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = ext.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
