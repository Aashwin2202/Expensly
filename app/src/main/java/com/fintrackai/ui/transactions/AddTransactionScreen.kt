package com.fintrackai.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.components.FinCard
import com.fintrackai.ui.components.InputField
import com.fintrackai.ui.components.PrimaryButton
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    var showCategoryPicker by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            selectedCategory = state.category,
            customCategories = state.customCategories,
            onSaveCustomCategory = viewModel::saveCustomCategory,
            onDeleteCustomCategory = viewModel::deleteCustomCategory,
            onEditCustomCategory = viewModel::editCustomCategory,
            onSelect = { viewModel.updateCategory(it) },
            onDismiss = { showCategoryPicker = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
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

            // Prominent amount input
            FinCard(
                cornerRadius = AppShape.extraLarge,
                contentPadding = Spacing.xxl
            ) {
                Text(
                    "Amount",
                    style = MaterialTheme.typography.labelMedium,
                    color = ext.textSecondary
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "₹",
                        style = TextStyle(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.text
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    BasicTextField(
                        value = state.amount,
                        onValueChange = viewModel::updateAmount,
                        textStyle = TextStyle(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.text,
                            letterSpacing = (-1).sp
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (!it.isFocused) viewModel.formatAmountOnBlur() },
                        decorationBox = { innerTextField ->
                            Box {
                                if (state.amount.isEmpty()) {
                                    Text(
                                        "0",
                                        style = TextStyle(
                                            fontSize = 40.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ext.textSecondary.copy(alpha = 0.3f),
                                            letterSpacing = (-1).sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // Type selector chips
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = state.type == "debit",
                    onClick = { viewModel.updateType("debit") },
                    label = { Text("Debit") },
                    shape = RoundedCornerShape(AppShape.medium)
                )
                FilterChip(
                    selected = state.type == "credit",
                    onClick = { viewModel.updateType("credit") },
                    label = { Text("Credit") },
                    shape = RoundedCornerShape(AppShape.medium)
                )
            }

            // Merchant
            InputField(
                value = state.merchant,
                onValueChange = viewModel::updateMerchant,
                label = "Merchant"
            )

            // Date + Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                InputField(
                    value = state.date,
                    onValueChange = viewModel::updateDate,
                    label = AddTransactionConstants.DATE_LABEL,
                    placeholder = AddTransactionConstants.DATE_PLACEHOLDER,
                    modifier = Modifier.weight(1f)
                )
                InputField(
                    value = state.time,
                    onValueChange = viewModel::updateTime,
                    label = AddTransactionConstants.TIME_LABEL,
                    placeholder = AddTransactionConstants.TIME_PLACEHOLDER,
                    modifier = Modifier.weight(1f)
                )
            }

            // Category selector
            Surface(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppShape.medium),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, ext.border.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Category",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textSecondary
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            CategoryCatalogHelper.categoryLabel(state.category, state.customCategories),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = ext.text
                        )
                    }
                    Text(
                        CategoryCatalogHelper.categoryIcon(state.category, state.customCategories),
                        fontSize = 24.sp
                    )
                }
            }

            // Account selector
            ExposedDropdownMenuBox(
                expanded = accountMenuExpanded,
                onExpandedChange = { accountMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                InputField(
                    value = state.accounts,
                    onValueChange = viewModel::updateAccounts,
                    label = "Account (optional)",
                    placeholder = "Type or choose from list",
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(AddTransactionConstants.ACCOUNT_MENU_CLEAR) },
                        onClick = {
                            viewModel.updateAccounts("")
                            accountMenuExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                    state.knownAccountOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, maxLines = 2) },
                            onClick = {
                                viewModel.updateAccounts(option)
                                accountMenuExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            PrimaryButton(
                text = "Save Transaction",
                onClick = viewModel::save,
                loading = state.saving
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}
