package com.fintrackai.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

object GraphScrollScaleHelper {
    /**
     * Max absolute value among [items] indices that are visible in [listState] plus
     * [GraphScrollConstants.VIEWPORT_NEIGHBOR_PAD] neighbors on each side.
     * Falls back to global max when nothing is laid out yet.
     */
    fun <T> viewportMaxAbs(
        items: List<T>,
        listState: LazyListState,
        neighborPad: Int = GraphScrollConstants.VIEWPORT_NEIGHBOR_PAD,
        value: (T) -> Double
    ): Double {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || items.isEmpty()) {
            return globalMaxAbs(items, value)
        }
        val indices = indicesWithNeighbors(visible, neighborPad, items.size)
        val localMax = indices.maxOfOrNull { abs(value(items[it])) } ?: 0.0
        return localMax.coerceAtLeast(1.0)
    }

    /**
     * Max of non-negative [value] in the visible window (+ neighbors). For expense bars (amount ≥ 0).
     */
    fun <T> viewportMaxNonNegative(
        items: List<T>,
        listState: LazyListState,
        neighborPad: Int = GraphScrollConstants.VIEWPORT_NEIGHBOR_PAD,
        value: (T) -> Double
    ): Double {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || items.isEmpty()) {
            return globalMaxNonNegative(items, value)
        }
        val indices = indicesWithNeighbors(visible, neighborPad, items.size)
        val localMax = indices.maxOfOrNull { value(items[it]).coerceAtLeast(0.0) } ?: 0.0
        return localMax.coerceAtLeast(1.0)
    }

    private fun indicesWithNeighbors(
        visible: List<LazyListItemInfo>,
        neighborPad: Int,
        size: Int
    ): Set<Int> {
        val out = mutableSetOf<Int>()
        for (vi in visible) {
            for (d in -neighborPad..neighborPad) {
                val i = vi.index + d
                if (i in 0 until size) out.add(i)
            }
        }
        return out
    }

    private fun <T> globalMaxAbs(items: List<T>, value: (T) -> Double): Double =
        (items.maxOfOrNull { abs(value(it)) } ?: 0.0).coerceAtLeast(1.0)

    private fun <T> globalMaxNonNegative(items: List<T>, value: (T) -> Double): Double =
        (items.maxOfOrNull { value(it).coerceAtLeast(0.0) } ?: 0.0).coerceAtLeast(1.0)
}
