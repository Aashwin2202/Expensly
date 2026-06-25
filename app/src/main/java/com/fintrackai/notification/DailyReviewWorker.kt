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
import java.time.LocalDate

/**
 * WorkManager worker that fires the nightly daily-review notification.
 * Scheduled to run once daily between 8:30 PM – 10:30 PM via a periodic work request.
 */
@HiltWorker
class DailyReviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationManagerHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Check if user has this notification type enabled
        if (!preferencesManager.notifDailyReview.first()) return Result.success()

        val today = LocalDate.now().toString() // e.g. "2026-04-04"

        // Dedupe: don't send if already sent today
        if (preferencesManager.getNotifLastDailyDate() == today) return Result.success()

        // Rate limit: max 2 non-budget notifications per day
        if (!preferencesManager.tryIncrementNotifCount()) return Result.success()

        // Query today's data
        val stats = transactionDao.getDailySummaryStats(today)

        // Skip if zero transactions
        if (stats.txCount == 0) return Result.success()

        val topCategory = transactionDao.getTopCategoryForDate(today)?.category
        val merchantCount = transactionDao.getDistinctMerchantCountForDate(today)

        val sent = notificationHelper.sendDailyReview(
            date = today,
            totalSpend = stats.totalSpend,
            txCount = stats.txCount,
            topCategory = topCategory,
            merchantCount = merchantCount
        )

        // Only record the date when the notification was actually posted; if permission is denied,
        // don't mark it as sent so it retries on the next worker fire after permission is granted.
        if (sent) preferencesManager.setNotifLastDailyDate(today)
        return Result.success()
    }
}
