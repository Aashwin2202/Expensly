package com.fintrackai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.category.CategoryOption
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.Reminder
import com.fintrackai.domain.model.Transaction
import com.fintrackai.domain.transactions.TransactionLinkConstants
import com.fintrackai.domain.transactions.TransactionLinkHelper
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.transactions.TransactionCountInStatsConstants
import com.fintrackai.ui.transactions.formatAmountWithCommas
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: Transaction?,
    linkedPeers: List<Transaction> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit = {},
    onRenameMerchant: ((originalName: String, newName: String, applyToAll: Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReportWrongDetection: ((reason: String, comments: String) -> Unit)? = null,
    onUnsplit: (() -> Unit)? = null,
    showTip: Boolean = false,
    onTipDismissed: () -> Unit = {}
) {
    if (transaction == null) return
    val ext = LocalExtendedColors.current
    val scroll = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<Pair<String, String>?>(null) } // originalName to newName
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant) }
    var amountStr by remember(transaction.id) { mutableStateOf(editableAmountString(transaction.amount)) }
    var type by remember(transaction.id) { mutableStateOf(transaction.type) }
    var countInStats by remember(transaction.id) { mutableStateOf(transaction.countInStats) }

    val merchantChanged by remember(transaction.id) {
        derivedStateOf { merchant.trim() != transaction.merchant.trim() }
    }

    // Track whether any editable field has changed
    val isDirty by remember(transaction.id) {
        derivedStateOf {
            merchant.trim() != transaction.merchant.trim() ||
                amountStr.replace(",", "").trim() != editableAmountString(transaction.amount) ||
                type != transaction.type ||
                countInStats != transaction.countInStats
        }
    }

    fun buildSaved(): Transaction? {
        val normalized = amountStr.replace(",", "").trim()
        val amt = normalized.toDoubleOrNull() ?: return null
        if (amt <= 0) return null
        if (merchant.isBlank()) return null
        return transaction.copy(
            merchant = merchant.trim(),
            amount = amt,
            type = type,
            countInStats = countInStats
        )
    }

    // On dismiss: if merchant name changed and rename callback provided, intercept to show dialog
    val onDismissWithSave: () -> Unit = {
        val saved = if (isDirty) buildSaved() else null
        if (saved != null) onSave(saved)
        if (merchantChanged && onRenameMerchant != null) {
            pendingRename = transaction.merchant.trim() to merchant.trim()
        } else {
            onDismiss()
        }
    }

    // Merchant rename dialog
    pendingRename?.let { (originalName, newName) ->
        AlertDialog(
            onDismissRequest = { pendingRename = null; onDismiss() },
            title = { Text("Rename Merchant") },
            text = {
                Text(
                    "Rename \"$originalName\" to \"$newName\" for all existing transactions and future imports, or just this one?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        onRenameMerchant!!(originalName, newName, true)
                        pendingRename = null
                        onDismiss()
                    }) { Text("All transactions") }
                    TextButton(onClick = {
                        onRenameMerchant!!(originalName, newName, false)
                        pendingRename = null
                        onDismiss()
                    }) { Text("Only this transaction") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null; onDismiss() }) { Text("Cancel") }
            }
        )
    }

    if (showReportDialog && onReportWrongDetection != null) {
        TransactionReportDialog(
            onDismiss = { showReportDialog = false },
            onSend = { reason, comments ->
                onReportWrongDetection(reason, comments)
                showReportDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Thanks for your feedback!")
                }
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismissWithSave, sheetState = sheetState) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(horizontal = 16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .verticalScroll(scroll)
        ) {
            // Header row with title and three-dot menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ext.text)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (onReportWrongDetection != null && transaction.originalSms != null) {
                            DropdownMenuItem(
                                text = { Text("Report Wrong Detection") },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (showTip) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { onTipDismissed() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Tap ⋮ to report a wrong transaction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amountStr,
                onValueChange = { v -> amountStr = formatAmountInput(v) },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) amountStr = formatAmountWithCommas(amountStr) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Type", style = MaterialTheme.typography.labelLarge, color = ext.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "debit", onClick = { type = "debit" }, label = { Text("Debit") })
                FilterChip(selected = type == "credit", onClick = { type = "credit" }, label = { Text("Credit") })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (type == "debit") TransactionCountInStatsConstants.COUNT_IN_EXPENSE
                        else TransactionCountInStatsConstants.COUNT_AS_INCOME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.text
                    )
                }
                Switch(checked = countInStats, onCheckedChange = { countInStats = it })
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (transaction.linkGroupId != null) {
                val peerFmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
                val linkAccent = MaterialTheme.colorScheme.tertiary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, linkAccent.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = linkAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(TransactionCardConstants.LINKED_BADGE.uppercase(), style = MaterialTheme.typography.labelLarge, color = linkAccent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (linkedPeers.isEmpty()) {
                            Text("…", style = MaterialTheme.typography.bodySmall, color = ext.text)
                        } else {
                            linkedPeers.forEachIndexed { idx, peer ->
                                val peerSign = if (peer.type == "debit") "-" else "+"
                                val typeWord = if (peer.type == "debit") "expense" else "credit"
                                Text(
                                    "${TransactionLinkConstants.LINKED_WITH_PREFIX} ${peer.merchant.ifBlank { "—" }} ($typeWord $peerSign₹${peerFmt.format(peer.amount.toLong())})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.text
                                )
                                if (idx < linkedPeers.lastIndex) Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (onUnsplit != null) {
                    OutlinedButton(
                        onClick = { onUnsplit(); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(TransactionLinkConstants.UNSPLIT)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Read-only info rows
            Spacer(modifier = Modifier.height(8.dp))
            val formattedDate = formatTransactionDate(transaction.date)
            if (formattedDate.isNotBlank()) {
                DetailInfoRow("Date", formattedDate, ext)
            }
            if (transaction.time.isNotBlank()) {
                DetailInfoRow("Time", transaction.time, ext)
            }
            if (transaction.accounts.isNotBlank()) {
                DetailInfoRow("Account", transaction.accounts, ext)
            }
            if (!transaction.originalSms.isNullOrBlank() || !transaction.smsSender.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Original Message", style = MaterialTheme.typography.labelLarge, color = ext.textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                if (!transaction.smsSender.isNullOrBlank()) {
                    Text(
                        "From: ${transaction.smsSender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val sms = transaction.originalSms ?: return@clickable
                            clipboardManager.setText(AnnotatedString(sms))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Message copied")
                            }
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = transaction.originalSms ?: "—",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.text
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private val wrongDetectionReasons = listOf(
    "Not a transaction",
    "Wrong Merchant",
    "Wrong Category",
    "Wrong Amount",
    "Wrong Type (Debit/Credit)",
    "Duplicate",
    "Other"
)

@Composable
private fun TransactionReportDialog(
    onDismiss: () -> Unit,
    onSend: (reason: String, comments: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf("") }
    val ext = LocalExtendedColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Wrong Detection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What was detected incorrectly?", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                wrongDetectionReasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Additional comments (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(selectedReason ?: "Other", comments) },
                enabled = selectedReason != null,
                shape = RoundedCornerShape(12.dp)
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun editableAmountString(amount: Double): String =
    if (floor(amount) == amount) amount.toLong().toString()
    else amount.toString()

private fun formatAmountInput(v: String): String {
    val filtered = v.filter { it.isDigit() || it == '.' }
    return if (filtered.count { it == '.' } > 1) {
        val first = filtered.indexOf('.')
        filtered.substring(0, first + 1) + filtered.substring(first + 1).replace(".", "")
    } else filtered
}

/** Formats "2025-04-03" → "Wednesday, 3 Apr 2025". Returns blank on parse failure. */
private fun formatTransactionDate(dateStr: String): String {
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEEE, d MMM yyyy", Locale.ENGLISH).format(parsed)
    } catch (_: Exception) {
        dateStr
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String, ext: com.fintrackai.ui.theme.ExtendedColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ext.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ext.text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val ext = LocalExtendedColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ext.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = ext.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryPickerSheet(
    selectedCategory: String,
    customCategories: List<CustomCategory> = emptyList(),
    onSaveCustomCategory: ((name: String, emoji: String, onSaved: (categoryId: String) -> Unit) -> Unit)? = null,
    onDeleteCustomCategory: ((id: String) -> Unit)? = null,
    onEditCustomCategory: ((id: String, name: String, emoji: String) -> Unit)? = null,
    showLongPressTip: Boolean = false,
    onLongPressTipDismissed: () -> Unit = {},
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember(customCategories) { CategoryCatalogHelper.mergeOptions(customCategories) }
    val optionRows = remember(options) { options.chunked(4) }

    // Add-new dialog state
    var newName by remember { mutableStateOf("") }
    var draftEmoji by remember { mutableStateOf("❓") }
    var addError by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    // Edit dialog state
    var editingOption by remember { mutableStateOf<CategoryOption?>(null) }
    var editName by remember { mutableStateOf("") }
    var editEmoji by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    // Delete confirm state
    var deletingOption by remember { mutableStateOf<CategoryOption?>(null) }

    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "heading") {
                Text("Select Category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (showLongPressTip) {
                item(key = "longpress_tip") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { onLongPressTipDismissed() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Long press any category to edit or delete it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (onSaveCustomCategory != null) {
                item(key = "add_category") {
                    OutlinedButton(
                        onClick = {
                            newName = ""
                            draftEmoji = "❓"
                            addError = null
                            showAddCategoryDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(CategoryPickerConstants.ADD_CATEGORY_BUTTON)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            items(
                items = optionRows,
                key = { chunk -> chunk.joinToString(",") { it.id } }
            ) { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunk.forEach { opt ->
                        val selected = opt.id.equals(selectedCategory, ignoreCase = true)
                        val canManage = (onEditCustomCategory != null || onDeleteCustomCategory != null)
                        val canDelete = onDeleteCustomCategory != null && opt.id != "others"
                        Box(modifier = Modifier.weight(1f)) {
                            var showMenu by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .then(
                                        if (selected) Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(10.dp)
                                        ) else Modifier
                                    )
                                    .then(
                                        if (canManage)
                                            Modifier.combinedClickable(
                                                onClick = { onSelect(opt.id); onDismiss() },
                                                onLongClick = { showMenu = true }
                                            )
                                        else
                                            Modifier.clickable { onSelect(opt.id); onDismiss() }
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(opt.icon, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    opt.label,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (canManage) {
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    if (onEditCustomCategory != null) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                showMenu = false
                                                editingOption = opt
                                                editName = opt.label
                                                editEmoji = opt.icon
                                                editError = null
                                            }
                                        )
                                    }
                                    if (canDelete) {
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMenu = false
                                                deletingOption = opt
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(4 - chunk.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // ── Add dialog ──────────────────────────────────────────────
    if (showAddCategoryDialog && onSaveCustomCategory != null) {
        val saveCustom = onSaveCustomCategory
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(CategoryPickerConstants.ADD_CATEGORY_TITLE) },
            text = {
                val ext = LocalExtendedColors.current
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftEmoji.takeIf { it != "❓" } ?: "",
                        onValueChange = { v ->
                            val cluster = v.graphemeClusters().lastOrNull()
                            draftEmoji = cluster ?: "❓"
                        },
                        label = { Text("Emoji") },
                        placeholder = { Text("Choose emoji from keyboard") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; addError = null },
                        label = { Text(CategoryPickerConstants.CATEGORY_NAME_LABEL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    addError?.let { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isBlank()) { addError = CategoryPickerConstants.CATEGORY_NAME_REQUIRED; return@TextButton }
                    addError = null
                    saveCustom(newName.trim(), draftEmoji) { id ->
                        newName = ""; draftEmoji = "❓"; showAddCategoryDialog = false
                        onSelect(id); onDismiss()
                    }
                }) { Text(CategoryPickerConstants.SAVE_CATEGORY) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text(CategoryPickerConstants.CANCEL) }
            }
        )
    }

    // ── Edit dialog ─────────────────────────────────────────────
    val editTarget = editingOption
    if (editTarget != null && onEditCustomCategory != null) {
        AlertDialog(
            onDismissRequest = { editingOption = null },
            title = { Text("Edit Category") },
            text = {
                val ext = LocalExtendedColors.current
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editEmoji,
                        onValueChange = { v ->
                            val cluster = v.graphemeClusters().lastOrNull()
                            editEmoji = cluster ?: ""
                        },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it; editError = null },
                        label = { Text("Category name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    editError?.let { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isBlank()) { editError = CategoryPickerConstants.CATEGORY_NAME_REQUIRED; return@TextButton }
                    onEditCustomCategory(editTarget.id, editName.trim(), editEmoji)
                    editingOption = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingOption = null }) { Text(CategoryPickerConstants.CANCEL) }
            }
        )
    }

    // ── Delete confirm dialog ────────────────────────────────────
    val deleteTarget = deletingOption
    if (deleteTarget != null && onDeleteCustomCategory != null) {
        AlertDialog(
            onDismissRequest = { deletingOption = null },
            title = { Text("Delete \"${deleteTarget.label}\"?") },
            text = { Text("All transactions in this category will be moved to Others.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCustomCategory(deleteTarget.id)
                    deletingOption = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingOption = null }) { Text(CategoryPickerConstants.CANCEL) }
            }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    selectedType: String?,
    selectedCategory: String?,
    customCategories: List<CustomCategory> = emptyList(),
    minAmount: String,
    maxAmount: String,
    onApply: (type: String?, category: String?, min: Double?, max: Double?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(selectedType) }
    var category by remember { mutableStateOf(selectedCategory) }
    var min by remember { mutableStateOf(minAmount) }
    var max by remember { mutableStateOf(maxAmount) }
    val filterOptions = remember(customCategories) { CategoryCatalogHelper.mergeOptions(customCategories) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", "debit" to "Debit", "credit" to "Credit").forEach { (value, label) ->
                    FilterChip(
                        selected = type == value,
                        onClick = { type = value },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("All") }
                )
                filterOptions.forEach { opt ->
                    FilterChip(
                        selected = category == opt.id,
                        onClick = { category = opt.id },
                        label = { Text("${opt.icon} ${opt.label}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Amount Range", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = min,
                    onValueChange = { min = it },
                    label = { Text("Min") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = max,
                    onValueChange = { max = it },
                    label = { Text("Max") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onReset(); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(
                    onClick = {
                        onApply(type, category, min.toDoubleOrNull(), max.toDoubleOrNull())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    currentSort: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "date_desc" to "Date (Newest)",
        "date_asc" to "Date (Oldest)",
        "amount_desc" to "Amount (Highest)",
        "amount_asc" to "Amount (Lowest)",
        "merchant_asc" to "Merchant (A-Z)"
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Sort By", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value); onDismiss() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentSort == value, onClick = { onSelect(value); onDismiss() })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    reminder: Reminder?,
    onDismiss: () -> Unit,
    onEdit: (Reminder) -> Unit = {},
    onDelete: (Reminder) -> Unit = {}
) {
    if (reminder == null) return
    val ext = LocalExtendedColors.current
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reminder Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ext.text)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(reminder.merchant, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ext.text)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "₹${formatter.format(reminder.amount.toLong())}",
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ext.text
            )
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow("Category", reminder.category.replaceFirstChar { it.uppercase() })
            DetailRow("Type", reminder.type.replaceFirstChar { it.uppercase() })
            DetailRow("Frequency", reminder.frequency.replaceFirstChar { it.uppercase() })
            DetailRow("Reminder Date", reminder.reminder_date)
            reminder.last_transaction_date?.let { DetailRow("Last Transaction", it) }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onDelete(reminder); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { onEdit(reminder); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text("Edit")
                }
            }
        }
    }
}

private fun String.graphemeClusters(): List<String> {
    val boundary = BreakIterator.getCharacterInstance()
    boundary.setText(this)
    val clusters = mutableListOf<String>()
    var start = boundary.first()
    var end = boundary.next()
    while (end != BreakIterator.DONE) {
        clusters.add(substring(start, end))
        start = end
        end = boundary.next()
    }
    return clusters
}
