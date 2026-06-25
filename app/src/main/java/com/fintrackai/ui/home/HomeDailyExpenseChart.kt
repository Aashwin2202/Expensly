package com.fintrackai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.fintrackai.domain.format.AmountCompactFormatHelper
import com.fintrackai.domain.model.DailyExpenseDay
import com.fintrackai.ui.components.ChartBarTrackLayoutHelper
import com.fintrackai.ui.components.ChartGradientHelper
import com.fintrackai.ui.components.ChartGridConstants
import com.fintrackai.ui.components.ChartYAxisTicksHelper
import com.fintrackai.ui.components.GraphScrollScaleHelper
import com.fintrackai.ui.components.UnidirectionalBarGridCanvas
import com.fintrackai.ui.components.UnidirectionalYAxisLabels
import com.fintrackai.ui.theme.ExtendedColors

@Composable
fun HomeDailyExpenseBarChart(
    days: List<DailyExpenseDay>,
    ext: ExtendedColors,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    if (days.isEmpty()) return

    val viewportMax by remember(days, listState) {
        derivedStateOf {
            GraphScrollScaleHelper.viewportMaxNonNegative(days, listState) { d ->
                if (d.isFuture) 0.0 else d.amount
            }
        }
    }
    val barMaxH = HomeConstants.DAILY_EXPENSE_BAR_MAX_HEIGHT_DP.dp
    val cellW = HomeConstants.DAILY_EXPENSE_CELL_WIDTH_DP.dp
    val trackW = HomeConstants.DAILY_EXPENSE_BAR_TRACK_WIDTH_DP.dp
    val density = LocalDensity.current
    val ticks = remember(viewportMax) {
        ChartYAxisTicksHelper.ticksUniformZeroToMax(viewportMax, ChartGridConstants.CHART_Y_UNIFORM_DIVISIONS)
    }
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ChartGridConstants.GRID_LINE_ALPHA)
    val barTrackTopOffset =
        ChartBarTrackLayoutHelper.offsetFromRowTopToBarTrack(ChartGridConstants.HOME_DAILY_LAZY_ROW_CONTENT_TOP_DP)

    val monthAnchor = days.firstOrNull()?.dateKey
    LaunchedEffect(days.size, monthAnchor) {
        if (days.isEmpty()) return@LaunchedEffect
        val todayIdx = days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: (days.size - 1).coerceAtLeast(0)
        var viewportPx = 0
        repeat(40) {
            viewportPx = listState.layoutInfo.viewportSize.width
            if (viewportPx > 0) return@repeat
            delay(16L)
        }
        if (viewportPx <= 0) return@LaunchedEffect
        val cellPx = with(density) { cellW.roundToPx() }
        val gapPx = with(density) { HomeConstants.DAILY_EXPENSE_CHART_ITEM_SPACING_DP.dp.roundToPx() }
        val padStartPx = with(density) { HomeConstants.DAILY_EXPENSE_LAZY_ROW_PADDING_START_DP.dp.roundToPx() }
        val padEndPx = with(density) { HomeConstants.DAILY_EXPENSE_LAZY_ROW_PADDING_END_DP.dp.roundToPx() }
        val usablePx = (viewportPx - padStartPx - padEndPx).coerceAtLeast(cellPx)
        val slotPx = (cellPx + gapPx).coerceAtLeast(1)
        // n columns need n * cell + (n-1) * gap <= usable ⇔ n <= (usable + gap) / (cell + gap)
        val columnsFit = ((usablePx + gapPx) / slotPx).coerceAtLeast(1)
        val firstIndex = (todayIdx - (columnsFit - 1)).coerceAtLeast(0)
        listState.scrollToItem(firstIndex, scrollOffset = 0)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.width(ChartGridConstants.Y_AXIS_LABEL_COLUMN_WIDTH_DP.dp)
                ) {
                    Spacer(Modifier.height(barTrackTopOffset))
                    UnidirectionalYAxisLabels(
                        viewportMax = viewportMax,
                        barHeight = barMaxH,
                        ticks = ticks,
                        labelColor = ext.textSecondary.copy(alpha = 0.88f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    UnidirectionalBarGridCanvas(
                        viewportMax = viewportMax,
                        barHeight = barMaxH,
                        ticks = ticks,
                        lineColor = gridLineColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = barTrackTopOffset)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = HomeConstants.DAILY_EXPENSE_LAZY_ROW_PADDING_START_DP.dp,
                            top = ChartGridConstants.HOME_DAILY_LAZY_ROW_CONTENT_TOP_DP.dp,
                            end = HomeConstants.DAILY_EXPENSE_LAZY_ROW_PADDING_END_DP.dp,
                            bottom = ChartGridConstants.HOME_DAILY_LAZY_ROW_CONTENT_TOP_DP.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(HomeConstants.DAILY_EXPENSE_CHART_ITEM_SPACING_DP.dp)
                    ) {
                        items(days, key = { it.dateKey }) { d ->
                    val clickable = !d.isFuture
                    val hFrac = when {
                        d.isFuture -> 0.05f
                        d.amount <= 0 -> 0.05f
                        else -> (d.amount / viewportMax).toFloat().coerceIn(0.08f, 1f)
                    }
                    Column(
                        modifier = Modifier
                            .width(cellW)
                            .then(
                                if (clickable) Modifier.clickable { onDayClick(d.dateKey) }
                                else Modifier
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val primary = MaterialTheme.colorScheme.primary
                        val todayHighlight = ext.accent
                        val amountLabel = if (d.isFuture) "—" else AmountCompactFormatHelper.formatCompactWithRupee(d.amount)
                        val labelColor = when {
                            d.isFuture -> ext.textSecondary.copy(alpha = 0.5f)
                            d.isToday -> todayHighlight
                            else -> ext.text
                        }
                        Text(
                            text = amountLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor,
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
                                .height(barMaxH)
                                .fillMaxWidth()
                        ) {
                            if (d.isFuture) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .width(trackW)
                                        .fillMaxHeight(hFrac)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomEnd = 3.dp, bottomStart = 3.dp))
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                )
                            } else {
                                val base = if (d.isToday) todayHighlight else primary.copy(alpha = 0.78f)
                                val brush = ChartGradientHelper.primaryBarBrush(base)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .width(trackW)
                                        .fillMaxHeight(hFrac)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomEnd = 3.dp, bottomStart = 3.dp))
                                        .background(brush)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${d.dayOfMonth}",
                            fontSize = if (d.isToday) 12.sp else 11.sp,
                            fontWeight = if (d.isToday) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                d.isFuture -> ext.textSecondary.copy(alpha = 0.45f)
                                d.isToday -> todayHighlight
                                else -> ext.textSecondary
                            }
                        )
                    }
                        }
                    }
                }
            }
        }
    }
}
