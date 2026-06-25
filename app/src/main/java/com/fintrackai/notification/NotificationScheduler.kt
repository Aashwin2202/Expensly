package com.fintrackai.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules periodic WorkManager jobs for daily and weekly notifications.
 * Call [schedule] once at app startup — WorkManager uses KEEP policy so
 * re-scheduling is a no-op if work is already enqueued.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val WORK_DAILY_REVIEW = "daily_review_notification"
        const val WORK_WEEKLY_SUMMARY = "weekly_summary_notification"
        const val WORK_REMINDER = "reminder_notification"
        const val WORK_BUDGET_CHECK = "budget_check"
    }

    /**
     * Enqueues all workers using KEEP policy. Safe to call on every app startup —
     * already-queued work is untouched.
     */
    fun schedule() {
        scheduleDailyReview(ExistingPeriodicWorkPolicy.KEEP)
        scheduleWeeklySummary(ExistingPeriodicWorkPolicy.KEEP)
        scheduleReminderCheck(ExistingPeriodicWorkPolicy.KEEP)
        scheduleBudgetCheck(ExistingPeriodicWorkPolicy.KEEP)
    }

    /**
     * Cancels and re-enqueues all workers with fresh initial delays. Call this after
     * the user grants notification permission so the first fire uses the correct target
     * time rather than whatever delay was computed before permission was granted.
     */
    fun reschedule() {
        scheduleDailyReview(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        scheduleWeeklySummary(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        scheduleReminderCheck(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        scheduleBudgetCheck(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
    }

    private fun scheduleDailyReview(policy: ExistingPeriodicWorkPolicy) {
        val initialDelay = computeDelayTo(targetHour = 21, targetMinute = 0)

        val request = PeriodicWorkRequestBuilder<DailyReviewWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_DAILY_REVIEW, policy, request
        )
    }

    private fun scheduleWeeklySummary(policy: ExistingPeriodicWorkPolicy) {
        val initialDelay = computeDelayTo(targetHour = 20, targetMinute = 30)

        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_WEEKLY_SUMMARY, policy, request
        )
    }

    private fun scheduleReminderCheck(policy: ExistingPeriodicWorkPolicy) {
        val initialDelay = computeDelayTo(targetHour = 9, targetMinute = 0)

        val request = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_REMINDER, policy, request
        )
    }

    private fun scheduleBudgetCheck(policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<BudgetCheckWorker>(
            repeatInterval = 4, repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_BUDGET_CHECK, policy, request
        )
    }

    /**
     * Computes the delay from now to the next occurrence of [targetHour]:[targetMinute].
     * If that time has already passed today, targets tomorrow.
     */
    private fun computeDelayTo(targetHour: Int, targetMinute: Int): Duration {
        val now = LocalDateTime.now()
        val targetToday = now.toLocalDate().atTime(LocalTime.of(targetHour, targetMinute))
        val target = if (now.isBefore(targetToday)) targetToday else targetToday.plusDays(1)
        return Duration.between(now, target)
    }
}
