package com.fintrackai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fintrackai.domain.model.Transaction
import com.fintrackai.domain.transactions.TransactionLinkConstants
import com.fintrackai.domain.transactions.TransactionLinkHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionLongPressSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onLink: () -> Unit,
    onUnsplit: () -> Unit
) {
    val canLink = TransactionLinkHelper.canOfferLink(transaction)
    val isLinked = transaction.linkGroupId != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    TransactionCardConstants.TRANSACTION_ACTIONS_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            if (canLink) {
                val linkLabel = if (transaction.type.equals("debit", ignoreCase = true)) {
                    TransactionLinkConstants.LINK_CREDIT_TITLE
                } else {
                    TransactionLinkConstants.LINK_DEBIT_TITLE
                }
                Text(
                    linkLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLink()
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider()
            }
            if (isLinked) {
                Text(
                    TransactionLinkConstants.UNSPLIT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUnsplit()
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider()
            }
            Text(
                TransactionLinkConstants.DELETE,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDelete()
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
