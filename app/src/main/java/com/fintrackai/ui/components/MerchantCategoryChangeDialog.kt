package com.fintrackai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.transactions.TransactionCategoryConstants

@Composable
fun MerchantCategoryChangeDialog(
    merchant: String,
    categoryLabel: String,
    merchantOccurrenceCount: Int,
    onDismiss: () -> Unit,
    onThisTransactionOnly: () -> Unit,
    onAllForMerchant: () -> Unit
) {
    val ext = LocalExtendedColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TransactionCategoryConstants.MERCHANT_CATEGORY_DIALOG_TITLE) },
        text = {
            Text(
                TransactionCategoryConstants.dialogBody(merchant, categoryLabel, merchantOccurrenceCount),
                color = ext.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = onThisTransactionOnly) {
                    Text(TransactionCategoryConstants.THIS_TRANSACTION_ONLY)
                }
                TextButton(onClick = onAllForMerchant) {
                    Text(TransactionCategoryConstants.ALL_FOR_MERCHANT)
                }
            }
        },
    )
}
