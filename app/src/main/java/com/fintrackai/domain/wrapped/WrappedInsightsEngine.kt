package com.fintrackai.domain.wrapped

import com.fintrackai.data.local.db.DayTotalRow
import com.fintrackai.data.local.db.TimeDistRow
import com.fintrackai.domain.model.CategoryStat
import com.fintrackai.domain.model.MerchantStat
import com.fintrackai.domain.model.MonthlyStats
import com.fintrackai.domain.model.Transaction
import java.text.SimpleDateFormat
import java.util.Locale

/** All computed insights for one Wrapped month. */
data class WrappedInsights(
    val monthDisplayName: String,
    val isFullMonth: Boolean,
    val totalSpend: Double,
    val totalIncome: Double,
    val totalTransactions: Int,
    val avgPerDay: Double,
    val daysElapsed: Int,
    val spendChangePercent: Double,       // +ve = increased, -ve = decreased
    val topCategory: CategoryInsight?,
    val topMerchant: MerchantInsight?,
    val mostFrequentMerchant: MerchantInsight?,
    val mostExpensiveDay: DayInsight?,
    val largestTransaction: TransactionInsight?,
    val spendingTimePattern: TimePatternInsight,
)

data class CategoryInsight(
    val name: String,
    val amount: Double,
    val sharePercent: Double
)

data class MerchantInsight(
    val name: String,
    val amount: Double,
    val transactionCount: Int
)

data class DayInsight(
    val date: String,
    val displayDate: String,
    val amount: Double
)

data class TransactionInsight(
    val amount: Double,
    val merchant: String,
    val category: String
)

data class TimePatternInsight(
    val dominantPeriod: String,   // "Morning", "Afternoon", "Evening", "Night"
    val emoji: String,
    val buckets: Map<String, TimeBucket>
)

data class TimeBucket(
    val count: Int,
    val amount: Double,
    val percent: Double
)

object WrappedInsightsEngine {

    fun compute(
        month: WrappedMonthHelper.WrappedMonth,
        stats: MonthlyStats,
        prevStats: MonthlyStats,
        categoryStats: List<CategoryStat>,
        merchantStats: List<MerchantStat>,
        transactions: List<Transaction>,
        mostExpensiveDay: DayTotalRow?,
        timeDistribution: List<TimeDistRow>
    ): WrappedInsights {
        val totalSpend = stats.expense
        val totalIncome = stats.income
        val totalTx = transactions.size
        val avgPerDay = if (month.daysElapsed > 0) totalSpend / month.daysElapsed else 0.0

        val prevSpend = prevStats.expense
        val changePercent = if (prevSpend > 0) ((totalSpend - prevSpend) / prevSpend) * 100 else 0.0

        val topCategory = computeTopCategory(categoryStats, totalSpend)
        val topMerchant = computeTopMerchant(merchantStats)
        val mostFrequentMerchant = computeMostFrequentMerchant(merchantStats, topMerchant)
        val expensiveDay = computeMostExpensiveDay(mostExpensiveDay)
        val largest = computeLargestTransaction(transactions)
        val timePattern = computeTimePattern(timeDistribution)

        return WrappedInsights(
            monthDisplayName = month.displayName,
            isFullMonth = month.isFullMonth,
            totalSpend = totalSpend,
            totalIncome = totalIncome,
            totalTransactions = totalTx,
            avgPerDay = avgPerDay,
            daysElapsed = month.daysElapsed,
            spendChangePercent = changePercent,
            topCategory = topCategory,
            topMerchant = topMerchant,
            mostFrequentMerchant = mostFrequentMerchant,
            mostExpensiveDay = expensiveDay,
            largestTransaction = largest,
            spendingTimePattern = timePattern,
        )
    }

    private fun computeTopCategory(stats: List<CategoryStat>, totalSpend: Double): CategoryInsight? {
        // Skip "others" to find a meaningful top category
        val top = stats.firstOrNull { it.category.lowercase() != "investment" } ?: stats.firstOrNull() ?: return null
        val share = if (totalSpend > 0) (top.amount / totalSpend) * 100 else 0.0
        return CategoryInsight(
            name = top.category.replaceFirstChar { it.titlecase(Locale.getDefault()) },
            amount = top.amount,
            sharePercent = share
        )
    }

    private fun computeTopMerchant(stats: List<MerchantStat>): MerchantInsight? {
        // Pick by highest spend
        val top = stats.firstOrNull() ?: return null
        return MerchantInsight(
            name = top.merchant,
            amount = top.amount,
            transactionCount = top.transactionCount
        )
    }

    private fun computeMostFrequentMerchant(stats: List<MerchantStat>, topBySpend: MerchantInsight?): MerchantInsight? {
        // Pick by highest transaction count, preferring a different merchant than topBySpend
        val sorted = stats.sortedByDescending { it.transactionCount }
        val top = sorted.firstOrNull { it.merchant != topBySpend?.name } ?: sorted.firstOrNull() ?: return null
        return MerchantInsight(
            name = top.merchant,
            amount = top.amount,
            transactionCount = top.transactionCount
        )
    }

    private fun computeMostExpensiveDay(row: DayTotalRow?): DayInsight? {
        row ?: return null
        val displayDate = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
            outFmt.format(sdf.parse(row.date)!!)
        } catch (_: Exception) { row.date }
        return DayInsight(date = row.date, displayDate = displayDate, amount = row.total)
    }

    private fun computeLargestTransaction(transactions: List<Transaction>): TransactionInsight? {
        val tx = transactions.maxByOrNull { it.amount } ?: return null
        return TransactionInsight(
            amount = tx.amount,
            merchant = tx.merchant,
            category = tx.category.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        )
    }

    private fun computeTimePattern(distribution: List<TimeDistRow>): TimePatternInsight {
        // Buckets: Morning 6-12, Afternoon 12-17, Evening 17-21, Night 21-6
        val bucketMap = mutableMapOf(
            "Morning" to Pair(0, 0.0),
            "Afternoon" to Pair(0, 0.0),
            "Evening" to Pair(0, 0.0),
            "Night" to Pair(0, 0.0)
        )
        for (row in distribution) {
            val bucket = when (row.hour) {
                in 6..11 -> "Morning"
                in 11..15 -> "Afternoon"
                in 16..20 -> "Evening"
                else -> "Night"
            }
            val (c, a) = bucketMap[bucket]!!
            bucketMap[bucket] = Pair(c + row.count, a + row.totalAmount)
        }
        val totalCount = bucketMap.values.sumOf { it.first }.coerceAtLeast(1)
        val result = bucketMap.mapValues { (_, v) ->
            TimeBucket(v.first, v.second, (v.first.toDouble() / totalCount) * 100)
        }
        val dominant = result.maxByOrNull { it.value.count }?.key ?: "Morning"
        val emoji = when (dominant) {
            "Morning" -> "☀️"
            "Afternoon" -> "🌤️"
            "Evening" -> "🌆"
            else -> "🌙"
        }
        return TimePatternInsight(dominantPeriod = dominant, emoji = emoji, buckets = result)
    }

}
