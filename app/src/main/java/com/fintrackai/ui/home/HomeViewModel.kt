package com.fintrackai.ui.home

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.data.repository.WrongSmsRepository
import com.fintrackai.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val monthlyStats: MonthlyStats = MonthlyStats(0.0, 0.0, 0.0),
    val recentTransactions: List<Transaction> = emptyList(),
    val dailyExpenseDays: List<DailyExpenseDay> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
    val knownAccountOptions: List<String> = emptyList(),
    val topCategories: List<CategoryStat> = emptyList(),
    val topMerchants: List<MerchantStat> = emptyList(),
    val showDailyChart: Boolean = true,
    val loading: Boolean = true,
    val smsRescanRunning: Boolean = false,
    val smsRescanPercent: Int = 0,
    val smsRescanDone: Boolean = false,
    val smsRescanSaved: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val wrongSmsRepo: WrongSmsRepository,
    private val prefs: PreferencesManager,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var initialSummaryLoaded = false

    init {
        // Combined collector for transaction-related state
        viewModelScope.launch {
            repo.getAllTransactionsFlow().collect { txns ->
                val recent = txns.take(HomeConstants.RECENT_TRANSACTIONS_LIMIT)
                
                // Only compute account options if they haven't been loaded or on major changes
                // Optimization: In a real app, this should be a distinct DAO query.
                val accountOptions = if (_uiState.value.knownAccountOptions.isEmpty()) {
                    txns.mapNotNull { tx -> tx.accounts.trim().takeIf { it.isNotEmpty() } }.distinct().sorted()
                } else _uiState.value.knownAccountOptions

                _uiState.value = _uiState.value.copy(
                    recentTransactions = recent,
                    knownAccountOptions = accountOptions
                )
                recomputeHomeAggregates(showLoadingOverlay = !initialSummaryLoaded)
            }
        }

        viewModelScope.launch {
            repo.getRemindersFlow().collect { reminders ->
                _uiState.value = _uiState.value.copy(reminders = reminders)
            }
        }

        viewModelScope.launch {
            repo.getCustomCategoriesFlow().collect { custom ->
                _uiState.value = _uiState.value.copy(customCategories = custom)
            }
        }

        viewModelScope.launch {
            refreshRecurringIfNewMonth()
            autoMarkRemindersPaid()
        }
    }

    fun toggleDailyChart() {
        _uiState.value = _uiState.value.copy(showDailyChart = !_uiState.value.showDailyChart)
    }

    fun refreshDailySummary() {
        viewModelScope.launch {
            recomputeHomeAggregates(showLoadingOverlay = !initialSummaryLoaded)
            refreshRecurringIfNewMonth()
            autoMarkRemindersPaid()
        }
    }

    private suspend fun autoMarkRemindersPaid() {
        val cal = Calendar.getInstance()
        val monthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        repo.autoMarkRemindersIfTransactionFound(monthKey)
    }

    private suspend fun refreshRecurringIfNewMonth() {
        val cal = Calendar.getInstance()
        val currentMonth = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val lastComputed = prefs.getRecurringLastComputedMonth()
        if (lastComputed == currentMonth) return
        val recurring = repo.getRecurringTransactions()
        _uiState.value = _uiState.value.copy(recurringTransactions = recurring)
        prefs.setRecurringLastComputedMonth(currentMonth)
    }

    private suspend fun recomputeHomeAggregates(showLoadingOverlay: Boolean) {
        if (showLoadingOverlay) {
            _uiState.value = _uiState.value.copy(loading = true)
        }
        try {
            coroutineScope {
                val cal = Calendar.getInstance()
                val monthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

                // Fetch all heavy data in parallel
                val statsDeferred = async { repo.getMonthlyStats() }
                val dailyMapDeferred = async { repo.getDailyDebitTotalsForMonth(monthKey) }
                val topCategoriesDeferred = async { repo.getCategoryStats(monthKey).take(3) }
                val topMerchantsDeferred = async { repo.getMerchantStats(monthKey).take(3) }

                val stats = statsDeferred.await()
                val dailyMap = dailyMapDeferred.await()
                val dailyDays = HomeDailyExpenseHelper.buildDaysForMonth(monthKey, dailyMap)
                val topCategories = topCategoriesDeferred.await()
                val topMerchants = topMerchantsDeferred.await()

                initialSummaryLoaded = true
                _uiState.value = _uiState.value.copy(
                    monthlyStats = stats,
                    dailyExpenseDays = dailyDays,
                    topCategories = topCategories,
                    topMerchants = topMerchants,
                    loading = false
                )
            }
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    fun dismissRecurring(merchant: String, amount: Double) {
        analytics.logReminderRecurringDismissed()
        viewModelScope.launch {
            repo.dismissRecurring(merchant, amount)
            val updated = repo.getRecurringTransactions()
            _uiState.value = _uiState.value.copy(recurringTransactions = updated)
        }
    }

    fun deleteReminder(id: String) {
        analytics.logReminderDeleted()
        viewModelScope.launch {
            repo.deleteReminder(id)
        }
    }

    fun markReminderPaid(id: String) {
        analytics.logReminderMarkedPaid()
        viewModelScope.launch {
            repo.markReminderPaid(id, java.time.LocalDate.now().toString())
        }
    }

    suspend fun countTransactionsForMerchant(merchant: String): Int {
        val m = merchant.trim()
        if (m.isBlank()) return 0
        return repo.getMerchantTransactionCount(m)
    }

    fun updateTransactionCategory(tx: Transaction, category: String, mode: MerchantCategoryApplyMode) {
        analytics.logTransactionCategoryChanged(
            from = tx.category,
            to = category,
            appliedToAll = mode == MerchantCategoryApplyMode.ALL_FOR_MERCHANT
        )
        val isInvestment = category.equals("investment", ignoreCase = true)
        viewModelScope.launch {
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY -> {
                    val updated = tx.copy(
                        category = category,
                        countInStats = if (isInvestment) false else tx.countInStats
                    )
                    repo.updateTransaction(updated)
                    if (tx.merchant.isNotBlank()) {
                        repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    }
                }
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT -> {
                    repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    repo.updateMerchantTransactionsCategory(tx.merchant, category)
                    if (isInvestment) {
                        repo.updateMerchantTransactionsCountInStats(tx.merchant, false)
                    }
                }
            }
        }
    }

    fun updateTransactionCountInStats(tx: Transaction, countInStats: Boolean, mode: MerchantCategoryApplyMode) {
        viewModelScope.launch {
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY -> {
                    repo.updateTransaction(tx.copy(countInStats = countInStats))
                }
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT -> {
                    repo.updateMerchantTransactionsCountInStats(tx.merchant, countInStats)
                }
            }
        }
    }

    fun saveTransaction(tx: Transaction) {
        viewModelScope.launch {
            repo.updateTransaction(tx)
            if (tx.merchant.isNotBlank()) {
                repo.saveMerchantCategoryMapping(tx.merchant, tx.category)
            }
        }
    }

    fun renameMerchant(originalName: String, newName: String, applyToAll: Boolean) {
        analytics.logTransactionMerchantRenamed(applyToAll)
        viewModelScope.launch {
            if (applyToAll) {
                repo.bulkRenameMerchant(originalName, newName)
            } else {
                repo.saveMerchantNameMapping(originalName, newName)
            }
        }
    }

    fun deleteTransaction(id: String) {
        analytics.logTransactionDeleted()
        viewModelScope.launch {
            repo.deleteTransactionById(id)
        }
    }

    fun reportWrongDetection(tx: Transaction, reason: String, comments: String) {
        analytics.logTransactionWrongDetectionReported()
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

    suspend fun getSecondariesForGroup(groupId: String): List<Transaction> = repo.getSecondariesForGroup(groupId)

    suspend fun getPrimaryForGroup(groupId: String): Transaction? = repo.getPrimaryForGroup(groupId)

    fun unsplitPair(visibleRowId: String) {
        viewModelScope.launch {
            repo.unlinkTransactionPair(visibleRowId)
            recomputeHomeAggregates(showLoadingOverlay = false)
        }
    }

    fun saveCustomCategory(name: String, emoji: String, onSaved: (String) -> Unit) {
        analytics.logCustomCategoryCreated()
        viewModelScope.launch {
            val id = repo.addCustomCategoryFromInput(name, emoji) ?: return@launch
            onSaved(id)
        }
    }

    fun deleteCustomCategory(id: String) {
        analytics.logCustomCategoryDeleted()
        viewModelScope.launch { repo.deleteCustomCategoryAndRecategorize(id) }
    }

    fun editCustomCategory(id: String, name: String, emoji: String) {
        analytics.logCustomCategoryEdited()
        viewModelScope.launch { repo.editCustomCategory(id, name, emoji) }
    }

    private var rescanJob: Job? = null

    fun rescanSmsFromHome(contentResolver: ContentResolver) {
        if (_uiState.value.smsRescanRunning) return
        analytics.logSmsRescanStarted()
        _uiState.value = _uiState.value.copy(smsRescanRunning = true, smsRescanPercent = 0, smsRescanDone = false)
        rescanJob = viewModelScope.launch {
            try {
                val range = repo.getDateRange()
                val since = startOfLocalDayMillisOrNull(range.maxDate)
                val (_, saved) = repo.scanSmsInboxAndSave(
                    contentResolver,
                    sinceMillisInclusive = since,
                    onProgress = { scanned, total ->
                        val pct = if (total > 0) ((scanned * 90) / total) else 90
                        _uiState.value = _uiState.value.copy(smsRescanPercent = pct)
                    }
                )
                val cur = _uiState.value.smsRescanPercent
                for (p in (cur + 1)..99) {
                    _uiState.value = _uiState.value.copy(smsRescanPercent = p)
                    kotlinx.coroutines.delay(30L)
                }
                prefs.setSmsPermissionGranted()
                analytics.logSmsRescanCompleted(0, saved)
                _uiState.value = _uiState.value.copy(
                    smsRescanRunning = false,
                    smsRescanPercent = 100,
                    smsRescanDone = true,
                    smsRescanSaved = saved
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(smsRescanRunning = false, smsRescanPercent = 0)
            }
        }
    }

    fun rescanFullInboxFromHome(contentResolver: ContentResolver) {
        if (_uiState.value.smsRescanRunning) return
        analytics.logSmsRescanStarted()
        _uiState.value = _uiState.value.copy(smsRescanRunning = true, smsRescanPercent = 0, smsRescanDone = false)
        rescanJob = viewModelScope.launch {
            try {
                val (_, saved) = repo.scanSmsInboxAndSave(
                    contentResolver,
                    sinceMillisInclusive = null,
                    onProgress = { scanned, total ->
                        val pct = if (total > 0) ((scanned * 90) / total) else 90
                        _uiState.value = _uiState.value.copy(smsRescanPercent = pct)
                    }
                )
                val cur = _uiState.value.smsRescanPercent
                for (p in (cur + 1)..99) {
                    _uiState.value = _uiState.value.copy(smsRescanPercent = p)
                    kotlinx.coroutines.delay(30L)
                }
                prefs.setSmsPermissionGranted()
                analytics.logSmsRescanCompleted(0, saved)
                _uiState.value = _uiState.value.copy(
                    smsRescanRunning = false,
                    smsRescanPercent = 100,
                    smsRescanDone = true,
                    smsRescanSaved = saved
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(smsRescanRunning = false, smsRescanPercent = 0)
            }
        }
    }

    fun clearSmsRescanDone() {
        _uiState.value = _uiState.value.copy(smsRescanDone = false)
    }

    private fun startOfLocalDayMillisOrNull(dateStr: String?): Long? {
        if (dateStr == null || dateStr == "N/A") return null
        return try {
            val date = java.time.LocalDate.parse(dateStr)
            date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
    }
}
