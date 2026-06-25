package com.fintrackai.ui.transactions

import com.fintrackai.domain.model.MonthTrend
import com.fintrackai.domain.sms.SmsConstants

/**
 * Helpers for the transactions screen monthly bar chart (aligns with RN `allMonthsInRange` behavior).
 */
object TransactionsGraphHelper {

    fun formatMonthKeyForDisplay(monthKey: String): String {
        val p = monthKey.split("-")
        if (p.size != 2) return monthKey
        val y = p[0].toIntOrNull() ?: return monthKey
        val m = p[1].toIntOrNull() ?: return monthKey
        return "${SmsConstants.MONTH_NAMES.getOrElse(m - 1) { "" }} '${y.toString().takeLast(2)}"
    }

    /** All YYYY-MM keys from [startMonthKey] through [endMonthKey] inclusive (lexicographic order works for ISO months). */
    fun enumerateMonthsInclusive(startMonthKey: String, endMonthKey: String): List<String> {
        if (startMonthKey > endMonthKey) return enumerateMonthsInclusive(endMonthKey, startMonthKey)
        val out = mutableListOf<String>()
        var y = startMonthKey.take(4).toIntOrNull() ?: return listOf(startMonthKey)
        var mo = startMonthKey.drop(5).take(2).toIntOrNull() ?: return listOf(startMonthKey)
        val endY = endMonthKey.take(4).toIntOrNull() ?: return out
        val endMo = endMonthKey.drop(5).take(2).toIntOrNull() ?: return out
        while (y < endY || (y == endY && mo <= endMo)) {
            out.add(String.format("%04d-%02d", y, mo))
            mo++
            if (mo > 12) {
                mo = 1
                y++
            }
        }
        return out
    }

    /**
     * Month key for merchant/category detail when drilling from Transactions.
     * Single-day and same-calendar-month custom ranges map to that month; cross-month custom ranges use
     * empty (detail shows all-time). Without a custom range, uses [selectedMonthKey].
     */
    fun drillDownMonthKeyFromPeriod(
        customDateStart: String?,
        customDateEnd: String?,
        selectedMonthKey: String?
    ): String {
        if (customDateStart != null && customDateEnd != null) {
            val start = customDateStart
            val end = customDateEnd
            if (start.length >= 7 && end.length >= 7) {
                val startMonth = start.take(7)
                val endMonth = end.take(7)
                if (startMonth == endMonth) return startMonth
            }
            return ""
        }
        return selectedMonthKey.orEmpty()
    }

    fun monthTrendRow(monthKey: String, amount: Double): MonthTrend {
        val labelParts = monthKey.split("-")
        val monthName = if (labelParts.size == 2) {
            val mi = labelParts[1].toIntOrNull()
            if (mi != null && mi in 1..12) {
                "${SmsConstants.MONTH_NAMES[mi - 1]} '${labelParts[0].takeLast(2)}"
            } else formatMonthKeyForDisplay(monthKey)
        } else formatMonthKeyForDisplay(monthKey)
        return MonthTrend(month = monthName, monthKey = monthKey, amount = amount)
    }
}
