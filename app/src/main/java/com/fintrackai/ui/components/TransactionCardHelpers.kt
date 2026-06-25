package com.fintrackai.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats [date] (YYYY-MM-DD) and time (e.g. HH:mm) as "Mon, 20 Mar 2025 • 14:30". */
fun formatTransactionDayDateTime(date: String, time: String): String {
    val trimmedTime = time.trim()
    return try {
        val d = LocalDate.parse(date)
        val day = d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        val datePart = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        val timePart = if (trimmedTime.isEmpty()) "—" else trimmedTime
        "$day, $datePart • $timePart"
    } catch (_: Exception) {
        val t = if (trimmedTime.isEmpty()) "—" else trimmedTime
        "$date • $t"
    }
}
