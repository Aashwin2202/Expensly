package com.fintrackai.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object ChartGradientHelper {
    fun verticalBarBrush(top: Color, bottom: Color): Brush =
        Brush.verticalGradient(colors = listOf(top, bottom))

    /** Single-hue bar fill: lighter at top, deeper toward the baseline. */
    fun primaryBarBrush(base: Color): Brush = verticalBarBrush(
        base.copy(alpha = 0.94f),
        base.copy(alpha = 0.48f)
    )

    fun errorBarBrush(base: Color): Brush = verticalBarBrush(
        base.copy(alpha = 0.92f),
        base.copy(alpha = 0.45f)
    )
}
