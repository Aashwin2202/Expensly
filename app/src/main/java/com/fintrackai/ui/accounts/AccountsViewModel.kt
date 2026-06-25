package com.fintrackai.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.account.AccountSummaryHelper
import com.fintrackai.domain.model.AccountSummary
import com.fintrackai.domain.model.RecurringTransaction
import com.fintrackai.domain.model.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class AccountsUiState(
    val accountSummaries: List<AccountSummary> = emptyList(),
    val creditCardSummaries: List<AccountSummary> = emptyList(),
    val debitCardSummaries: List<AccountSummary> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
    val reminders: List<Reminder> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val prefs: PreferencesManager,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val currentMonthKey: String = run {
        val cal = Calendar.getInstance()
        String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    init {
        // Aggregate DB query + mappings combined reactively — no full table scan on every emission.
        combine(
            repo.getAccountTotalsFlow(currentMonthKey),
            repo.getAccountMappingsFlow()
        ) { totals, mappings ->
            withContext(Dispatchers.Default) {
                AccountSummaryHelper.splitFromTotals(totals, mappings)
            }
        }.onEach { (accounts, creditCards, debitCards) ->
            _uiState.update {
                it.copy(
                    accountSummaries = accounts,
                    creditCardSummaries = creditCards,
                    debitCardSummaries = debitCards
                )
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            repo.getRemindersFlow().collect { reminders ->
                _uiState.update { it.copy(reminders = reminders) }
            }
        }

        viewModelScope.launch {
            coroutineScope {
                launch { refreshRecurringIfNeeded() }
                launch { autoMarkRemindersPaid() }
            }
        }
    }

    private suspend fun autoMarkRemindersPaid() {
        repo.autoMarkRemindersIfTransactionFound(currentMonthKey)
    }

    private suspend fun refreshRecurringIfNeeded() {
        val lastComputed = prefs.getRecurringLastComputedMonth()
        if (lastComputed == currentMonthKey && _uiState.value.recurringTransactions.isNotEmpty()) return
        val recurring = withContext(Dispatchers.Default) { repo.getRecurringTransactions() }
        _uiState.update { it.copy(recurringTransactions = recurring) }
        prefs.setRecurringLastComputedMonth(currentMonthKey)
    }

    fun markReminderPaid(id: String) {
        analytics.logReminderMarkedPaid()
        viewModelScope.launch {
            repo.markReminderPaid(id, java.time.LocalDate.now().toString())
        }
    }

    fun deleteReminder(id: String) {
        analytics.logReminderDeleted()
        viewModelScope.launch { repo.deleteReminder(id) }
    }

    fun dismissRecurring(merchant: String, amount: Double) {
        analytics.logReminderRecurringDismissed()
        viewModelScope.launch {
            repo.dismissRecurring(merchant, amount)
            val updated = withContext(Dispatchers.Default) { repo.getRecurringTransactions() }
            _uiState.update { it.copy(recurringTransactions = updated) }
        }
    }

    fun changeCardType(last4Digits: String, bankName: String, newType: String) {
        analytics.logAccountCardTypeChanged(from = "unknown", to = newType)
        viewModelScope.launch {
            repo.updateAccountType(last4Digits, bankName, newType)
            // account mappings flow will re-emit automatically via getAllFlow()
        }
    }

    fun deleteCard(last4Digits: String, bankName: String) {
        analytics.logAccountCardDeleted()
        viewModelScope.launch {
            repo.deleteCardMapping(last4Digits, bankName)
            // account mappings flow will re-emit automatically via getAllFlow()
        }
    }
}
