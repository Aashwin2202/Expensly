package com.fintrackai.ui.home

import com.fintrackai.domain.model.DailyExpenseDay
import java.time.LocalDate
import java.time.YearMonth

object HomeDailyExpenseHelper {
    /** Full month 1..lastDay with debit totals (0 if none) for the home chart. */
    fun buildDaysForMonth(monthKey: String, amountsByDate: Map<String, Double>): List<DailyExpenseDay> {
        val ym = runCatching {
            val p = monthKey.split("-")
            if (p.size != 2) return emptyList()
            YearMonth.of(p[0].toInt(), p[1].toInt())
        }.getOrNull() ?: return emptyList()
        val lastDay = ym.lengthOfMonth()
        val today = LocalDate.now()
        return (1..lastDay).map { day ->
            val date = ym.atDay(day)
            val key = String.format("%04d-%02d-%02d", ym.year, ym.monthValue, day)
            DailyExpenseDay(
                dayOfMonth = day,
                dateKey = key,
                amount = amountsByDate[key] ?: 0.0,
                isFuture = date.isAfter(today),
                isToday = date == today
            )
        }
    }
}
