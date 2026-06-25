package com.fintrackai.notification

import com.fintrackai.data.local.db.BudgetDao
import com.fintrackai.data.local.db.TransactionDao
import com.fintrackai.data.local.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks whether a category or overall monthly budget threshold (80% or 100%)
 * has been crossed and fires a real-time notification if so. Should be called
 * after every new debit transaction is inserted.
 */
@Singleton
class BudgetMonitor @Inject constructor(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationManagerHelper
) {

    /**
     * Call after inserting a debit transaction. Checks the category budget for
     * [category] and the overall monthly budget to see if spending has crossed
     * the 80% or 100% threshold.
     */
    suspend fun checkBudgetAfterTransaction(category: String) {
        if (!preferencesManager.notifBudgetAlerts.first()) return

        val today = LocalDate.now()
        val monthKey = "${today.year}-${today.monthValue.toString().padStart(2, '0')}"

        checkCategoryBudget(category, monthKey)
        checkOverallBudget(monthKey)
    }

    /**
     * Checks all category budgets and the overall budget for the current month.
     * Used by the periodic background worker so budget alerts fire even when the
     * SMS broadcast is suppressed by OEM battery optimisation.
     */
    suspend fun checkAllBudgets() {
        if (!preferencesManager.notifBudgetAlerts.first()) return

        val today = LocalDate.now()
        val monthKey = "${today.year}-${today.monthValue.toString().padStart(2, '0')}"

        checkOverallBudget(monthKey)

        val categoryBudgets = budgetDao.getCategoryBudgets()
        for (entry in categoryBudgets) {
            val category = entry.key.removePrefix("category_")
            val spent = transactionDao.getCategorySpendForMonth(category, monthKey)
            fireAlertIfNeeded(category, spent, entry.value)
        }
    }

    private suspend fun checkCategoryBudget(category: String, monthKey: String) {
        val budgetKey = "category_${category.lowercase()}"
        val budget = budgetDao.getCategoryBudgets().find { it.key == budgetKey }?.value
            ?: return

        val spent = transactionDao.getCategorySpendForMonth(category, monthKey)
        fireAlertIfNeeded(category, spent, budget)
    }

    private suspend fun checkOverallBudget(monthKey: String) {
        val budget = budgetDao.getMonthlyBudget() ?: return

        val stats = transactionDao.getMonthlyStats(monthKey)
        fireAlertIfNeeded("overall", stats.expense, budget)
    }

    private suspend fun fireAlertIfNeeded(category: String, spent: Double, budget: Double) {
        if (budget <= 0.0) return
        val percent = (spent / budget) * 100.0

        if (percent >= 100.0) {
            if (!preferencesManager.wasBudgetAlertSent(category, 100)) {
                if (notificationHelper.sendBudgetAlert(category, 100)) {
                    preferencesManager.markBudgetAlertSent(category, 100)
                }
            }
        } else if (percent >= 80.0) {
            if (!preferencesManager.wasBudgetAlertSent(category, 80)) {
                if (notificationHelper.sendBudgetAlert(category, 80)) {
                    preferencesManager.markBudgetAlertSent(category, 80)
                }
            }
        }
    }
}
