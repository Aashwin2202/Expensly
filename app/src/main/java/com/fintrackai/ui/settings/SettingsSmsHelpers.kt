package com.fintrackai.ui.settings

import com.fintrackai.domain.model.DateRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * When false, inbox scans must not use a null [com.fintrackai.data.repository.TransactionRepository.scanSmsInboxAndSave]
 * lower bound — that would read the full SMS inbox.
 */
fun hasSavedTransactionsForSmsDateScope(dateRange: DateRange): Boolean =
    dateRange.count > 0 &&
        dateRange.maxDate.isNotBlank() &&
        dateRange.maxDate != "N/A"

/**
 * Returns inclusive start-of-day millis in the default timezone for [yyyyMmDd],
 * or null if [yyyyMmDd] is blank, equals [naLabel], or cannot be parsed.
 */
fun startOfLocalDayMillisOrNull(yyyyMmDd: String?, naLabel: String = "N/A"): Long? {
    if (yyyyMmDd.isNullOrBlank() || yyyyMmDd == naLabel) return null
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
        isLenient = false
    }
    val day = sdf.parse(yyyyMmDd) ?: return null
    return Calendar.getInstance(TimeZone.getDefault()).apply {
        time = day
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
