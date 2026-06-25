package com.fintrackai.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AddTransactionUiState(
    val merchant: String = "",
    val amount: String = "",
    val type: String = "debit",
    /** When true, debit affects expense totals; credit affects income totals. */
    val countInStats: Boolean = true,
    val category: String = "others",
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val time: String = SimpleDateFormat("HH:mm", Locale.US).format(Date()),
    val accounts: String = "",
    /** Distinct non-blank account strings from saved transactions (for picker). */
    val knownAccountOptions: List<String> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getCustomCategoriesFlow().collect { custom ->
                _uiState.value = _uiState.value.copy(customCategories = custom)
            }
        }
        viewModelScope.launch {
            repo.getAllTransactionsFlow().collect { list ->
                val opts = list.mapNotNull { tx ->
                    tx.accounts.trim().takeIf { it.isNotEmpty() }
                }.distinct().sorted()
                _uiState.value = _uiState.value.copy(knownAccountOptions = opts)
            }
        }
    }

    fun updateMerchant(v: String) { _uiState.value = _uiState.value.copy(merchant = v) }
    fun updateAmount(v: String) {
        // Keep only digits and one decimal point — no comma formatting while typing
        val filtered = v.filter { it.isDigit() || it == '.' }
        val singleDot = if (filtered.count { it == '.' } > 1) {
            val first = filtered.indexOf('.')
            filtered.substring(0, first + 1) + filtered.substring(first + 1).replace(".", "")
        } else filtered
        _uiState.value = _uiState.value.copy(amount = singleDot)
    }

    fun formatAmountOnBlur() {
        val current = _uiState.value.amount
        _uiState.value = _uiState.value.copy(amount = formatAmountWithCommas(current))
    }

    fun updateType(v: String) { _uiState.value = _uiState.value.copy(type = v) }
    fun updateCountInStats(v: Boolean) { _uiState.value = _uiState.value.copy(countInStats = v) }
    fun updateCategory(v: String) { _uiState.value = _uiState.value.copy(category = v) }
    fun updateDate(v: String) { _uiState.value = _uiState.value.copy(date = v) }
    fun updateTime(v: String) { _uiState.value = _uiState.value.copy(time = v) }
    fun updateAccounts(v: String) { _uiState.value = _uiState.value.copy(accounts = v) }

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

    fun save() {
        val s = _uiState.value
        val amt = s.amount.replace(",", "").toDoubleOrNull()
        if (s.merchant.isBlank()) { _uiState.value = s.copy(error = "Enter merchant name"); return }
        if (amt == null || amt <= 0) { _uiState.value = s.copy(error = "Enter valid amount"); return }
        if (!s.date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            _uiState.value = s.copy(error = AddTransactionConstants.INVALID_DATE)
            return
        }
        if (!s.time.matches(Regex("^\\d{2}:\\d{2}$"))) {
            _uiState.value = s.copy(error = AddTransactionConstants.INVALID_TIME)
            return
        }

        _uiState.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            val tx = Transaction(
                id = "${System.currentTimeMillis()}-${(1..9).map { ('a'..'z').random() }.joinToString("")}",
                merchant = s.merchant.trim(),
                amount = amt,
                type = s.type,
                category = s.category,
                date = s.date,
                time = s.time,
                accounts = s.accounts.trim(),
                countInStats = s.countInStats
            )
            val saved = repo.saveTransaction(tx)
            if (saved && tx.merchant.isNotBlank()) {
                repo.saveMerchantCategoryMapping(tx.merchant, tx.category)
            }
            if (saved) {
                analytics.logTransactionAddedManual(tx.category, tx.amount, tx.type)
            }
            _uiState.value = _uiState.value.copy(saving = false, saved = true)
        }
    }
}

internal fun formatAmountWithCommas(raw: String): String {
    if (raw.isEmpty()) return raw
    val clean = raw.replace(",", "")
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    val dotIndex = clean.indexOf('.')
    return if (dotIndex >= 0) {
        val intPart = clean.substring(0, dotIndex)
        val decPart = clean.substring(dotIndex)
        (intPart.toLongOrNull()?.let { fmt.format(it) } ?: intPart) + decPart
    } else {
        clean.toLongOrNull()?.let { fmt.format(it) } ?: clean
    }
}
