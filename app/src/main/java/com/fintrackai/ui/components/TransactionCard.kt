package com.fintrackai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.Transaction
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionCard(
    transaction: Transaction,
    customCategories: List<CustomCategory> = emptyList(),
    onClick: () -> Unit = {},
    onCategoryClick: (() -> Unit)? = null,
    onCategoryIconPositioned: ((Rect) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    longPressLabel: String = TransactionCardConstants.TRANSACTION_ACTIONS_TITLE,
    isLinked: Boolean = transaction.linkGroupId != null,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    val icon = CategoryCatalogHelper.categoryIcon(transaction.category, customCategories)
    val dayDateTime = formatTransactionDayDateTime(transaction.date, transaction.time)
    val isDebit = transaction.type == "debit"
    val amountColor = if (isDebit) MaterialTheme.colorScheme.error else ext.success
    val sign = if (isDebit) "-" else "+"
    val formatted = NumberFormat.getNumberInstance(Locale("en", "IN")).format(transaction.amount.toLong())

    @Composable
    fun CardInnerContent() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (transaction.countInStats) 1f else 0.45f)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon — rounded square
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(
                        if (onCategoryIconPositioned != null)
                            Modifier.onGloballyPositioned { coords ->
                                onCategoryIconPositioned(coords.boundsInRoot())
                            }
                        else Modifier
                    )
                    .clip(RoundedCornerShape(AppShape.medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        when {
                            onCategoryClick != null && onLongClick != null -> Modifier.combinedClickable(
                                onClick = onCategoryClick,
                                onLongClick = onLongClick,
                                onLongClickLabel = longPressLabel
                            )
                            onCategoryClick != null -> Modifier.clickable(onClick = onCategoryClick)
                            onLongClick != null -> Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick,
                                onLongClickLabel = longPressLabel
                            )
                            else -> Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick,
                                onLongClickLabel = longPressLabel
                            )
                        } else {
                            Modifier.clickable(onClick = onClick)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.merchant,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = ext.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLinked) {
                            Spacer(Modifier.width(Spacing.xs))
                            Icon(
                                Icons.Default.Link,
                                contentDescription = TransactionCardConstants.LINKED_BADGE,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = dayDateTime,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = ext.textSecondary,
                        maxLines = 1
                    )
                }
                Text(
                    text = "$sign₹$formatted",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = amountColor,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                else Modifier
            )
    ) {
        CardInnerContent()
    }
}
