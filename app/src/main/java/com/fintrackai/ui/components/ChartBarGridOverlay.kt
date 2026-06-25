package com.fintrackai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.domain.format.AmountCompactFormatHelper

/**
 * Full-width horizontal grid for the bar track, drawn once behind the scrolling columns
 * so lines do not break across item gaps.
 */
@Composable
fun UnidirectionalBarGridCanvas(
    viewportMax: Double,
    barHeight: Dp,
    ticks: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val m = viewportMax.coerceAtLeast(1.0)
    Canvas(modifier = modifier.fillMaxWidth().height(barHeight)) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        ticks.forEach { v ->
            val frac = (v / m).toFloat().coerceIn(0f, 1f)
            val y = h * (1f - frac)
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = stroke
            )
        }
    }
}

@Composable
fun BidirectionalBarGridCanvas(
    maxAbs: Double,
    barHeight: Dp,
    tickAmounts: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val m = maxAbs.coerceAtLeast(1.0)
    Canvas(modifier = modifier.fillMaxWidth().height(barHeight)) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        tickAmounts.forEach { amt ->
            val frac = (amt / m).toFloat().coerceIn(-1f, 1f)
            val yFromTop = h * (0.5f - frac / 2f)
            drawLine(
                color = lineColor,
                start = Offset(0f, yFromTop),
                end = Offset(w, yFromTop),
                strokeWidth = stroke
            )
        }
    }
}

@Composable
fun UnidirectionalYAxisLabels(
    viewportMax: Double,
    barHeight: Dp,
    ticks: List<Double>,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val m = viewportMax.coerceAtLeast(1.0)
    val labelCenter = ChartGridConstants.Y_AXIS_LABEL_VERT_CENTER_OFFSET_DP.dp
    Box(modifier = modifier.height(barHeight).fillMaxWidth()) {
        ticks.forEach { v ->
            val frac = (v / m).toFloat().coerceIn(0f, 1f)
            // Same Y as [UnidirectionalBarGridCanvas]: line at (1-frac) from bottom → top origin: barHeight * (1-frac)
            val yFromTop = barHeight * (1f - frac)
            Text(
                text = AmountCompactFormatHelper.formatCompactWithRupee(v),
                color = labelColor,
                fontSize = ChartGridConstants.Y_AXIS_LABEL_FONT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(end = 2.dp)
                    .offset(y = yFromTop - labelCenter)
            )
        }
    }
}

@Composable
fun BidirectionalYAxisLabels(
    maxAbs: Double,
    barHeight: Dp,
    tickAmounts: List<Double>,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val m = maxAbs.coerceAtLeast(1.0)
    val labelCenter = ChartGridConstants.Y_AXIS_LABEL_VERT_CENTER_OFFSET_DP.dp
    Box(modifier = modifier.height(barHeight).fillMaxWidth()) {
        tickAmounts.forEach { amt ->
            val frac = (amt / m).toFloat().coerceIn(-1f, 1f)
            val yFromTop = barHeight * (0.5f - frac / 2f)
            Text(
                text = AmountCompactFormatHelper.formatCompactWithRupee(amt),
                color = labelColor,
                fontSize = ChartGridConstants.Y_AXIS_LABEL_FONT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(end = 2.dp)
                    .offset(y = yFromTop - labelCenter)
            )
        }
    }
}
