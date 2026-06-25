package com.fintrackai.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

/** Full-list screen shown when the user taps "View All" next to a section on AccountsScreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAccountsScreen(
    initialSection: String,
    onBack: () -> Unit,
    onNavigateToTransactions: (accountKey: String, accountTitle: String, typeFilter: String, dateStart: String, dateEnd: String, linkAnchorId: String, isCard: Boolean) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))

    val title = when (initialSection) {
        "credit_cards" -> "Credit Cards"
        "debit_cards" -> "Debit Cards"
        else -> "Accounts"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            when (initialSection) {
                "accounts" -> {
                    if (state.accountSummaries.isEmpty()) {
                        item {
                            Text(
                                "No accounts yet — import SMS or add transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary
                            )
                        }
                    } else {
                        items(state.accountSummaries, key = { it.accountKey }) { acc ->
                            AccountSummaryCard(
                                summary = acc,
                                formatter = formatter,
                                ext = ext,
                                onClick = { onNavigateToTransactions(acc.accountKey, acc.title, "debit", "", "", "", false) }
                            )
                        }
                    }
                }
                "credit_cards" -> {
                    if (state.creditCardSummaries.isEmpty()) {
                        item {
                            Text(
                                "No credit card spend detected yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary
                            )
                        }
                    } else {
                        itemsIndexed(state.creditCardSummaries, key = { _, c -> c.accountKey }) { index, card ->
                            CreditCardVisual(
                                index = index,
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
                }
                "debit_cards" -> {
                    if (state.debitCardSummaries.isEmpty()) {
                        item {
                            Text(
                                "No debit card transactions detected yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary
                            )
                        }
                    } else {
                        itemsIndexed(state.debitCardSummaries, key = { _, c -> c.accountKey }) { index, card ->
                            CreditCardVisual(
                                index = index + state.creditCardSummaries.size,
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
                }
            }
        }
    }
}
