package com.fintrackai.ui.accounts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.fintrackai.R
import com.fintrackai.domain.account.AccountSummaryHelper
import com.fintrackai.domain.model.AccountSummary
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.ExtendedColors
import com.fintrackai.ui.theme.Spacing
import java.text.NumberFormat

/** Extracts (last4Digits, bankName) from an AccountSummary title for DB look-up. */
internal fun parseMappingKey(summary: AccountSummary): Pair<String, String>? {
    val last4 = AccountSummaryHelper.extractLast4Digits(summary.title)
        ?: AccountSummaryHelper.extractLast4Digits(summary.accountKey)
        ?: return null
    val bankName = summary.title.trim().split(" ").firstOrNull()?.uppercase() ?: return null
    return last4 to bankName
}

private val TYPE_OPTIONS = listOf(
    "credit_card" to "Credit Card",
    "debit_card"  to "Debit Card"
)

private val BANK_ABBREV_MAP = listOf(
    "hdfc" to "HDFC",
    "icici" to "ICICI",
    "standard chartered" to "SCB",
    "kotak" to "KOTAK",
    "indusind" to "IND",
    "idfc" to "IDFC",
    "axis" to "AXIS",
    "canara" to "CAN",
    "bandhan" to "BDH",
    "federal" to "FED",
    "hsbc" to "HSBC",
    "citi" to "CITI",
    "union" to "UBI",
    "idbi" to "IDBI",
    "yes bank" to "YES",
    "yes " to "YES",
    "pnb" to "PNB",
    "bob" to "BOB",
    "boi" to "BOI",
    "rbl" to "RBL",
    "sbi" to "SBI",
    "au small" to "AU",
    "au bank" to "AU",
    "au " to "AU",
    "iob" to "IOB",
    "indian overseas" to "IOB",
)

private val BANK_LOGO_RES = mapOf(
    "HDFC"  to R.drawable.bank_hdfc,
    "ICICI" to R.drawable.bank_icici,
    "SBI"   to R.drawable.bank_sbi,
    "AXIS"  to R.drawable.bank_axis,
    "KOTAK" to R.drawable.bank_kotak,
    "IND"   to R.drawable.bank_indusind,
    "IDFC"  to R.drawable.bank_idfc,
    "CAN"   to R.drawable.bank_canara,
    "BDH"   to R.drawable.bank_bandhan,
    "FED"   to R.drawable.bank_federal,
    "HSBC"  to R.drawable.bank_hsbc,
    "YES"   to R.drawable.bank_yes_bank,
    "PNB"   to R.drawable.bank_pnb,
    "BOB"   to R.drawable.bank_bank_of_baroda,
    "BOI"   to R.drawable.bank_boi,
    "AU"    to R.drawable.bank_au,
    "IOB"   to R.drawable.bank_iob,
)

fun formatBalanceDate(isoDate: String): String {
    return try {
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate
        val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val month = parts[1].toIntOrNull()?.let { months.getOrNull(it) } ?: parts[1]
        "${parts[2].trimStart('0').ifEmpty { "0" }} $month ${parts[0]}"
    } catch (e: Exception) {
        isoDate
    }
}

fun extractBankAbbreviation(title: String): String? {
    val lower = title.lowercase()
    for ((key, abbrev) in BANK_ABBREV_MAP) {
        if (lower.contains(key)) return abbrev
    }
    return null
}

fun bankLogoRes(abbrev: String): Int? = BANK_LOGO_RES[abbrev]

@Composable
fun AccountSummaryCard(
    summary: AccountSummary,
    formatter: NumberFormat,
    ext: ExtendedColors,
    onClick: () -> Unit
) {
    val bankAbbrev = extractBankAbbreviation(summary.title)
    val logoRes = bankAbbrev?.let { bankLogoRes(it) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppShape.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AppShape.medium))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (logoRes != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(AppShape.small))
                            .background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(logoRes),
                            contentDescription = "$bankAbbrev logo",
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else if (bankAbbrev != null) {
                    Text(
                        bankAbbrev,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (bankAbbrev.length <= 3) 14.sp else 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.title,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.text,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    "₹${formatter.format(summary.totalAmount.toLong())}",
                    color = ext.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "spent this month",
                    color = ext.textSecondary,
                    fontSize = 11.sp
                )
            }
            if (summary.availableBalance != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Avl Bal",
                        color = ext.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "₹${formatter.format(summary.availableBalance.toLong())}",
                        color = ext.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (summary.balanceUpdatedDate != null) {
                        Text(
                            "as of ${formatBalanceDate(summary.balanceUpdatedDate)}",
                            color = ext.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreditCardVisual(
    index: Int,
    summary: AccountSummary,
    formatter: NumberFormat,
    onClick: () -> Unit,
    onChangeType: ((newType: String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val bankAbbrev = extractBankAbbreviation(summary.title)
    val logoRes = bankAbbrev?.let { bankLogoRes(it) }
    val g = bankAbbrev?.let { AccountsConstants.BANK_CARD_GRADIENTS[it] }
        ?: AccountsConstants.CREDIT_CARD_GRADIENT_ARGB_PAIRS[index % AccountsConstants.CREDIT_CARD_GRADIENT_ARGB_PAIRS.size]
    val brush = Brush.linearGradient(
        colors = listOf(Color(g.first), Color(g.second))
    )
    var showMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(AppShape.large))
            .background(brush)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(Spacing.xl)
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (logoRes != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(AppShape.small))
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(logoRes),
                        contentDescription = "$bankAbbrev logo",
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (bankAbbrev != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppShape.small))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        bankAbbrev,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (bankAbbrev.length <= 3) 18.sp else 13.sp,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Text(
                    summary.title.trim().take(20),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
        }

        val last4 = AccountSummaryHelper.extractLast4Digits(summary.accountKey)
            ?: AccountSummaryHelper.extractLast4Digits(summary.title)
            ?: "····"
        Text(
            "XXXX  XXXX  XXXX  $last4",
            modifier = Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = 0.90f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 2.sp
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy((-6).dp)
        ) {
            Text(
                "₹${formatter.format(summary.totalAmount.toLong())}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            Text(
                "spent this month",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy((-6).dp)
        ) {
            Text(
                "₹${formatter.format(summary.allTimeTotal.toLong())}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            Text(
                "total spend",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }

        // Tappable red "!" badge for low-confidence classifications — opens type picker
        if (!summary.isConfident && onChangeType != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFD32F2F))
                    .clickable { showTypeMenu = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "!",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                Text(
                    "Set card type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                TYPE_OPTIONS.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            showTypeMenu = false
                            onChangeType(type)
                        }
                    )
                }
            }
        }

        // Long-press menu: change type + delete
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (onChangeType != null) {
                Text(
                    "Change type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                TYPE_OPTIONS.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            showMenu = false
                            onChangeType(type)
                        }
                    )
                }
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete card", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
