package com.fintrackai.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fintrackai.data.local.db.TransactionDao
import com.fintrackai.data.local.preferences.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * WorkManager worker that fires the weekly summary notification every Sunday evening.
 * Runs daily but only sends when it's Sunday (and hasn't already sent for this week).
 */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationManagerHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferencesManager.notifWeeklySummary.first()) return Result.success()

        val today = LocalDate.now()

        // Only fire on Sunday
        if (today.dayOfWeek != DayOfWeek.SUNDAY) return Result.success()

        val sundayKey = today.toString()
        if (preferencesManager.getNotifLastWeeklyDate() == sundayKey) return Result.success()

        if (!preferencesManager.tryIncrementNotifCount()) return Result.success()

        // This week: Monday–Sunday
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeekStats = transactionDao.getWeeklySummaryStats(
            startDate = thisMonday.toString(),
            endDate = today.toString()
        )

        if (thisWeekStats.txCount == 0) return Result.success()

        // Last week: previous Monday–Sunday
        val lastSunday = thisMonday.minusDays(1)
        val lastMonday = lastSunday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeekStats = transactionDao.getWeeklySummaryStats(
            startDate = lastMonday.toString(),
            endDate = lastSunday.toString()
        )

        val percentChange = if (lastWeekStats.totalSpend > 0) {
            ((thisWeekStats.totalSpend - lastWeekStats.totalSpend) / lastWeekStats.totalSpend) * 100.0
        } else {
            null // No comparison available
        }

        notificationHelper.sendWeeklySummary(
            totalSpend = thisWeekStats.totalSpend,
            percentChange = percentChange,
            weekStart = thisMonday.toString(),
            weekEnd = today.toString()
        )

        preferencesManager.setNotifLastWeeklyDate(sundayKey)
        return Result.success()
    }
}
