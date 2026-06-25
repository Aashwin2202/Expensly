package com.fintrackai.ui.weeklysummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.data.local.db.CategoryStatRow
import com.fintrackai.data.local.db.DayTotalRow
import com.fintrackai.data.local.db.TransactionDao
import com.fintrackai.data.local.db.TransactionEntity
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.MerchantCategoryApplyMode
import com.fintrackai.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import com.fintrackai.analytics.AnalyticsHelper
import javax.inject.Inject

data class WeeklySummaryUiState(
    val loading: Boolean = true,
    val weekStart: String = "",
    val weekEnd: String = "",
    // Per-day totals Mon–Sun
    val dailyTotals: List<DayTotalRow> = emptyList(),
    val totalSpend: Double = 0.0,
    val txCount: Int = 0,
    val merchantCount: Int = 0,
    val topCategory: String? = null,
    val topMerchant: String? = null,
    val topMerchantAmount: Double = 0.0,
    val uncategorizedSpend: Double = 0.0,
    val categoryBreakdown: List<CategoryStatRow> = emptyList(),
    // % change vs prior week (null if no prior data)
    val percentChange: Double? = null,
    // Prior week total for display
    val priorWeekTotal: Double = 0.0,
    val hasData: Boolean = false,
    // Up to 3 uncategorized transactions for the preview
    val uncategorizedTransactions: List<Transaction> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList()
)

@HiltViewModel
class WeeklySummaryViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val dao: TransactionDao,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklySummaryUiState())
    val uiState: StateFlow<WeeklySummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getCustomCategoriesFlow().collect { custom ->
                _uiState.value = _uiState.value.copy(customCategories = custom)
            }
        }
    }

    /**
     * Load summary for the week containing [weekStartDate].
     * If null, defaults to the most recent completed week (Mon–Sun).
     */
    private fun TransactionEntity.toDomain() = Transaction(
        id = id, merchant = merchant, amount = amount, type = type,
        category = category, date = date, time = time, accounts = accounts,
        reference = reference, countInStats = countInStats == 1,
        originalSms = originalSms, smsSender = smsSender,
        smsDedupeHash = smsDedupeHash,
        linkGroupId = linkGroupId,
        linkSuppressed = linkSuppressed == 1,
        linkStashedAmount = linkStashedAmount,
        linkStashedType = linkStashedType
    )

    fun load(weekStartDate: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)

            val today = LocalDate.now()

            val thisMonday: LocalDate
            val thisSunday: LocalDate

            if (weekStartDate != null) {
                thisMonday = LocalDate.parse(weekStartDate)
                thisSunday = thisMonday.plusDays(6)
            } else {
                // Default: most recent completed week (last Mon–Sun)
                val lastSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val lastMonday = lastSunday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                thisMonday = lastMonday
                thisSunday = lastSunday
            }

            val startStr = thisMonday.toString()
            val endStr = thisSunday.toString()

            val dailyTotals = dao.getDailyTotalsForRange(startStr, endStr)
            val txCount = dao.getTxCountInRange(startStr, endStr)
            val merchantCount = dao.getDistinctMerchantCountInRange(startStr, endStr)
            val topCatRow = dao.getTopCategoryInRange(startStr, endStr)
            val topMerchantRow = dao.getTopMerchantInRange(startStr, endStr)
            val uncatSpend = dao.getUncategorizedSpendInRange(startStr, endStr)
            val categories = dao.getAllCategoryStatsInRange(startStr, endStr)
            val uncatTransactions = dao.getUncategorizedTransactionsInRange(startStr, endStr, 3)
                .map { it.toDomain() }

            val totalSpend = dailyTotals.sumOf { it.total }

            // Prior week for % change
            val priorSunday = thisMonday.minusDays(1)
            val priorMonday = priorSunday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val priorStats = dao.getWeeklySummaryStats(priorMonday.toString(), priorSunday.toString())
            val percentChange = if (priorStats.totalSpend > 0) {
                ((totalSpend - priorStats.totalSpend) / priorStats.totalSpend) * 100.0
            } else null

            _uiState.value = _uiState.value.copy(
                loading = false,
                weekStart = startStr,
                weekEnd = endStr,
                dailyTotals = dailyTotals,
                totalSpend = totalSpend,
                txCount = txCount,
                merchantCount = merchantCount,
                topCategory = topCatRow?.category?.replaceFirstChar { it.uppercase() },
                topMerchant = topMerchantRow?.merchant,
                topMerchantAmount = topMerchantRow?.amount ?: 0.0,
                uncategorizedSpend = uncatSpend,
                categoryBreakdown = categories,
                percentChange = percentChange,
                priorWeekTotal = priorStats.totalSpend,
                hasData = txCount > 0,
                uncategorizedTransactions = uncatTransactions
            )
        }
    }

    fun saveTransaction(tx: Transaction) {
        viewModelScope.launch { repo.updateTransaction(tx) }
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

    fun updateTransactionCategory(tx: Transaction, category: String, mode: MerchantCategoryApplyMode) {
        viewModelScope.launch {
            when (mode) {
                MerchantCategoryApplyMode.THIS_TRANSACTION_ONLY -> {
                    repo.updateTransaction(tx.copy(category = category))
                    if (tx.merchant.isNotBlank()) {
                        repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    }
                }
                MerchantCategoryApplyMode.ALL_FOR_MERCHANT -> {
                    repo.saveMerchantCategoryMapping(tx.merchant, category, previousCategory = tx.category)
                    repo.updateMerchantTransactionsCategory(tx.merchant, category)
                }
            }
            // Refresh uncategorized list after update
            val weekStart = _uiState.value.weekStart
            val weekEnd = _uiState.value.weekEnd
            if (weekStart.isNotEmpty()) {
                val updated = dao.getUncategorizedTransactionsInRange(weekStart, weekEnd, 3)
                    .map { it.toDomain() }
                _uiState.value = _uiState.value.copy(uncategorizedTransactions = updated)
            }
        }
    }

    suspend fun countTransactionsForMerchant(merchant: String): Int {
        val m = merchant.trim()
        if (m.isBlank()) return 0
        return repo.getMerchantTransactionCount(m)
    }

    fun saveCustomCategory(name: String, emoji: String, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.addCustomCategoryFromInput(name, emoji) ?: return@launch
            onSaved(id)
        }
    }

    fun deleteCustomCategory(id: String) {
        viewModelScope.launch { repo.deleteCustomCategoryAndRecategorize(id) }
    }

    fun editCustomCategory(id: String, name: String, emoji: String) {
        viewModelScope.launch { repo.editCustomCategory(id, name, emoji) }
    }

    fun logWeeklyInsightCardClicked() = analytics.logWeeklyInsightCardClicked()
}
