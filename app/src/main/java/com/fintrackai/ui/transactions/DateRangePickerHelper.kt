package com.fintrackai.ui.transactions

import java.util.Calendar
import java.util.Locale

object DateRangePickerHelper {
    private fun formatYmd(c: Calendar): String = String.format(
        Locale.US,
        "%04d-%02d-%02d",
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH) + 1,
        c.get(Calendar.DAY_OF_MONTH)
    )

    /**
     * First day of the month that is ([monthCount] − 1) months before the current month,
     * through today — e.g. 3 → roughly “last three calendar months” including the current one.
     */
    fun lastNCalendarMonthsThroughToday(monthCount: Int): Pair<String, String> {
        require(monthCount >= 1)
        val end = Calendar.getInstance(Locale.US)
        val start = Calendar.getInstance(Locale.US)
        start.add(Calendar.MONTH, -(monthCount - 1))
        start.set(Calendar.DAY_OF_MONTH, 1)
        return formatYmd(start) to formatYmd(end)
    }
}
