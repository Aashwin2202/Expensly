package com.fintrackai.ui.wrapped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.wrapped.WrappedInsights
import com.fintrackai.domain.wrapped.WrappedInsightsEngine
import com.fintrackai.domain.wrapped.WrappedMonthHelper
import com.fintrackai.navigation.AppNavStateViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WrappedUiState(
    val loading: Boolean = true,
    val insights: WrappedInsights? = null,
    val hasData: Boolean = true
)

@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val prefs: PreferencesManager,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _state = MutableStateFlow(WrappedUiState())
    val state = _state.asStateFlow()

    init {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            try {
                // Always show the previous calendar month in Wrapped
                val prevMonthKey = AppNavStateViewModel.previousMonthKey()
                val month = WrappedMonthHelper.resolve()
                val prevOfPrevMonthKey = WrappedMonthHelper.previousMonthKey(prevMonthKey)

                val stats = repo.getMonthlyStatsForMonth(prevMonthKey)
                val prevStats = repo.getMonthlyStatsForMonth(prevOfPrevMonthKey)
                val categoryStats = repo.getCategoryStats(prevMonthKey)
                val merchantStats = repo.getMerchantStats(prevMonthKey)
                val transactions = repo.getDebitTransactionsForMonth(prevMonthKey)
                val mostExpensiveDay = repo.getMostExpensiveDayInMonth(prevMonthKey)
                val timeDist = repo.getTimeDistributionForMonth(prevMonthKey)

                if (transactions.isEmpty()) {
                    // No data for previous month — mark as shown so we don't re-check until next month
                    prefs.setWrappedLastShownMonth(prevMonthKey)
                    analytics.logWrappedNoData()
                    _state.value = WrappedUiState(loading = false, hasData = false)
                    return@launch
                }

                val insights = WrappedInsightsEngine.compute(
                    month = month,
                    stats = stats,
                    prevStats = prevStats,
                    categoryStats = categoryStats,
                    merchantStats = merchantStats,
                    transactions = transactions,
                    mostExpensiveDay = mostExpensiveDay,
                    timeDistribution = timeDist
                )

                // Mark this month's wrapped as shown
                prefs.setWrappedLastShownMonth(prevMonthKey)
                analytics.logWrappedOpened()

                _state.value = WrappedUiState(loading = false, insights = insights)
            } catch (e: Exception) {
                analytics.logWrappedLoadFailed(e.message ?: "unknown")
                _state.value = WrappedUiState(loading = false, hasData = false)
            }
        }
    }
}
