package com.fintrackai.ui.components

import kotlin.math.floor
import kotlin.math.pow

object ChartYAxisTicksHelper {

    /** Evenly spaced amounts from 0 through [max] (inclusive); pixel spacing of grid lines stays uniform. */
    fun ticksUniformZeroToMax(max: Double, divisions: Int = ChartGridConstants.CHART_Y_UNIFORM_DIVISIONS): List<Double> {
        val m = max.coerceAtLeast(1.0)
        val d = divisions.coerceAtLeast(1)
        return (0..d).map { i -> m * i / d }
    }

    /**
     * Evenly spaced ticks from −[maxAbs] to +[maxAbs] through zero (2×[halfDivisions] + 1 lines).
     */
    fun ticksUniformSymmetric(maxAbs: Double, halfDivisions: Int = ChartGridConstants.MONTHLY_BIDIRECTIONAL_HALF_DIVISIONS): List<Double> {
        val m = maxAbs.coerceAtLeast(1.0)
        val h = halfDivisions.coerceAtLeast(1)
        return ((-h)..h).map { k -> m * k / h.toDouble() }.distinct().sorted()
    }

    /**
     * Ascending ticks from 0 through [max] (inclusive), using "nice" steps so labels read cleanly.
     * [desiredSegments] guides roughly how many intervals appear between 0 and max.
     */
    fun ticksZeroToMax(max: Double, desiredSegments: Int = ChartGridConstants.CHART_Y_UNIFORM_DIVISIONS): List<Double> {
        val m = max.coerceAtLeast(1.0)
        val segments = desiredSegments.coerceAtLeast(1)
        val step = niceStep(m / segments)
        val out = mutableListOf<Double>()
        var v = 0.0
        var guard = 0
        while (v < m - step * 0.0001 && guard++ < 32) {
            out.add(v)
            v += step
        }
        val last = out.lastOrNull() ?: 0.0
        if (m - last < step * 0.12) {
            if (out.isNotEmpty()) out[out.lastIndex] = m
            else out.add(m)
        } else {
            out.add(m)
        }
        return out.distinct().sorted()
    }

    /**
     * Ticks for a centered zero axis: negative, zero, positive; magnitudes use the same nice steps as [ticksZeroToMax].
     */
    fun ticksSymmetricAroundZero(maxAbs: Double, segmentsPerSide: Int = ChartGridConstants.CHART_Y_UNIFORM_DIVISIONS): List<Double> {
        val m = maxAbs.coerceAtLeast(1.0)
        val positiveOnly = ticksZeroToMax(m, segmentsPerSide).filter { it > 0 }
        val negative = positiveOnly.map { -it }.sorted()
        return (negative + listOf(0.0) + positiveOnly).distinct().sorted()
    }

    private fun niceStep(rough: Double): Double {
        if (rough <= 0) return 1.0
        val exp = floor(kotlin.math.log10(rough)).toInt()
        val man = rough / 10.0.pow(exp)
        val niceMan = when {
            man <= 1.0 -> 1.0
            man <= 2.0 -> 2.0
            man <= 5.0 -> 5.0
            else -> 10.0
        }
        return niceMan * 10.0.pow(exp)
    }
}
