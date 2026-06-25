package com.fintrackai.domain.wrapped

import java.util.Calendar
import java.util.Locale

/**
 * Determines which month to show in the "Expense Wrapped" flow.
 * If current date <= 10th → last month (full month data).
 * Else → current month (month-to-date).
 */
object WrappedMonthHelper {

    data class WrappedMonth(
        val monthKey: String,       // "YYYY-MM"
        val displayName: String,    // "March 2026"
        val isFullMonth: Boolean,   // true if showing last month
        val daysInMonth: Int,
        val daysElapsed: Int        // days with data (full month = daysInMonth, MTD = today's day)
    )

    fun resolve(now: Calendar = Calendar.getInstance()): WrappedMonth {
        val target = (now.clone() as Calendar).apply {
            add(Calendar.MONTH, -1)
        }

        val year = target.get(Calendar.YEAR)
        val month = target.get(Calendar.MONTH) + 1
        val monthKey = String.format("%04d-%02d", year, month)
        val daysInMonth = target.getActualMaximum(Calendar.DAY_OF_MONTH)

        val monthName = target.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
        val displayName = "$monthName $year"

        return WrappedMonth(
            monthKey = monthKey,
            displayName = displayName,
            isFullMonth = true,
            daysInMonth = daysInMonth,
            daysElapsed = daysInMonth
        )
    }

    /** Previous month relative to the wrapped month (for comparison). */
    fun previousMonthKey(wrappedMonthKey: String): String {
        val parts = wrappedMonthKey.split("-")
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, parts[0].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
}
