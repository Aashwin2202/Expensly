package com.fintrackai.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.data.repository.WrongSmsRepository
import com.fintrackai.domain.model.Transaction
import com.fintrackai.domain.transactions.TransactionLinkConstants
import com.fintrackai.ui.components.MerchantCountInStatsChangeDialog
import com.fintrackai.ui.components.TransactionCardConstants
import com.fintrackai.ui.components.TransactionDeleteConfirmDialog
import com.fintrackai.ui.theme.LocalExtendedColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.floor

enum class MerchantRenameMode { REPLACE_ALL, REPLACE_ONE }

data class TransactionDetailUiState(
    val transaction: Transaction? = null,
    val linkedPeers: List<Transaction> = emptyList(),
    val loading: Boolean = true,
    val done: Boolean = false,
    /** Non-null when we're waiting for the user to pick a rename mode. */
    val pendingSave: Transaction? = null,
    /** Non-null when saved tx has countInStats change and merchant has multiple transactions. */
    val pendingCountInStats: PendingMerchantCountInStatsChange? = null
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val wrongSmsRepo: WrongSmsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        val rawId = savedStateHandle.get<String>("transactionId").orEmpty()
        val txId = URLDecoder.decode(rawId, StandardCharsets.UTF_8.name())
        viewModelScope.launch {
            val tx = repo.getTransactionById(txId)
            val peers = if (tx?.linkGroupId != null) {
                if (tx.linkSuppressed) {
                    listOfNotNull(repo.getPrimaryForGroup(tx.linkGroupId!!))
                } else {
                    repo.getSecondariesForGroup(tx.linkGroupId!!)
                }
            } else emptyList()
            _uiState.value = TransactionDetailUiState(
                transaction = tx,
                linkedPeers = peers,
                loading = false
            )
        }
    }

    /**
     * Called when the user taps Save. If the merchant name changed, sets [pendingSave] so the
     * UI can show the rename-mode dialog. Otherwise saves immediately.
     */
    fun requestSave(tx: Transaction) {
        val original = _uiState.value.transaction
        val merchantChanged = original != null &&
            original.merchant.isNotBlank() &&
            tx.merchant.isNotBlank() &&
            !original.merchant.trim().equals(tx.merchant.trim(), ignoreCase = true)
        if (merchantChanged) {
            _uiState.value = _uiState.value.copy(pendingSave = tx)
        } else {
            commitSave(tx, MerchantRenameMode.REPLACE_ONE)
        }
    }

    fun dismissRenamePicker() {
        _uiState.value = _uiState.value.copy(pendingSave = null)
    }

    fun confirmSave(mode: MerchantRenameMode) {
        val tx = _uiState.value.pendingSave ?: return
        _uiState.value = _uiState.value.copy(pendingSave = null)
        commitSave(tx, mode)
    }

    private fun commitSave(tx: Transaction, mode: MerchantRenameMode) {
        viewModelScope.launch {
            val original = _uiState.value.transaction
            when (mode) {
                MerchantRenameMode.REPLACE_ALL -> {
                    if (original != null) {
                        repo.bulkRenameMerchant(original.merchant.trim(), tx.merchant.trim())
                    }
                    repo.updateTransaction(tx)
                }
                MerchantRenameMode.REPLACE_ONE -> {
                    repo.updateTransaction(tx)
                }
            }
            val countInStatsChanged = original != null && tx.countInStats != original.countInStats && tx.merchant.isNotBlank()
            if (countInStatsChanged) {
                val n = repo.getMerchantTransactionCount(tx.merchant)
                if (n > 1) {
                    _uiState.value = _uiState.value.copy(
                        pendingCountInStats = PendingMerchantCountInStatsChange(tx, tx.countInStats, n)
                    )
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(done = true)
        }
    }

    fun dismissCountInStatsPicker() {
        _uiState.value = _uiState.value.copy(pendingCountInStats = null, done = true)
    }

    fun applyCountInStatsToAll() {
        val pending = _uiState.value.pendingCountInStats ?: return
        _uiState.value = _uiState.value.copy(pendingCountInStats = null)
        viewModelScope.launch {
            repo.updateMerchantTransactionsCountInStats(pending.transaction.merchant, pending.countInStats)
            _uiState.value = _uiState.value.copy(done = true)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.deleteTransactionById(id)
            _uiState.value = _uiState.value.copy(done = true)
        }
    }

    fun reportWrongDetection(reason: String, comments: String) {
        val tx = _uiState.value.transaction ?: return
        val rawSms = tx.originalSms ?: return
        viewModelScope.launch {
            wrongSmsRepo.report(
                rawSms = rawSms,
                smsSender = tx.smsSender,
                reason = reason,
                comments = comments,
                detectedMerchant = tx.merchant.takeIf { it.isNotBlank() },
                detectedAmount = tx.amount,
                detectedType = tx.type.takeIf { it.isNotBlank() },
                detectedCategory = tx.category.takeIf { it.isNotBlank() },
                detectedDate = tx.date.takeIf { it.isNotBlank() },
                detectedTime = tx.time.takeIf { it.isNotBlank() },
                detectedAccounts = tx.accounts.takeIf { it.isNotBlank() },
                detectedReference = tx.reference
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    if (state.pendingSave != null) {
        MerchantRenameDialog(
            originalName = state.transaction?.merchant.orEmpty(),
            newName = state.pendingSave!!.merchant,
            onDismiss = { viewModel.dismissRenamePicker() },
            onReplaceAll = { viewModel.confirmSave(MerchantRenameMode.REPLACE_ALL) },
            onReplaceOne = { viewModel.confirmSave(MerchantRenameMode.REPLACE_ONE) }
        )
    }

    state.pendingCountInStats?.let { pending ->
        MerchantCountInStatsChangeDialog(
            merchant = pending.transaction.merchant.trim().ifBlank { "—" },
            countInStats = pending.countInStats,
            txType = pending.transaction.type,
            merchantOccurrenceCount = pending.merchantOccurrenceCount,
            onDismiss = { viewModel.dismissCountInStatsPicker() },
            onThisTransactionOnly = { viewModel.dismissCountInStatsPicker() },
            onAllForMerchant = { viewModel.applyCountInStatsToAll() }
        )
    }

    if (showDeleteConfirm && state.transaction != null) {
        TransactionDeleteConfirmDialog(
            merchantLabel = state.transaction!!.merchant.ifBlank { "—" },
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete(state.transaction!!.id)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = { Text("Transaction", fontWeight = FontWeight.SemiBold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.transaction == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) { Text("Transaction not found.", color = ext.textSecondary) }
            }
            else -> {
                TransactionDetailForm(
                    transaction = state.transaction!!,
                    linkedPeers = state.linkedPeers,
                    modifier = Modifier.padding(padding),
                    onSave = { viewModel.requestSave(it) },
                    onDelete = { showDeleteConfirm = true },
                    onReportWrong = { reason, comments ->
                        viewModel.reportWrongDetection(reason, comments)
                        scope.launch { snackbarHostState.showSnackbar("Thank you for your feedback!") }
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionDetailForm(
    transaction: Transaction,
    linkedPeers: List<Transaction>,
    modifier: Modifier = Modifier,
    onSave: (Transaction) -> Unit,
    onDelete: () -> Unit,
    onReportWrong: (reason: String, comments: String) -> Unit
) {
    val ext = LocalExtendedColors.current
    val scroll = rememberScrollState()

    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant) }
    var amountStr by remember(transaction.id) { mutableStateOf(editableAmountStr(transaction.amount)) }
    var type by remember(transaction.id) { mutableStateOf(transaction.type) }
    var date by remember(transaction.id) { mutableStateOf(transaction.date) }
    var time by remember(transaction.id) { mutableStateOf(transaction.time) }
    var accounts by remember(transaction.id) { mutableStateOf(transaction.accounts) }
    var reference by remember(transaction.id) { mutableStateOf(transaction.reference.orEmpty()) }
    var countInStats by remember(transaction.id) { mutableStateOf(transaction.countInStats) }
    var error by remember(transaction.id) { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    if (showReportDialog) {
        ReportWrongDetectionDialog(
            onDismiss = { showReportDialog = false },
            onSend = { reason, comments ->
                onReportWrong(reason, comments)
                showReportDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it; error = null },
            label = { Text("Merchant") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amountStr,
            onValueChange = { v ->
                amountStr = formatAmountInput(v.filter { c -> c.isDigit() || c == '.' || c == ',' })
                error = null
            },
            label = { Text("Amount (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text("Type", style = MaterialTheme.typography.labelLarge, color = ext.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == "debit",
                onClick = { type = "debit"; error = null },
                label = { Text("Debit") }
            )
            FilterChip(
                selected = type == "credit",
                onClick = { type = "credit"; error = null },
                label = { Text("Credit") }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
        if (transaction.linkGroupId != null) {
            Spacer(Modifier.height(8.dp))
            val peerFmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
            val linkAccent = MaterialTheme.colorScheme.tertiary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, linkAccent.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = linkAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            TransactionCardConstants.LINKED_BADGE.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = linkAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (linkedPeers.isEmpty()) {
                        Text("…", style = MaterialTheme.typography.bodySmall, color = ext.text)
                    } else {
                        linkedPeers.forEachIndexed { idx, peer ->
                            val peerSign = if (peer.type == "debit") "-" else "+"
                            val typeWord = if (peer.type == "debit") "expense" else "credit"
                            Text(
                                "${TransactionLinkConstants.LINKED_WITH_PREFIX} ${peer.merchant.ifBlank { "—" }} " +
                                    "($typeWord $peerSign₹${peerFmt.format(peer.amount.toLong())})",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.text
                            )
                            if (idx < linkedPeers.lastIndex) Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = date,
            onValueChange = { date = it; error = null },
            label = { Text("Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = time,
            onValueChange = { time = it; error = null },
            label = { Text("Time (HH:MM)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = accounts,
            onValueChange = { accounts = it; error = null },
            label = { Text("Account") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = reference,
            onValueChange = { reference = it; error = null },
            label = { Text("Reference (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (transaction.originalSms != null || transaction.smsSender != null) {
            Spacer(Modifier.height(20.dp))
            Text("Original SMS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ext.text)
            transaction.smsSender?.takeIf { it.isNotBlank() }?.let {
                Text("From: $it", style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                Spacer(Modifier.height(4.dp))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (merchant.isBlank()) { error = "Merchant is required"; return@Button }
                val normalized = amountStr.replace(",", "").trim()
                val amt = normalized.toDoubleOrNull()
                if (amt == null || amt <= 0) { error = "Enter a valid amount"; return@Button }
                if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(date.trim())) {
                    error = "Use date format YYYY-MM-DD"; return@Button
                }
                onSave(
                    transaction.copy(
                        merchant = merchant.trim(),
                        amount = amt,
                        type = type,
                        date = date.trim(),
                        time = time.trim().ifBlank { "00:00" },
                        accounts = accounts.trim(),
                        reference = reference.trim().ifBlank { null },
                        countInStats = countInStats
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Save") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Delete") }
        if (transaction.originalSms != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showReportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Report Wrong Detection") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MerchantRenameDialog(
    originalName: String,
    newName: String,
    onDismiss: () -> Unit,
    onReplaceAll: () -> Unit,
    onReplaceOne: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Merchant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You renamed \"$originalName\" to \"$newName\". How would you like to apply this?",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReplaceAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Replace All")
                }
                OutlinedButton(
                    onClick = onReplaceOne,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Replace This Only")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun editableAmountStr(amount: Double): String {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return if (floor(amount) == amount) fmt.format(amount.toLong())
    else {
        val intPart = amount.toLong()
        val decPart = amount.toString().substringAfter('.')
        fmt.format(intPart) + "." + decPart
    }
}

private fun formatAmountInput(v: String): String {
    val raw = v.replace(",", "")
    val dotIndex = raw.indexOf('.')
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return if (dotIndex >= 0) {
        val intPart = raw.substring(0, dotIndex)
        val decPart = raw.substring(dotIndex)
        (intPart.toLongOrNull()?.let { fmt.format(it) } ?: intPart) + decPart
    } else {
        raw.toLongOrNull()?.let { fmt.format(it) } ?: raw
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
private fun ReportWrongDetectionDialog(
    onDismiss: () -> Unit,
    onSend: (reason: String, comments: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Wrong Detection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "What was detected incorrectly?",
                    style = MaterialTheme.typography.bodyMedium
                )
                wrongDetectionReasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
