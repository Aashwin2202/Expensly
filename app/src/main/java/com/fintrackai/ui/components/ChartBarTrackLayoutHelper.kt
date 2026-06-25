package com.fintrackai.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ChartBarTrackLayoutHelper {
    /**
     * Vertical distance from the chart Row’s top to the bar track (bottom = amount 0, top = scale max).
     * Must match: Y-axis [Spacer], grid [Canvas] offset, LazyRow item layout (padding + amount + 4.dp + bar).
     */
    fun offsetFromRowTopToBarTrack(lazyRowContentPaddingTopDp: Int): Dp =
        lazyRowContentPaddingTopDp.dp +
            ChartGridConstants.CHART_AMOUNT_LABEL_BLOCK_HEIGHT_DP.dp +
            ChartGridConstants.SPACER_BELOW_AMOUNT_LABEL_DP.dp
}
