package com.fintrackai.domain.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Compact Indian-style amount labels (matches React Native [formatAmount] in
 * frontend/src/utils/helpers/formatAmount.ts): Cr / L / K with fixed decimals.
 */
object AmountCompactFormatHelper {

    /**
     * Returns the numeric suffix only (no ₹). Examples: `1.87L`, `5.0K`, `12.34Cr`, `999`.
     * Negative values use a leading minus on the whole label.
     */
    fun formatCompact(amount: Double): String {
        val a = abs(amount)
        val sign = if (amount < 0) "-" else ""
        val body = when {
            a >= AmountCompactFormatConstants.ONE_CRORE ->
                String.format(Locale.US, "%.2f", a / AmountCompactFormatConstants.ONE_CRORE) + "Cr"
            a >= AmountCompactFormatConstants.ONE_LAKH ->
                String.format(Locale.US, "%.2f", a / AmountCompactFormatConstants.ONE_LAKH) + "L"
            a >= AmountCompactFormatConstants.ONE_THOUSAND ->
                String.format(Locale.US, "%.1f", a / AmountCompactFormatConstants.ONE_THOUSAND) + "K"
            else -> round(a).toLong().toString()
        }
        return sign + body
    }

    /** Same as [formatCompact] with rupee prefix. */
    fun formatCompactWithRupee(amount: Double): String = "₹${formatCompact(amount)}"
}
