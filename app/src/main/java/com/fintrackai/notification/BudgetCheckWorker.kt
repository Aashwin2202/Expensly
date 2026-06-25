package com.fintrackai.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that re-checks all budget thresholds every few hours while the app is closed.
 * This is the reliable background path — the SMS broadcast receiver is suppressed on many OEM
 * devices with aggressive battery optimisation, so the budget check must happen independently.
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val budgetMonitor: BudgetMonitor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        budgetMonitor.checkAllBudgets()
        return Result.success()
    }
}
