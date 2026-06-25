package com.fintrackai

import com.fintrackai.domain.insights.DailySummaryGenerator
import org.junit.Assert.*
import org.junit.Test

class DailySummaryTest {

    @Test
    fun `daily summary with no spending`() {
        val result = DailySummaryGenerator.getDailySummaryText(0.0, listOf(100.0, 200.0), null)
        assertTrue(result.contains("₹0"))
    }

    @Test
    fun `daily summary with budget context`() {
        val ctx = DailySummaryGenerator.BudgetContext(remaining = 5000.0, daysLeftInMonth = 10)
        val result = DailySummaryGenerator.getDailySummaryText(500.0, listOf(400.0, 600.0), ctx)
        assertTrue(result.contains("₹"))
        assertTrue(result.contains("•"))
    }

    @Test
    fun `daily summary over budget`() {
        val ctx = DailySummaryGenerator.BudgetContext(remaining = -1000.0, daysLeftInMonth = 5)
        val result = DailySummaryGenerator.getDailySummaryText(500.0, listOf(300.0), ctx)
        assertTrue(result.contains("budget") || result.contains("over") || result.contains("Budget"))
    }

    @Test
    fun `daily summary without budget motivates`() {
        val result = DailySummaryGenerator.getDailySummaryText(500.0, listOf(300.0, 400.0), null)
        assertTrue(result.contains("budget") || result.contains("Budget"))
    }
}
