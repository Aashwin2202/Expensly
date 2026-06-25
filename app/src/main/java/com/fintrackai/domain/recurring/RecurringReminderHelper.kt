package com.fintrackai.domain.recurring

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object RecurringReminderHelper {
    /**
     * Inclusive start date (yyyy-MM-dd) for the sliding window: first day of the month
     * that is [months] - 1 months before the start of the current month.
     * For [RecurringReminderConstants.RECURRING_DETECTION_CALENDAR_MONTHS] = 3, this is the first day of 2 months ago.
     */
    fun recurringDetectionStartDateInclusive(months: Int): String {
        require(months >= 1) { "months must be >= 1" }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, -(months - 1))
        return sdf.format(cal.time)
    }

    /** Exclusive end date (yyyy-MM-dd) = first day of the current month. Transactions on or after this date are excluded. */
    fun recurringDetectionEndDateExclusive(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return sdf.format(cal.time)
    }
}
