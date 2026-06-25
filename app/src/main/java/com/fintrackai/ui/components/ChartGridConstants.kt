package com.fintrackai.ui.components

object ChartGridConstants {
    /** Equal intervals from 0 to max (e.g. 4 → ticks at 0, ¼, ½, ¾, max). */
    const val CHART_Y_UNIFORM_DIVISIONS = 4

    /**
     * Half-axis divisions for bidirectional charts (e.g. 2 → −max, −max/2, 0, max/2, max).
     */
    const val MONTHLY_BIDIRECTIONAL_HALF_DIVISIONS = 2

    /** Must match [HomeDailyExpenseChart] LazyRow `contentPadding` top. */
    const val HOME_DAILY_LAZY_ROW_CONTENT_TOP_DP = 6

    /** Must match [MonthlyExpenseBarChart] LazyRow `contentPadding` top. */
    const val MONTHLY_LAZY_ROW_CONTENT_TOP_DP = 12

    /** Width for the optional Y-axis label column beside horizontal scrolling charts. */
    const val Y_AXIS_LABEL_COLUMN_WIDTH_DP = 38

    /**
     * Fixed height reserved for the amount label above the bar track (2 lines × 12sp lineHeight).
     * The Text composable in each bar column must use Modifier.height(this value.dp) so the offset
     * is exact regardless of system font scale.
     */
    const val CHART_AMOUNT_LABEL_BLOCK_HEIGHT_DP = 24

    /** Spacer between amount label and bar track (must match chart composables). */
    const val SPACER_BELOW_AMOUNT_LABEL_DP = 4

    const val GRID_LINE_ALPHA = 0.14f
    const val Y_AXIS_LABEL_FONT_SP = 9

    /**
     * Offset from each grid line so [Y_AXIS_LABEL_FONT_SP] text is vertically centered on the line (visual match to Canvas).
     * At 9sp font size the rendered line height is ~12dp; half of that (6) plus a 1dp nudge = 7.
     */
    const val Y_AXIS_LABEL_VERT_CENTER_OFFSET_DP = 11
}
