package com.fintrackai.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fintrackai.data.local.db.ReminderDao
import com.fintrackai.data.local.preferences.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * WorkManager worker that fires reminder notifications daily at 9 AM.
 * - Sends an "upcoming" notification for any reminder due tomorrow (unpaid).
 * - Sends an "overdue" notification for any reminder due yesterday (still unpaid).
 */
@HiltWorker
class ReminderNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderDao: ReminderDao,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationManagerHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferencesManager.notifReminders.first()) return Result.success()

        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val yesterday = today.minusDays(1)

        val reminders = reminderDao.getAllOnce()

        reminders.forEach { reminder ->
            val baseDate = runCatching { LocalDate.parse(reminder.reminderDate) }.getOrNull()
                ?: return@forEach

            // Skip if already paid this calendar month
            val alreadyPaidThisMonth = reminder.paidOn?.let { paidOn ->
                runCatching { LocalDate.parse(paidOn) }.getOrNull()?.let {
                    it.year == today.year && it.monthValue == today.monthValue
                }
            } ?: false
            if (alreadyPaidThisMonth) return@forEach

            val isDueTomorrow = isDueOn(reminder.frequency, baseDate, tomorrow)
            val isDueYesterday = isDueOn(reminder.frequency, baseDate, yesterday)

            if (isDueTomorrow) {
                notificationHelper.sendReminderUpcoming(
                    reminderId = reminder.id,
                    merchant = reminder.merchant,
                    amount = reminder.amount
                )
            } else if (isDueYesterday) {
                notificationHelper.sendReminderOverdue(
                    reminderId = reminder.id,
                    merchant = reminder.merchant,
                    amount = reminder.amount
                )
            }
        }

        return Result.success()
    }

    /**
     * Returns true if [baseDate]'s recurrence falls on [checkDate] for the given [frequency].
     *
     * - monthly: same day-of-month each month (clamped to month length)
     * - quarterly / half_yearly: same day-of-month every 3 or 6 months
     * - yearly: exact month+day each year
     * - one_time / unknown: exact date match only
     */
    private fun isDueOn(frequency: String, baseDate: LocalDate, checkDate: LocalDate): Boolean {
        val day = minOf(baseDate.dayOfMonth, checkDate.month.length(checkDate.isLeapYear))
        return when (frequency) {
            "monthly" -> checkDate.dayOfMonth == day
            "quarterly" -> {
                val monthsDiff = (checkDate.year - baseDate.year) * 12 +
                        (checkDate.monthValue - baseDate.monthValue)
                monthsDiff % 3 == 0 && checkDate.dayOfMonth == day
            }
            "half_yearly" -> {
                val monthsDiff = (checkDate.year - baseDate.year) * 12 +
                        (checkDate.monthValue - baseDate.monthValue)
                monthsDiff % 6 == 0 && checkDate.dayOfMonth == day
            }
            "yearly" -> checkDate.monthValue == baseDate.monthValue && checkDate.dayOfMonth == day
            else -> checkDate == baseDate  // one_time or unknown: exact match
        }
    }
}
