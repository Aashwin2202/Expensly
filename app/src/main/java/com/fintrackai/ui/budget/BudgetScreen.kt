package com.fintrackai.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import com.fintrackai.ui.transactions.formatAmountWithCommas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.components.HeaderTitle
import com.fintrackai.ui.theme.LocalExtendedColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetScreen(viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    var showCategoryPicker by remember { mutableStateOf(false) }
    var newBudgetCategoryId by remember { mutableStateOf<String?>(null) }
    var newBudgetAmount by remember { mutableStateOf("") }
    var showNewBudgetDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            selectedCategory = newBudgetCategoryId ?: "others",
            customCategories = state.customCategories,
            onSaveCustomCategory = viewModel::saveCustomCategory,
            onDeleteCustomCategory = viewModel::deleteCustomCategory,
            onEditCustomCategory = viewModel::editCustomCategory,
            onSelect = { id ->
                newBudgetCategoryId = id
                newBudgetAmount = ""
                showCategoryPicker = false
                showNewBudgetDialog = true
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
    if (showNewBudgetDialog && newBudgetCategoryId != null) {
        AlertDialog(
            onDismissRequest = {
                showNewBudgetDialog = false
                newBudgetCategoryId = null
            },
            title = {
                Text(
                    "Budget for ${CategoryCatalogHelper.categoryLabel(newBudgetCategoryId!!, state.customCategories)}"
                )
            },
            text = {
                OutlinedTextField(
                    value = newBudgetAmount,
                    onValueChange = { v -> newBudgetAmount = v.filter { it.isDigit() } },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.onFocusChanged { if (!it.isFocused) newBudgetAmount = formatAmountWithCommas(newBudgetAmount) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveCategoryBudget(newBudgetCategoryId!!, newBudgetAmount)
                    showNewBudgetDialog = false
                    newBudgetCategoryId = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewBudgetDialog = false
                    newBudgetCategoryId = null
                }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeaderTitle("Budget", "People who set budgets spend up to 20% less.")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Monthly Budget", fontWeight = FontWeight.SemiBold, color = ext.text)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.monthlyBudgetInput,
                            onValueChange = viewModel::updateMonthlyBudgetInput,
                            label = { Text("₹ Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (!it.isFocused) viewModel.formatMonthlyBudgetOnBlur() },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(onClick = viewModel::saveMonthlyBudget, shape = RoundedCornerShape(12.dp)) {
                            Text("Set")
                        }
                    }
                    if (state.monthlyBudget != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val pct = if (state.monthlyBudget!! > 0) (state.monthlyExpense / state.monthlyBudget!! * 100).toInt() else 0
                        val remaining = state.monthlyBudget!! - state.monthlyExpense
                        Text(
                            "Spent: ₹${formatter.format(state.monthlyExpense.toLong())} / ₹${formatter.format(state.monthlyBudget!!.toLong())} ($pct%)",
                            color = ext.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (state.monthlyExpense / state.monthlyBudget!!).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category budgets", fontWeight = FontWeight.SemiBold, color = ext.text, fontSize = 16.sp)
                FilledIconButton(onClick = { showCategoryPicker = true }) {
                    Icon(Icons.Default.Add, "Add category budget")
                }
            }
        }

        val budgeted = state.categoryBudgets.filter { it.budget > 0 }
        if (budgeted.isEmpty()) {
            item {
                Text(
                    "Tap + to add a budget for a category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
            }
        }

        items(budgeted, key = { it.id }) { cat ->
            var input by remember(cat.id) { mutableStateOf(if (cat.budget > 0) cat.budget.toLong().toString() else "") }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.icon, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat.name, fontWeight = FontWeight.Medium, color = ext.text)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Spent: ₹${formatter.format(cat.spent.toLong())} / ₹${formatter.format(cat.budget.toLong())}",
                            color = ext.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (cat.budget > 0) {
                            val pct = ((cat.spent / cat.budget) * 100).toInt().coerceAtLeast(0)
                            Text(
                                "$pct%",
                                color = if (cat.spent > cat.budget) MaterialTheme.colorScheme.error else ext.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (cat.budget > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (cat.spent / cat.budget).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (cat.spent > cat.budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { v -> input = v.filter { it.isDigit() } },
                            label = { Text("₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (!it.isFocused) input = formatAmountWithCommas(input) },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = { viewModel.saveCategoryBudget(cat.id, input) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("Set") }
                        TextButton(
                            onClick = { viewModel.saveCategoryBudget(cat.id, "0") },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
