@file:OptIn(ExperimentalMaterial3Api::class)

package com.fintrackai.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.ui.components.InputField
import com.fintrackai.ui.components.PrimaryButton
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar


@Composable
fun SetReminderScreen(
    merchant: String,
    amount: Double,
    transactionType: String,
    reminderId: String = "",
    defaultDate: String = "",
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SetReminderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val state by viewModel.uiState.collectAsState()

    val isEditing = reminderId.isNotBlank()

    // Load existing reminder when editing
    LaunchedEffect(reminderId) {
        if (isEditing) viewModel.loadExisting(reminderId)
    }

    // Derive initial values: prefer existing reminder when editing
    val existing = state.existing

    var type by remember(existing) {
        mutableStateOf(existing?.type ?: transactionType.ifBlank { "debit" })
    }
    var merchantName by remember(existing) {
        mutableStateOf(existing?.merchant ?: merchant)
    }
    var amountText by remember(existing) {
        mutableStateOf(
            existing?.amount?.let { if (it > 0) it.toLong().toString() else "" }
                ?: if (amount > 0) amount.toLong().toString() else ""
        )
    }
    var selectedCategory by remember(existing) {
        mutableStateOf(existing?.category ?: "bill")
    }
    var frequency by remember(existing) {
        mutableStateOf(existing?.frequency ?: "monthly")
    }
    var reminderDate by remember(existing) {
        mutableStateOf(
            if (existing != null) {
                existing.reminder_date
            } else {
                // Take the day-of-month from defaultDate (last transaction date) if available,
                // otherwise use today's day — always advance to next month
                val sourceDay = runCatching { LocalDate.parse(defaultDate) }
                    .getOrNull()
                    ?.dayOfMonth
                    ?: LocalDate.now().dayOfMonth
                val next = LocalDate.now().plusMonths(1).withDayOfMonth(
                    minOf(sourceDay, YearMonth.now().plusMonths(1).lengthOfMonth())
                )
                String.format("%04d-%02d-%02d", next.year, next.monthValue, next.dayOfMonth)
            }
        )
    }

    // Navigate back once saved (single trigger)
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    // While loading existing reminder, show spinner
    if (isEditing && !state.loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Edit Reminder" else "Set Reminder",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Spacer(Modifier.height(Spacing.sm))

            // Type selector
            ReminderSectionLabel("Type", ext.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("debit" to "Debit", "credit" to "Credit").forEach { (v, label) ->
                    FilterChip(
                        selected = type == v,
                        onClick = { type = v },
                        label = { Text(label) }
                    )
                }
            }

            // Merchant
            InputField(
                value = merchantName,
                onValueChange = { merchantName = it },
                label = "Merchant / Payee",
                placeholder = "e.g. Netflix, Rent",
                singleLine = true
            )

            // Amount
            InputField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Amount (₹)",
                placeholder = "0",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // Category dropdown
            ReminderSectionLabel("Category", ext.textSecondary)
            ReminderDropdown(
                options = REMINDER_CATEGORIES,
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )

            // Frequency dropdown
            ReminderSectionLabel("Frequency", ext.textSecondary)
            ReminderDropdown(
                options = FREQUENCIES,
                selected = frequency,
                onSelect = { frequency = it }
            )

            // Reminder Date
            ReminderSectionLabel("Remind me on", ext.textSecondary)
            OutlinedButton(
                onClick = {
                    val parts = reminderDate.split("-").mapNotNull { it.toIntOrNull() }
                    val cal = Calendar.getInstance()
                    val y = if (parts.size == 3) parts[0] else cal.get(Calendar.YEAR)
                    val m = if (parts.size == 3) parts[1] - 1 else cal.get(Calendar.MONTH)
                    val d = if (parts.size == 3) parts[2] else cal.get(Calendar.DAY_OF_MONTH)
                    DatePickerDialog(context, { _, year, month, day ->
                        reminderDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                    }, y, m, d).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppShape.medium)
            ) {
                Text(reminderScheduleLabel(frequency, reminderDate))
            }

            Spacer(Modifier.height(Spacing.sm))

            PrimaryButton(
                text = if (state.saving) "Saving…" else if (isEditing) "Update Reminder" else "Save Reminder",
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@PrimaryButton
                    if (merchantName.isBlank()) return@PrimaryButton
                    viewModel.saveReminder(
                        reminderId = reminderId.ifBlank { null },
                        type = type,
                        amount = amt,
                        category = selectedCategory,
                        merchant = merchantName.trim(),
                        frequency = frequency,
                        reminderDate = reminderDate
                    )
                },
                enabled = !state.saving && merchantName.isNotBlank() && amountText.isNotBlank()
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun ReminderDropdown(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(AppShape.medium)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (id, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReminderSectionLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}
