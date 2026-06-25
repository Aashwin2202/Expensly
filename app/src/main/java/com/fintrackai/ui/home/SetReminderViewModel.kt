package com.fintrackai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.model.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetReminderUiState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val existing: Reminder? = null,
    val loaded: Boolean = false
)

@HiltViewModel
class SetReminderViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetReminderUiState())
    val uiState: StateFlow<SetReminderUiState> = _uiState.asStateFlow()

    fun loadExisting(reminderId: String) {
        if (_uiState.value.loaded) return
        viewModelScope.launch {
            val reminder = repo.getReminderById(reminderId)
            _uiState.value = _uiState.value.copy(existing = reminder, loaded = true)
        }
    }

    fun saveReminder(
        reminderId: String?,
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        frequency: String,
        reminderDate: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            val savedId: String
            if (reminderId != null) {
                repo.updateReminder(reminderId, type, amount, category, merchant, frequency, reminderDate)
                analytics.logReminderUpdated(type)
                savedId = reminderId
            } else {
                savedId = repo.createReminder(type, amount, category, merchant, frequency, reminderDate).id
                analytics.logReminderCreated(type, frequency)
            }
            // Auto-mark paid if a matching transaction already exists this month
            val monthKey = java.time.LocalDate.now().let {
                String.format("%04d-%02d", it.year, it.monthValue)
            }
            val alreadyPaid = repo.getReminderById(savedId)?.paid_on?.let { paidOn ->
                runCatching { java.time.LocalDate.parse(paidOn) }.getOrNull()?.let {
                    it.year == java.time.LocalDate.now().year && it.monthValue == java.time.LocalDate.now().monthValue
                }
            } ?: false
            if (!alreadyPaid && repo.hasMatchingTransactionInMonth(merchant, amount, monthKey)) {
                repo.markReminderPaid(savedId, java.time.LocalDate.now().toString())
            }
            _uiState.value = _uiState.value.copy(saving = false, saved = true)
        }
    }
}
