package com.fintrackai.domain.recurring

object RecurringReminderConstants {
    /**
     * How many calendar months of debit history to use when detecting recurring charges.
     * With 3, start date = first of 2 months ago; combined with the exclusive end of the
     * current month's first day, this covers only the last 2 complete months (excluding current).
     */
    const val RECURRING_DETECTION_CALENDAR_MONTHS = 3
}
