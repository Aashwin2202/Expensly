package com.fintrackai.domain.insights

import com.fintrackai.data.remote.ChatMessage
import com.fintrackai.data.remote.GroqApiService
import com.fintrackai.data.remote.GroqRequest
// TODO: Re-enable for AI insights
// import com.fintrackai.domain.model.CategoryStat
// import com.fintrackai.domain.model.RepeatedMerchant
// import com.fintrackai.domain.model.TimeBasedPatterns
// import com.fintrackai.domain.model.TopMerchant
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class InsightGenerator @Inject constructor(
    private val groqApi: GroqApiService,
    @Named("groqApiKey") private val apiKey: String
) {
    private fun formatIndian(amount: Number): String =
        NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)

    private suspend fun chat(messages: List<ChatMessage>, temperature: Double = 0.0): String {
        val response = groqApi.chat(
            auth = "Bearer $apiKey",
            request = GroqRequest(messages = messages, temperature = temperature)
        )
        return response.choices.firstOrNull()?.message?.content?.trim() ?: ""
    }

    suspend fun generateSQL(userInput: String): String {
        val systemPrompt = """
You are an expert SQLite assistant.

SQLite schema:
CREATE TABLE transactions (
  id TEXT PRIMARY KEY,
  merchant TEXT,
  amount REAL,
  type TEXT CHECK (type IN ('debit','credit')),
  category TEXT,
  date TEXT,
  time TEXT,
  accounts TEXT
);

CRITICAL SQLite Rules:
- Generate ONLY SELECT queries
- NEVER use INSERT, UPDATE, DELETE, DROP, ALTER
- Use only columns listed above
- Use SUM(amount) for totals
- Debit = money spent, Credit = money received

SQLite Date Functions (MUST USE THESE):
- strftime('%Y', date) returns year as '2024'
- strftime('%m', date) returns month as '01'-'12'
- strftime('%Y-%m', date) returns '2024-12'
- date('now') returns current date in YYYY-MM-DD
- date('now', '-1 month') returns last month's date
- IMPORTANT: When using strftime with date(), use: strftime('%Y-%m', date('now', '-1 month'))
- NEVER use: strftime('%Y-%m', 'now', '-1 month') - this is WRONG
- Use || for string concatenation (NOT CONCAT function)

Date Query Examples:
- Last month: date >= date('now', 'start of month', '-1 month') AND date < date('now', 'start of month')
- Current month: date >= date('now', 'start of month') AND date < date('now', 'start of month', '+1 month')

Return ONLY raw SQL, no markdown, no comments, no explanation

User question:
"$userInput"
""".trimIndent()

        return chat(
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userInput)
            )
        )
    }

    fun isSafeSQL(query: String): Boolean {
        val forbidden = listOf("INSERT", "UPDATE", "DELETE", "DROP", "ALTER")
        return forbidden.none { query.uppercase().contains(it) }
    }

    suspend fun summarizeResult(userInput: String, rows: List<Map<String, Any?>>): String {
        val systemPrompt = """
You are a personal finance assistant.

Instructions:
- Answer in clear, friendly English
- Prefix money with ₹
- Format amounts in Indian numbering system (e.g., 231423 as "2,31,423")
- Debit means spending, credit means income
- If rows are empty, say no matching transactions were found
- Do NOT mention SQL or databases
""".trimIndent()

        return chat(
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", "User question:\n$userInput\n\nSQL result:\n${rows}")
            ),
            0.4
        )
    }

    // TODO: Re-enable the methods below for AI-powered insights.
    // Currently using LocalInsightGenerator for offline, template-based insights.

    /*
    suspend fun generateDailySpendingInsight(
        today: Double,
        pastWeeks: List<Double>,
        monthlyBudget: Double?,
        spentThisMonth: Double,
        daysLeftInMonth: Int
    ): String {
        val hasBudget = monthlyBudget != null && monthlyBudget > 0
        val systemPrompt = if (hasBudget) """
You are a personal finance assistant that provides a concise Daily Summary.
Instructions:
- Generate a short one or two sentence summary.
- First show today's spending compared to average, then how much money is LEFT to spend this month.
- Be conversational and encouraging. Format amounts in Indian numbering system.
- Use emojis sparingly (0-1 emoji max). Prefix money with ₹
- Do NOT mention SQL, databases, or technical details
""".trimIndent() else """
You are a personal finance assistant that provides a concise Daily Summary.
Instructions:
- The user has NOT set a monthly budget yet.
- Generate a short summary that MOTIVATES them to set a budget.
- Be friendly and encouraging. Use emojis sparingly (0-1 emoji max).
- Do NOT mention SQL, databases, or technical details
""".trimIndent()

        val avg = if (pastWeeks.isNotEmpty()) pastWeeks.sum().toLong() / pastWeeks.size else 0L
        val nonZero = pastWeeks.filter { it > 0 }
        val avgNonZero = if (nonZero.isNotEmpty()) nonZero.sum().toLong() / nonZero.size else 0L

        var content = """
Today's spending: ₹${formatIndian(today.toLong())}
Past 8 weeks (same day): $pastWeeks
Average: ₹${formatIndian(avg)}
Average (with spending): ₹${formatIndian(avgNonZero)}
""".trimIndent()

        if (hasBudget) {
            val remaining = maxOf(0.0, monthlyBudget!! - spentThisMonth)
            content += "\nMonthly budget: ₹${formatIndian(monthlyBudget.toLong())}"
            content += "\nSpent: ₹${formatIndian(spentThisMonth.toLong())}"
            content += "\nLeft: ₹${formatIndian(remaining.toLong())}"
            content += "\nDays left: $daysLeftInMonth"
        }

        return chat(listOf(ChatMessage("system", systemPrompt), ChatMessage("user", content)), 0.6)
    }

    suspend fun generateWeeklyChangeInsight(
        thisWeekCategories: List<CategoryStat>,
        lastWeekCategories: List<CategoryStat>,
        topMerchant: TopMerchant?
    ): String {
        val systemPrompt = """
You are a personal finance assistant. Generate ONLY a single short one-liner (max 20-25 words) about spending changes.
Be conversational. Use emojis (1-2 max). Prefix money with ₹. Format in Indian numbering.
""".trimIndent()

        val thisTotal = thisWeekCategories.sumOf { it.amount }
        val lastTotal = lastWeekCategories.sumOf { it.amount }
        val content = """
This Week Total: ₹${formatIndian(thisTotal.toLong())}
Last Week Total: ₹${formatIndian(lastTotal.toLong())}
This Week: $thisWeekCategories
Last Week: $lastWeekCategories
Top Merchant: ${topMerchant?.let { "${it.merchant}: ₹${formatIndian(it.amount.toLong())}" } ?: "None"}
""".trimIndent()

        return chat(listOf(ChatMessage("system", systemPrompt), ChatMessage("user", content)), 0.7)
    }

    suspend fun generateRepeatedMerchantInsight(
        repeatedMerchants: List<RepeatedMerchant>
    ): String {
        if (repeatedMerchants.isEmpty()) return "No repeated spending patterns detected this month. Great job managing your expenses!"

        val systemPrompt = """
You are a personal finance assistant. Generate ONLY a single short one-liner (max 20-25 words).
Highlight the merchant with most spending frequency and amount. Use emojis (1-2 max). Prefix money with ₹.
""".trimIndent()

        val top = repeatedMerchants[0]
        val content = """
Repeated Merchants: $repeatedMerchants
Top: ${top.merchant}, ${top.transactionCount} times, ₹${formatIndian(top.totalAmount.toLong())}
""".trimIndent()

        return chat(listOf(ChatMessage("system", systemPrompt), ChatMessage("user", content)), 0.7)
    }

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    suspend fun generateTimeBasedPatternInsight(
        patterns: TimeBasedPatterns
    ): String {
        val systemPrompt = """
You are a personal finance assistant. Generate ONLY a single short one-liner (max 25-30 words).
Highlight interesting time-based patterns. Use emojis (1-2 max). Prefix money with ₹. Format in Indian numbering.
""".trimIndent()

        val top5 = patterns.timeDistribution
            .sortedByDescending { it.totalAmount }
            .take(5)
            .joinToString("\n") { "  ${formatHour(it.hour)}: ₹${formatIndian(it.totalAmount.toLong())} (${it.count} txns)" }

        val content = """
Late Night (10PM-4AM): ${patterns.lateNightSpending.count} txns, ₹${formatIndian(patterns.lateNightSpending.totalAmount.toLong())}, ${patterns.lateNightSpending.percentage}%
Weekday: ${patterns.weekdaySpending.count} txns, ₹${formatIndian(patterns.weekdaySpending.totalAmount.toLong())}, ${patterns.weekdaySpending.percentage}%
Weekend: ${patterns.weekendSpending.count} txns, ₹${formatIndian(patterns.weekendSpending.totalAmount.toLong())}, ${patterns.weekendSpending.percentage}%
Peak Hour: ${if (patterns.peakSpendingHour.hour >= 0) formatHour(patterns.peakSpendingHour.hour) else "N/A"}, ${patterns.peakSpendingHour.count} txns, ₹${formatIndian(patterns.peakSpendingHour.totalAmount.toLong())}
Top 5 Hours:
$top5
""".trimIndent()

        return chat(listOf(ChatMessage("system", systemPrompt), ChatMessage("user", content)), 0.7)
    }

    suspend fun generateVelocityInsight(
        spentThisMonth: Double,
        daysElapsed: Int,
        daysInMonth: Int,
        previousMonthsTotals: List<Double>
    ): String {
        val systemPrompt = """
You are a personal finance assistant. Generate ONLY a single short one-liner (max 25-30 words).
Predict end-of-month spend at current pace vs last 6 months average.
Use emojis (1-2 max). Prefix money with ₹. Format in Indian numbering.
""".trimIndent()

        val dailyRate = if (daysElapsed > 0) spentThisMonth / daysElapsed else 0.0
        val projected = dailyRate * daysInMonth
        val avg6 = if (previousMonthsTotals.isNotEmpty())
            previousMonthsTotals.sum() / previousMonthsTotals.size else null

        var content = "Spent: ₹${formatIndian(spentThisMonth.toLong())} over $daysElapsed days. Projected: ₹${formatIndian(projected.toLong())}"
        if (avg6 != null) content += ". Last 6 months avg: ₹${formatIndian(avg6.toLong())}"

        return chat(listOf(ChatMessage("system", systemPrompt), ChatMessage("user", content)), 0.6)
    }
    */
}
