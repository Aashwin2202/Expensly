package com.fintrackai.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AppNavStateViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    /**
     * Null until DataStore has emitted; then drives cold-start route so we never flash Auth
     * when the user is already logged in.
     */
    val bootstrap: StateFlow<NavBootstrap?> = combine(
        combine(
            prefs.isLoggedIn,
            prefs.loginSmsImportCompleted,
            prefs.wrappedLastShownMonth,
            prefs.onboardingCompleted,
            prefs.homeTutorialSeen,
            prefs.walletTutorialSeen,
            prefs.txTutorialSeen,
            prefs.categoryTipSeen,
            prefs.txDetailTipSeen,
            prefs.smsPermissionGranted
        ) { it },
        prefs.feedbackPromptShown
    ) { values, feedbackShown ->
        val loggedIn = values[0] as Boolean
        val importDone = values[1] as Boolean
        val wrappedLastShown = values[2] as? String
        val onboardingDone = values[3] as Boolean
        val tutorialSeen = values[4] as Boolean
        val walletSeen = values[5] as Boolean
        val txSeen = values[6] as Boolean
        val catSeen = values[7] as Boolean
        val txDetailSeen = values[8] as Boolean
        val smsGranted = values[9] as Boolean
        val shouldShowWrapped = loggedIn && importDone && shouldShowWrappedForMonth(wrappedLastShown)
        NavBootstrap(loggedIn, importDone, shouldShowWrapped, onboardingDone, tutorialSeen, walletSeen, txSeen, catSeen, txDetailSeen, smsGranted, feedbackShown)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingCompleted() }
    }

    fun markHomeTutorialSeen() {
        viewModelScope.launch { prefs.setHomeTutorialSeen() }
    }

    fun markWalletTutorialSeen() {
        viewModelScope.launch { prefs.setWalletTutorialSeen() }
    }

    fun markTxTutorialSeen() {
        viewModelScope.launch { prefs.setTxTutorialSeen() }
    }

    fun markCategoryTipSeen() {
        viewModelScope.launch { prefs.setCategoryTipSeen() }
    }

    fun markTxDetailTipSeen() {
        viewModelScope.launch { prefs.setTxDetailTipSeen() }
    }

    fun markFeedbackPromptShown() {
        viewModelScope.launch { prefs.setFeedbackPromptShown() }
    }

    fun logNotificationTapped(destination: String) {
        val notifType = when {
            destination.startsWith("transactions_date|") -> "daily_review"
            destination.startsWith("transactions_range|") -> "transactions_range"
            destination.startsWith("weekly_summary|") -> "weekly_summary"
            destination == "budget" -> "budget"
            destination == "reminders" -> "reminders"
            else -> "unknown"
        }
        analytics.logNotificationTapped(notifType)
    }

    companion object {
        /**
         * Returns the "YYYY-MM" key for the previous calendar month — the month
         * that should be shown in Wrapped.
         */
        fun previousMonthKey(): String {
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }

        /**
         * Show Wrapped once per month: whenever the previous month's Wrapped hasn't been seen yet.
         */
        fun shouldShowWrappedForMonth(lastShown: String?): Boolean {
            val prevMonth = previousMonthKey()
            return lastShown != prevMonth
        }
    }
}
