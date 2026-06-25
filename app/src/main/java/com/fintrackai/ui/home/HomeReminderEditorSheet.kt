@file:OptIn(ExperimentalLayoutApi::class)

// FUTURE: HomeScreen reminders UI is commented out; re-wire [HomeReminderEditorSheet] when developing reminders.

package com.fintrackai.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.Reminder
import com.fintrackai.domain.model.RecurringTransaction
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.theme.LocalExtendedColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeReminderEditorSheet(
    editing: Reminder?,
    fromRecurring: RecurringTransaction?,
    customCategories: List<CustomCategory> = emptyList(),
    onSaveCustomCategory: ((name: String, emoji: String, onSaved: (categoryId: String) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveReminder: (
        id: String?,
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        frequency: String,
        reminderDate: String
    ) -> Unit,
    onDoneWithRecurringSource: (merchant: String, amount: Double) -> Unit
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    var type by remember { mutableStateOf(editing?.type ?: "debit") }
    var amount by remember { mutableStateOf(editing?.amount?.toString() ?: fromRecurring?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(editing?.category ?: "others") }
    var merchant by remember { mutableStateOf(editing?.merchant ?: fromRecurring?.merchant ?: "") }
    var frequency by remember {
        mutableStateOf(
            editing?.frequency ?: intervalToFrequency(fromRecurring?.interval ?: 1)
        )
    }
    var reminderDate by remember {
        mutableStateOf(
            editing?.reminder_date?.takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                ?: fromRecurring?.lastDate?.takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                ?: run {
                    val c = Calendar.getInstance()
                    String.format(
                        "%04d-%02d-%02d",
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1,
                        c.get(Calendar.DAY_OF_MONTH)
                    )
                }
        )
    }
    var showCategoryPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editing != null) "Edit reminder" else "New reminder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ext.text
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Type", style = MaterialTheme.typography.labelLarge, color = ext.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("debit" to "Debit", "credit" to "Credit").forEach { (v, l) ->
                    FilterChip(
                        selected = type == v,
                        onClick = { type = v },
                        label = { Text(l) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showCategoryPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Category: ${CategoryCatalogHelper.categoryLabel(category, customCategories)}")
            }
            if (showCategoryPicker) {
                CategoryPickerSheet(
                    selectedCategory = category.lowercase(),
                    customCategories = customCategories,
                    onSaveCustomCategory = onSaveCustomCategory,
                    onSelect = { category = it.lowercase(); showCategoryPicker = false },
                    onDismiss = { showCategoryPicker = false }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Frequency", style = MaterialTheme.typography.labelLarge, color = ext.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "monthly" to "Monthly",
                    "quarterly" to "Quarterly",
                    "half_yearly" to "6 months",
                    "yearly" to "Yearly"
                ).forEach { (v, l) ->
                    FilterChip(selected = frequency == v, onClick = { frequency = v }, label = { Text(l) })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val parts = reminderDate.split("-").mapNotNull { it.toIntOrNull() }
                    if (parts.size == 3) {
                        DatePickerDialog(context, { _, y, m, d ->
                            reminderDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                        }, parts[0], parts[1] - 1, parts[2]).show()
                    } else {
                        val c = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            reminderDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reminder date: $reminderDate")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@Button
                    if (merchant.isBlank()) return@Button
                    val id = editing?.id
                    onSaveReminder(id, type, amt, category.lowercase(), merchant.trim(), frequency, reminderDate)
                    fromRecurring?.let { onDoneWithRecurringSource(it.merchant, it.amount) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun intervalToFrequency(interval: Int): String {
    if (interval >= 12) return "yearly"
    if (interval >= 6) return "half_yearly"
    if (interval >= 3) return "quarterly"
    return "monthly"
}
