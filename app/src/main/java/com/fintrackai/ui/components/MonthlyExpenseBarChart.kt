package com.fintrackai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.domain.format.AmountCompactFormatHelper
import com.fintrackai.domain.model.MonthTrend
import com.fintrackai.ui.theme.ExtendedColors

/**
 * Horizontal bar chart used on Transactions and detail screens.
 * When [onSelectMonth] is null, bars are not clickable (drill-down month locked).
 * If any month has a negative amount, bars are drawn from a center zero line (down = net debit-heavy).
 * Bar heights scale to the max amount among the visible items plus [GraphScrollConstants.VIEWPORT_NEIGHBOR_PAD]
 * neighbors on each side, so the scale updates while scrolling horizontally.
 */
@Composable
fun MonthlyExpenseBarChart(
    months: List<MonthTrend>,
    selectedMonthKey: String?,
    listState: LazyListState,
    ext: ExtendedColors,
    onSelectMonth: ((String) -> Unit)?,
    forcePositive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bidirectional = !forcePositive && months.any { it.amount < 0 }
    val scaleMaxAbs by remember(months, listState) {
        derivedStateOf {
            GraphScrollScaleHelper.viewportMaxAbs(months, listState) { it.amount }
        }
    }
    val scaleMaxPos by remember(months, listState) {
        derivedStateOf {
            GraphScrollScaleHelper.viewportMaxNonNegative(months, listState) { maxOf(0.0, it.amount) }
        }
    }
    val cellW = BarChartConstants.MONTHLY_CELL_WIDTH_DP.dp
    val trackW = BarChartConstants.MONTHLY_BAR_TRACK_WIDTH_DP.dp
    val barH = BarChartConstants.CHART_BAR_HEIGHT_DP.dp
    val posTicks = remember(scaleMaxPos) {
        ChartYAxisTicksHelper.ticksUniformZeroToMax(scaleMaxPos, ChartGridConstants.CHART_Y_UNIFORM_DIVISIONS)
    }
    val symTicks = remember(scaleMaxAbs) {
        ChartYAxisTicksHelper.ticksUniformSymmetric(scaleMaxAbs, ChartGridConstants.MONTHLY_BIDIRECTIONAL_HALF_DIVISIONS)
    }
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ChartGridConstants.GRID_LINE_ALPHA)
    val barTrackTopOffset =
        ChartBarTrackLayoutHelper.offsetFromRowTopToBarTrack(ChartGridConstants.MONTHLY_LAZY_ROW_CONTENT_TOP_DP)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.width(ChartGridConstants.Y_AXIS_LABEL_COLUMN_WIDTH_DP.dp).padding(start = 6.dp)) {
                Spacer(Modifier.height(barTrackTopOffset))
                if (bidirectional) {
                    BidirectionalYAxisLabels(
                        maxAbs = scaleMaxAbs,
                        barHeight = barH,
                        tickAmounts = symTicks,
                        labelColor = ext.textSecondary.copy(alpha = 0.88f),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    UnidirectionalYAxisLabels(
                        viewportMax = scaleMaxPos,
                        barHeight = barH,
                        ticks = posTicks,
                        labelColor = ext.textSecondary.copy(alpha = 0.88f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (bidirectional) {
                    BidirectionalBarGridCanvas(
                        maxAbs = scaleMaxAbs,
                        barHeight = barH,
                        tickAmounts = symTicks,
                        lineColor = gridLineColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = barTrackTopOffset)
                    )
                } else {
                    UnidirectionalBarGridCanvas(
                        viewportMax = scaleMaxPos,
                        barHeight = barH,
                        ticks = posTicks,
                        lineColor = gridLineColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = barTrackTopOffset)
                    )
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = ChartGridConstants.MONTHLY_LAZY_ROW_CONTENT_TOP_DP.dp,
                        end = 12.dp,
                        bottom = ChartGridConstants.MONTHLY_LAZY_ROW_CONTENT_TOP_DP.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(months, key = { it.monthKey }) { item ->
                val selected = item.monthKey == selectedMonthKey
                val mod = Modifier
                    .width(cellW)
                    .then(
                        if (selected) Modifier.clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        else Modifier
                    )
                    .then(
                        if (onSelectMonth != null) {
                            Modifier.clickable { onSelectMonth(item.monthKey) }
                        } else Modifier
                    )
                val isNegative = !forcePositive && item.amount < 0
                val labelColor = if (isNegative) MaterialTheme.colorScheme.error else ext.text
                val amountText = AmountCompactFormatHelper.formatCompactWithRupee(if (forcePositive) kotlin.math.abs(item.amount) else item.amount)
                Column(
                    modifier = mod,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = amountText,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) (if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else labelColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ChartGridConstants.CHART_AMOUNT_LABEL_BLOCK_HEIGHT_DP.dp)
                            .padding(horizontal = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(ChartGridConstants.SPACER_BELOW_AMOUNT_LABEL_DP.dp))
                    Box(
                        modifier = Modifier
                            .height(barH)
                            .fillMaxWidth()
                    ) {
                        if (bidirectional) {
                            val posBase = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            val negBase = if (selected) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            MonthlyBarBidirectional(
                                amount = item.amount,
                                maxAbs = scaleMaxAbs,
                                trackWidth = trackW,
                                positiveBrush = ChartGradientHelper.primaryBarBrush(posBase),
                                negativeBrush = ChartGradientHelper.errorBarBrush(negBase)
                            )
                        } else {
                            val hFrac = ((if (forcePositive) kotlin.math.abs(item.amount) else item.amount) / scaleMaxPos).toFloat().coerceIn(0.05f, 1f)
                            val base = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            val brush = ChartGradientHelper.primaryBarBrush(base)
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(trackW)
                                        .fillMaxHeight(hFrac)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 3.dp, bottomStart = 3.dp))
                                        .background(brush)
                                )
                            }
                        }
                    }
                    Text(
                        text = item.month,
                        fontSize = if (selected) 12.sp else 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (selected) (if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            else ext.textSecondary
                    )
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyBarBidirectional(
    amount: Double,
    maxAbs: Double,
    trackWidth: Dp,
    positiveBrush: Brush,
    negativeBrush: Brush
) {
    val denom = maxAbs.coerceAtLeast(1.0)
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (amount > 0) {
                val f = (amount / denom).toFloat().coerceIn(0.05f, 1f)
                Box(
                    modifier = Modifier
                        .width(trackWidth)
                        .fillMaxHeight(f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 3.dp, bottomStart = 3.dp))
                        .background(positiveBrush)
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (amount < 0) {
                val f = (-amount / denom).toFloat().coerceIn(0.05f, 1f)
                Box(
                    modifier = Modifier
                        .width(trackWidth)
                        .fillMaxHeight(f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(negativeBrush)
                )
            }
        }
    }
}
