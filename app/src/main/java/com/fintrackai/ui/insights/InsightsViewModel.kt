package com.fintrackai.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.insights.DailySummaryGenerator
import com.fintrackai.domain.insights.LocalInsightGenerator
import com.fintrackai.notification.NotificationScheduler
// TODO: Re-enable for AI insights
// import com.fintrackai.domain.insights.InsightGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightItem(
    val id: String,
    val title: String,
    val loadingText: String
)

data class InsightsUiState(
    val insights: Map<String, String?> = mapOf(
        "daily" to null, "weekly" to null, "merchant" to null,
        "time" to null, "velocity" to null
    ),
    val loading: Map<String, Boolean> = mapOf(
        "daily" to false, "weekly" to false, "merchant" to false,
        "time" to false, "velocity" to false
    ),
    val currentIndex: Int = 0,
    val savedInsights: Set<String> = emptySet(),
    val showNotificationPrompt: Boolean = false,
    val notificationPermissionGranted: Boolean = false
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val localInsightGenerator: LocalInsightGenerator,
    private val notificationScheduler: NotificationScheduler,
    private val analytics: AnalyticsHelper
    // TODO: Re-enable for AI insights
    // private val insightGenerator: InsightGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    val insightItems = listOf(
        InsightItem("daily", "Daily Summary", "Preparing your daily summary..."),
        InsightItem("velocity", "Spending Velocity", "Predicting month-end spend..."),
        InsightItem("weekly", "Weekly Change", "Comparing this week..."),
        InsightItem("merchant", "Merchant Pattern", "Analyzing spending patterns..."),
        InsightItem("time", "Time Analysis", "Checking time-based patterns...")
    )

    fun refreshAll() {
        _uiState.value = InsightsUiState(
            currentIndex = _uiState.value.currentIndex,
            showNotificationPrompt = _uiState.value.showNotificationPrompt,
            notificationPermissionGranted = _uiState.value.notificationPermissionGranted
        )
        val item = insightItems.getOrNull(_uiState.value.currentIndex) ?: return
        loadInsight(item.id)
    }

    fun onScreenEntered(isPermissionGranted: Boolean) {
        _uiState.value = _uiState.value.copy(
            notificationPermissionGranted = isPermissionGranted,
            showNotificationPrompt = !isPermissionGranted
        )
        if (!isPermissionGranted) analytics.logInsightNotificationPromptShown()
    }

    fun dismissNotificationPrompt() {
        analytics.logInsightNotificationPromptDismissed()
        _uiState.value = _uiState.value.copy(showNotificationPrompt = false)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) analytics.logNotificationPermissionGranted()
        else analytics.logNotificationPermissionDenied()
        _uiState.value = _uiState.value.copy(
            notificationPermissionGranted = granted,
            showNotificationPrompt = false
        )
        if (granted) {
            // Cancel existing work and re-enqueue with fresh delays computed from now,
            // since the original KEEP-policy work was scheduled before permission was granted.
            notificationScheduler.reschedule()
        }
    }

    fun loadInsight(id: String) {
        if (_uiState.value.insights[id] != null || _uiState.value.loading[id] == true) return
        _uiState.value = _uiState.value.copy(
            loading = _uiState.value.loading + (id to true)
        )
        viewModelScope.launch {
            try {
                val text = when (id) {
                    "daily" -> {
                        val comparison = repo.getDailySpendingComparison()
                        val budget = repo.getMonthlyBudget()
                        val stats = repo.getMonthlyStats()
                        val cal = java.util.Calendar.getInstance()
                        val daysLeft = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - cal.get(java.util.Calendar.DAY_OF_MONTH)
                        val budgetCtx = if (budget != null) {
                            DailySummaryGenerator.BudgetContext(budget - stats.expense, daysLeft)
                        } else null
                        DailySummaryGenerator.getDailySummaryText(
                            comparison.today,
                            comparison.pastWeeks,
                            budgetCtx
                        )
                    }
                    "weekly" -> {
                        val wcc = repo.getWeeklyCategoryComparison()
                        localInsightGenerator.generateWeeklyChangeInsight(
                            wcc.thisWeekCategories, wcc.lastWeekCategories, wcc.topMerchant
                        )
                        // TODO: Re-enable for AI insights
                        // insightGenerator.generateWeeklyChangeInsight(
                        //     wcc.thisWeekCategories, wcc.lastWeekCategories, wcc.topMerchant
                        // )
                    }
                    "merchant" -> {
                        val repeatedMerchants = repo.getRepeatedMerchantsThisMonth()
                        val cal = java.util.Calendar.getInstance()
                        val monthKey = String.format("%04d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
                        val merchantStats = repo.getMerchantStats(monthKey)
                        val topByAmount = merchantStats.take(5).map {
                            com.fintrackai.domain.model.RepeatedMerchant(it.merchant, it.transactionCount, it.amount)
                        }
                        localInsightGenerator.generateMerchantInsight(repeatedMerchants, topByAmount)
                        // TODO: Re-enable for AI insights
                        // insightGenerator.generateRepeatedMerchantInsight(repeatedMerchants)
                    }
                    "time" -> {
                        val patterns = repo.getTimeBasedPatterns()
                        localInsightGenerator.generateTimeAnalysisInsight(patterns)
                        // TODO: Re-enable for AI insights
                        // insightGenerator.generateTimeBasedPatternInsight(patterns)
                    }
                    "velocity" -> {
                        val data = repo.getVelocityData()
                        localInsightGenerator.generateVelocityInsight(data)
                        // TODO: Re-enable for AI insights
                        // insightGenerator.generateVelocityInsight(
                        //     data.spentThisMonth, data.daysElapsed,
                        //     data.daysInMonth, data.previousMonthsTotals
                        // )
                    }
                    else -> "No insight available"
                }
                _uiState.value = _uiState.value.copy(
                    insights = _uiState.value.insights + (id to text),
                    loading = _uiState.value.loading + (id to false)
                )
            } catch (e: Exception) {
                analytics.logInsightLoadFailed(id, e.message ?: "unknown")
                _uiState.value = _uiState.value.copy(
                    insights = _uiState.value.insights + (id to "Failed to load insight: ${e.message}"),
                    loading = _uiState.value.loading + (id to false)
                )
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
        val item = insightItems.getOrNull(index) ?: return
        analytics.logInsightViewed(item.id)
        loadInsight(item.id)
    }

    fun toggleSaved(id: String) {
        val current = _uiState.value.savedInsights
        val nowSaved = !current.contains(id)
        if (nowSaved) analytics.logInsightSaved(id) else analytics.logInsightUnsaved(id)
        _uiState.value = _uiState.value.copy(
            savedInsights = if (nowSaved) current + id else current - id
        )
    }
}
