package com.fintrackai.ui.home

object HomeConstants {
    const val RECENT_TRANSACTIONS_LIMIT = 5
    /** Column width per day: amount label uses full width; bar is narrower and centered. */
    const val DAILY_EXPENSE_CELL_WIDTH_DP = 48
    /** Colored daily bar width (centered in the cell). */
    const val DAILY_EXPENSE_BAR_TRACK_WIDTH_DP = 30
    const val DAILY_EXPENSE_BAR_MAX_HEIGHT_DP = 120
    /** Horizontal spacing between day columns in the daily chart LazyRow (must match `spacedBy`). */
    const val DAILY_EXPENSE_CHART_ITEM_SPACING_DP = 8
    /** LazyRow `contentPadding` horizontal values (must match HomeDailyExpenseBarChart). */
    const val DAILY_EXPENSE_LAZY_ROW_PADDING_START_DP = 20
    const val DAILY_EXPENSE_LAZY_ROW_PADDING_END_DP = 40
    const val MONTHLY_SUMMARY_TITLE_SP = 26
    const val MONTHLY_SUMMARY_SUBTITLE_SP = 14
}
