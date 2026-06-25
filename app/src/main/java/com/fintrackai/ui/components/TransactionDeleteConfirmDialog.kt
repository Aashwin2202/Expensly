package com.fintrackai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TransactionDeleteConfirmDialog(
    merchantLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TransactionCardConstants.DELETE_DIALOG_TITLE) },
        text = {
            Column {
                Text(merchantLabel, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    TransactionCardConstants.DELETE_DIALOG_SUPPORTING_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    TransactionCardConstants.DELETE_CONFIRM,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(TransactionCardConstants.CANCEL)
            }
        }
    )
}
