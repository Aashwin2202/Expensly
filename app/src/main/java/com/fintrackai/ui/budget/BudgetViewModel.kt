package com.fintrackai.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.category.BUILT_IN_CATEGORIES
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.model.CustomCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.fintrackai.ui.transactions.formatAmountWithCommas
import java.util.Calendar
import javax.inject.Inject

data class CategoryBudgetItem(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val spent: Double,
    val budget: Double
)

data class BudgetUiState(
    val monthlyBudget: Double? = null,
    val monthlyBudgetInput: String = "",
    val monthlyExpense: Double = 0.0,
    val categoryBudgets: List<CategoryBudgetItem> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadData()
        viewModelScope.launch {
            repo.getCustomCategoriesFlow().collect { loadData() }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val cal = Calendar.getInstance()
            val monthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val stats = repo.getMonthlyStats()
            val budget = repo.getMonthlyBudget()
            val categoryStats = repo.getCategoryStats(monthKey)
            val categoryBudgets = repo.getCategoryBudgets()
            val customCats = repo.getCustomCategories()

            val builtInIds = BUILT_IN_CATEGORIES.map { it.first }.toSet()
            val allCats = BUILT_IN_CATEGORIES + customCats
                .filter { it.id !in builtInIds }
                .map { Triple(it.id, it.icon, it.color) }

            val spentMap = categoryStats.associate { it.category.lowercase() to it.amount }

            val items = allCats.map { (id, _, color) ->
                CategoryBudgetItem(
                    id = id,
                    name = CategoryCatalogHelper.categoryLabel(id, customCats),
                    icon = CategoryCatalogHelper.categoryIcon(id, customCats), color = color,
                    spent = spentMap[id.lowercase()] ?: 0.0,
                    budget = categoryBudgets[id.lowercase()] ?: 0.0
                )
            }

            _uiState.value = BudgetUiState(
                monthlyBudget = budget,
                monthlyBudgetInput = budget?.let { formatBudgetAmount(it) } ?: "",
                monthlyExpense = stats.expense,
                categoryBudgets = items,
                customCategories = customCats,
                loading = false
            )
        }
    }

    fun updateMonthlyBudgetInput(v: String) {
        _uiState.value = _uiState.value.copy(monthlyBudgetInput = v.filter { it.isDigit() })
    }

    fun formatMonthlyBudgetOnBlur() {
        val current = _uiState.value.monthlyBudgetInput
        _uiState.value = _uiState.value.copy(monthlyBudgetInput = formatAmountWithCommas(current))
    }

    fun saveMonthlyBudget() {
        val amount = _uiState.value.monthlyBudgetInput.replace(",", "").toDoubleOrNull() ?: return
        viewModelScope.launch {
            if (amount > 0) {
                repo.setMonthlyBudget(amount)
                analytics.logBudgetSetMonthly(amount)
            } else {
                repo.removeMonthlyBudget()
                analytics.logBudgetRemovedMonthly()
            }
            loadData()
        }
    }

    fun saveCategoryBudget(categoryId: String, amountStr: String) {
        val amount = amountStr.replace(",", "").toDoubleOrNull()
        viewModelScope.launch {
            if (amount != null && amount > 0) {
                repo.setCategoryBudget(categoryId, amount)
                analytics.logBudgetSetCategory(categoryId, amount)
            } else {
                repo.removeCategoryBudget(categoryId)
                analytics.logBudgetRemovedCategory(categoryId)
            }
            loadData()
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

    private fun formatBudgetAmount(amount: Double): String = amount.toLong().toString()
}
