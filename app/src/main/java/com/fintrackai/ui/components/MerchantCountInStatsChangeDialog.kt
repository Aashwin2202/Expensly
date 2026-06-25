package com.fintrackai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.transactions.TransactionCountInStatsConstants

@Composable
fun MerchantCountInStatsChangeDialog(
    merchant: String,
    countInStats: Boolean,
    txType: String,
    merchantOccurrenceCount: Int,
    onDismiss: () -> Unit,
    onThisTransactionOnly: () -> Unit,
    onAllForMerchant: () -> Unit
) {
    val ext = LocalExtendedColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TransactionCountInStatsConstants.MERCHANT_COUNT_IN_STATS_DIALOG_TITLE) },
        text = {
            Text(
                TransactionCountInStatsConstants.dialogBody(merchant, countInStats, txType, merchantOccurrenceCount),
                color = ext.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = onThisTransactionOnly) {
                    Text(TransactionCountInStatsConstants.THIS_TRANSACTION_ONLY)
                }
                TextButton(onClick = onAllForMerchant) {
                    Text(TransactionCountInStatsConstants.ALL_FOR_MERCHANT)
                }
            }
        },
    )
}
