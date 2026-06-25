package com.fintrackai.domain.insights

import com.fintrackai.domain.model.CategoryStat
import com.fintrackai.domain.model.RepeatedMerchant
import com.fintrackai.domain.model.TimeBasedPatterns
import com.fintrackai.domain.model.TopMerchant
import com.fintrackai.domain.model.VelocityData
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInsightGenerator @Inject constructor() {

    private fun fmt(amount: Number): String =
        "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)}"

    private fun pick(templates: List<String>): String {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        return templates[day % templates.size]
    }

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    // ── Spending Velocity ────────────────────────────────────────────

    fun generateVelocityInsight(data: VelocityData): String {
        val spent = data.spentThisMonth
        val daysElapsed = data.daysElapsed
        val daysInMonth = data.daysInMonth
        val dailyRate = if (daysElapsed > 0) spent / daysElapsed else 0.0
        val projected = dailyRate * daysInMonth
        val avg6 = if (data.previousMonthsTotals.isNotEmpty())
            data.previousMonthsTotals.sum() / data.previousMonthsTotals.size else null
        val daysLeft = daysInMonth - daysElapsed

        val line1: String
        val line2: String

        if (avg6 == null) {
            line1 = pick(listOf(
                "You've spent ${fmt(spent.toLong())} in $daysElapsed days so far 📊",
                "$daysElapsed days in and you're at ${fmt(spent.toLong())} 💰",
                "Current month tally: ${fmt(spent.toLong())} across $daysElapsed days 🧮"
            ))
            line2 = pick(listOf(
                "At this pace, you'll hit ~${fmt(projected.toLong())} by month-end. Keep tracking to get better predictions! 🚀",
                "Projected month-end: ~${fmt(projected.toLong())}. More months = smarter insights! 📈",
                "On track for ~${fmt(projected.toLong())} this month. We'll compare with past months once we have more data! 🔮"
            ))
        } else {
            val pctVsAvg = ((projected - avg6) / avg6 * 100).toLong()
            line1 = if (pctVsAvg > 15) {
                pick(listOf(
                    "You're burning through cash! 🔥 ${fmt(spent.toLong())} spent in $daysElapsed days—projected ${fmt(projected.toLong())} by month-end.",
                    "Spending is running hot! ⚡ At ${fmt(dailyRate.toLong())}/day, you're headed for ${fmt(projected.toLong())}.",
                    "Month's not over and you're already at ${fmt(spent.toLong())}. 📈 Projected: ${fmt(projected.toLong())}. Buckle up!"
                ))
            } else if (pctVsAvg < -15) {
                pick(listOf(
                    "You're on a lean streak! 🎉 Projected ${fmt(projected.toLong())} vs your usual ${fmt(avg6.toLong())}.",
                    "Spending is chill this month! 💪 ${fmt(spent.toLong())} so far, tracking well below your ${fmt(avg6.toLong())} average.",
                    "Easy does it! 🌿 At this rate you'll spend ${fmt(projected.toLong())}—that's ${Math.abs(pctVsAvg)}% under your average."
                ))
            } else {
                pick(listOf(
                    "Cruising at a steady pace. 📊 ${fmt(spent.toLong())} in $daysElapsed days, projected ${fmt(projected.toLong())}.",
                    "Right in line with your usual! ⚖️ Projected ${fmt(projected.toLong())} vs average ${fmt(avg6.toLong())}.",
                    "On par with your spending history. 🎯 ${fmt(spent.toLong())} so far, headed for ~${fmt(projected.toLong())}."
                ))
            }

            line2 = pick(listOf(
                "Your 6-month average is ${fmt(avg6.toLong())}. ${if (pctVsAvg > 0) "You're ${pctVsAvg}% above it" else "You're ${Math.abs(pctVsAvg)}% below it"}—$daysLeft days left to steer the ship! 🧭",
                "Past 6 months you averaged ${fmt(avg6.toLong())}/month. ${if (projected > avg6) "This month looks heavier." else "This month looks lighter."} Still $daysLeft days to go! ⏳",
                "Compared to your ${fmt(avg6.toLong())} average: ${if (pctVsAvg > 0) "running ${pctVsAvg}% hotter 🌡️" else "running ${Math.abs(pctVsAvg)}% cooler 🧊"}. $daysLeft days remaining."
            ))
        }

        return "• $line1\n• $line2"
    }

    // ── Weekly Change ────────────────────────────────────────────────

    fun generateWeeklyChangeInsight(
        thisWeekCategories: List<CategoryStat>,
        lastWeekCategories: List<CategoryStat>,
        topMerchant: TopMerchant?
    ): String {
        val thisTotal = thisWeekCategories.sumOf { it.amount }.toLong()
        val lastTotal = lastWeekCategories.sumOf { it.amount }.toLong()

        if (lastTotal == 0L && thisTotal == 0L) {
            return "• No spending recorded for the past two weeks 🤷\n• Start tracking to see weekly trends here!"
        }

        val line1: String
        if (lastTotal == 0L) {
            line1 = pick(listOf(
                "You spent ${fmt(thisTotal)} this week—no last week data to compare yet! 🆕",
                "This week's total: ${fmt(thisTotal)}. First week on record, so no comparison yet 📝",
                "${fmt(thisTotal)} this week. Once we have last week's data, the fun begins! 🎬"
            ))
        } else {
            val changePct = ((thisTotal - lastTotal).toDouble() / lastTotal * 100).toLong()
            line1 = when {
                changePct > 30 -> pick(listOf(
                    "Spending jumped ${changePct}% this week! 📈 ${fmt(thisTotal)} vs ${fmt(lastTotal)} last week.",
                    "Whoa—${changePct}% more than last week! 🚀 You went from ${fmt(lastTotal)} to ${fmt(thisTotal)}.",
                    "This week hit ${fmt(thisTotal)}—that's ${changePct}% above last week's ${fmt(lastTotal)}. 💸 Big week!"
                ))
                changePct > 5 -> pick(listOf(
                    "Spending ticked up ${changePct}% this week. 📊 ${fmt(thisTotal)} vs ${fmt(lastTotal)} last week.",
                    "Slight uptick: ${fmt(thisTotal)} this week vs ${fmt(lastTotal)} last week. ⬆️ +${changePct}%.",
                    "A little more this week—${fmt(thisTotal)} vs ${fmt(lastTotal)}. That's +${changePct}% 📈"
                ))
                changePct < -30 -> pick(listOf(
                    "Major win! Spending dropped ${Math.abs(changePct)}%. 🎉 From ${fmt(lastTotal)} down to ${fmt(thisTotal)}.",
                    "Impressive—${Math.abs(changePct)}% less this week! 💪 ${fmt(lastTotal)} → ${fmt(thisTotal)}.",
                    "Down ${Math.abs(changePct)}% from last week. 📉 ${fmt(thisTotal)} vs ${fmt(lastTotal)}. Keep it up!"
                ))
                changePct < -5 -> pick(listOf(
                    "Spending eased down ${Math.abs(changePct)}%. 👍 ${fmt(thisTotal)} this week vs ${fmt(lastTotal)} last week.",
                    "Slight dip: ${fmt(thisTotal)} vs ${fmt(lastTotal)} last week. 📉 Down ${Math.abs(changePct)}%.",
                    "Nicely done—${Math.abs(changePct)}% less this week at ${fmt(thisTotal)}. 🙌"
                ))
                else -> pick(listOf(
                    "Pretty steady—${fmt(thisTotal)} this week vs ${fmt(lastTotal)} last week. ⚖️",
                    "Almost identical weeks! 🎯 ${fmt(thisTotal)} vs ${fmt(lastTotal)}. Consistency is a superpower.",
                    "Flat week—${fmt(thisTotal)} vs ${fmt(lastTotal)}. No drama here. ✌️"
                ))
            }
        }

        // Find the biggest category change
        val lastMap = lastWeekCategories.associate { it.category to it.amount }
        val thisMap = thisWeekCategories.associate { it.category to it.amount }
        val allCategories = (lastMap.keys + thisMap.keys)

        data class CatChange(val category: String, val thisAmt: Double, val lastAmt: Double, val diff: Double)
        val changes = allCategories.map { cat ->
            val t = thisMap[cat] ?: 0.0
            val l = lastMap[cat] ?: 0.0
            CatChange(cat, t, l, t - l)
        }

        val biggestRise = changes.maxByOrNull { it.diff }
        val biggestDrop = changes.minByOrNull { it.diff }

        val line2: String = when {
            biggestRise != null && biggestRise.diff > 0 -> pick(listOf(
                "Biggest mover: ${biggestRise.category} jumped from ${fmt(biggestRise.lastAmt.toLong())} to ${fmt(biggestRise.thisAmt.toLong())}. 🏷️",
                "${biggestRise.category} saw the biggest spike—up ${fmt(biggestRise.diff.toLong())} from last week. 📌",
                "${biggestRise.category} rose the most: ${fmt(biggestRise.lastAmt.toLong())} → ${fmt(biggestRise.thisAmt.toLong())}. ⬆️"
            ))
            biggestDrop != null && biggestDrop.diff < 0 -> pick(listOf(
                "${biggestDrop.category} dropped the most—down ${fmt(Math.abs(biggestDrop.diff).toLong())} from last week. 🏷️",
                "Biggest saver: ${biggestDrop.category} went from ${fmt(biggestDrop.lastAmt.toLong())} to ${fmt(biggestDrop.thisAmt.toLong())}. 📉",
                "${biggestDrop.category} saw the biggest cut—saved ${fmt(Math.abs(biggestDrop.diff).toLong())} vs last week. ⬇️"
            ))
            topMerchant != null -> pick(listOf(
                "Top merchant this week: ${topMerchant.merchant} at ${fmt(topMerchant.amount.toLong())}. 🏪",
                "${topMerchant.merchant} leads this week's spending at ${fmt(topMerchant.amount.toLong())}. 👑",
                "Your biggest merchant: ${topMerchant.merchant} (${fmt(topMerchant.amount.toLong())}). 🛒"
            ))
            else -> pick(listOf(
                "No major category shifts this week—steady as she goes! ⛵",
                "Categories look pretty balanced this week. 🎯",
                "Nothing dramatic on the category front—smooth sailing! 🌊"
            ))
        }

        return "• $line1\n• $line2"
    }

    // ── Merchant Pattern ─────────────────────────────────────────────

    fun generateMerchantInsight(
        repeatedMerchants: List<RepeatedMerchant>,
        topMerchantsByAmount: List<RepeatedMerchant>
    ): String {
        if (repeatedMerchants.isEmpty() && topMerchantsByAmount.isEmpty()) {
            return "• No merchant patterns detected this month yet 🤷\n• Keep spending (responsibly!) and we'll spot your favorites."
        }

        // Point 1: Highest spend merchant
        val line1: String = if (topMerchantsByAmount.isNotEmpty()) {
            val top = topMerchantsByAmount[0]
            pick(listOf(
                "${top.merchant} is your biggest money magnet this month—${fmt(top.totalAmount.toLong())} across ${top.transactionCount} transactions! 💰",
                "Top spender alert: ${top.merchant} with ${fmt(top.totalAmount.toLong())} (${top.transactionCount} txns). 🏆 They love your wallet!",
                "${top.merchant} takes the crown—${fmt(top.totalAmount.toLong())} spent there in ${top.transactionCount} visits. 👑",
                "Your wallet's favorite destination: ${top.merchant} at ${fmt(top.totalAmount.toLong())} (${top.transactionCount} times). 🎯",
                "${top.merchant} is winning the \"who gets your money\" award: ${fmt(top.totalAmount.toLong())} across ${top.transactionCount} txns! 💸"
            ))
        } else {
            "No clear top merchant by amount yet this month 📊"
        }

        // Point 2: Most frequent merchant
        val mostFrequent = repeatedMerchants.maxByOrNull { it.transactionCount }
        val line2: String = if (mostFrequent != null) {
            pick(listOf(
                "Most visited: ${mostFrequent.merchant} with ${mostFrequent.transactionCount} transactions (${fmt(mostFrequent.totalAmount.toLong())} total). 🔁 Creature of habit? 😏",
                "You keep going back to ${mostFrequent.merchant}—${mostFrequent.transactionCount} times this month! 📍 That's ${fmt(mostFrequent.totalAmount.toLong())} worth of loyalty.",
                "${mostFrequent.merchant} sees you ${mostFrequent.transactionCount}x a month. 🏪 At this point, they should give you a loyalty card! Total: ${fmt(mostFrequent.totalAmount.toLong())}.",
                "${mostFrequent.merchant} is your most frequent stop—${mostFrequent.transactionCount} visits, ${fmt(mostFrequent.totalAmount.toLong())} spent. 🔄 Old faithful!",
                "Can't stay away from ${mostFrequent.merchant}! 📌 ${mostFrequent.transactionCount} transactions totaling ${fmt(mostFrequent.totalAmount.toLong())} this month."
            ))
        } else {
            pick(listOf(
                "No merchant with 5+ visits this month—you're spreading the love! 🌈",
                "No repeat-offender merchants yet. Variety is the spice of life! 🌶️",
                "You haven't hit 5+ visits at any single spot this month. Explorer mode! 🗺️"
            ))
        }

        return "• $line1\n• $line2"
    }

    // ── Time Analysis ────────────────────────────────────────────────

    fun generateTimeAnalysisInsight(patterns: TimeBasedPatterns): String {
        val peak = patterns.peakSpendingHour
        val weekday = patterns.weekdaySpending
        val weekend = patterns.weekendSpending

        if (weekday.count == 0 && weekend.count == 0) {
            return "• Not enough spending data for time analysis yet ⏳\n• Keep tracking and we'll reveal your spending rhythms!"
        }

        // Point 1: Peak spending time window
        val line1: String = if (peak.hour >= 0 && peak.count > 0) {
            val hourStr = formatHour(peak.hour)
            val topHours = patterns.timeDistribution
                .sortedByDescending { it.totalAmount }
                .take(3)
            val windowDesc = if (topHours.size >= 2) {
                val hours = topHours.map { it.hour }.sorted()
                "${formatHour(hours.first())}–${formatHour(hours.last())}"
            } else hourStr

            pick(listOf(
                "Your wallet wakes up around $windowDesc—that's when most of your spending happens! ⏰",
                "Peak spending hour: $hourStr with ${peak.count} transactions worth ${fmt(peak.totalAmount.toLong())}. 🕐",
                "The cash register rings loudest at $hourStr! 🔔 ${peak.count} txns totaling ${fmt(peak.totalAmount.toLong())}.",
                "Your spending peaks around $hourStr—${peak.count} transactions and ${fmt(peak.totalAmount.toLong())} gone! ⚡",
                "$hourStr is your prime spending time: ${peak.count} transactions, ${fmt(peak.totalAmount.toLong())} spent. 🎯"
            ))
        } else {
            "No clear peak spending hour detected yet ⏳"
        }

        // Point 2: Weekend vs Weekday
        val totalTxns = weekday.count + weekend.count
        val weekdayPct = if (totalTxns > 0) (weekday.count.toDouble() / totalTxns * 100).toLong() else 0L
        val weekendPct = if (totalTxns > 0) (weekend.count.toDouble() / totalTxns * 100).toLong() else 0L

        val weekdayPerDay = if (weekday.count > 0) weekday.totalAmount / 5 else 0.0
        val weekendPerDay = if (weekend.count > 0) weekend.totalAmount / 2 else 0.0

        val line2: String = when {
            weekendPerDay > weekdayPerDay * 1.5 -> pick(listOf(
                "Weekends are your splurge zone! 🎉 ${fmt(weekend.totalAmount.toLong())} on weekends vs ${fmt(weekday.totalAmount.toLong())} on weekdays. YOLO mode activated!",
                "Weekend you spends ~${fmt(weekendPerDay.toLong())}/day vs weekday's ~${fmt(weekdayPerDay.toLong())}/day. 🍾 Weekend warrior alert!",
                "${weekendPct}% of transactions happen on weekends, and they're heavier too—${fmt(weekend.totalAmount.toLong())} vs ${fmt(weekday.totalAmount.toLong())}. 📊",
                "Weekends = party mode! 💃 You spend ${fmt(weekendPerDay.toLong())}/day on weekends vs ${fmt(weekdayPerDay.toLong())}/day on weekdays.",
                "Weekend spending per day is way higher! 🎊 ~${fmt(weekendPerDay.toLong())} vs ~${fmt(weekdayPerDay.toLong())} on weekdays. Weekend you means business!"
            ))
            weekdayPerDay > weekendPerDay * 1.5 -> pick(listOf(
                "Weekdays drain your wallet more! 💼 ${fmt(weekday.totalAmount.toLong())} vs ${fmt(weekend.totalAmount.toLong())} on weekends.",
                "Working days = spending days. 📋 ~${fmt(weekdayPerDay.toLong())}/day vs ~${fmt(weekendPerDay.toLong())}/day on weekends.",
                "Weekday spending dominates at ${weekdayPct}% of transactions. 🏢 Office lunches adding up?",
                "Your weekday spend per day (~${fmt(weekdayPerDay.toLong())}) outpaces weekends (~${fmt(weekendPerDay.toLong())}). ⚡ Hustle costs!",
                "Most of your money flows on weekdays: ${fmt(weekday.totalAmount.toLong())} vs ${fmt(weekend.totalAmount.toLong())} on weekends. 📊"
            ))
            else -> pick(listOf(
                "Weekday and weekend spending are pretty balanced—~${fmt(weekdayPerDay.toLong())}/day vs ~${fmt(weekendPerDay.toLong())}/day. ⚖️ Zen mode! 🧘",
                "Your spending doesn't discriminate—weekdays (${fmt(weekday.totalAmount.toLong())}) and weekends (${fmt(weekend.totalAmount.toLong())}) are neck and neck. 🎯",
                "Even-steven! ~${fmt(weekdayPerDay.toLong())}/day on weekdays, ~${fmt(weekendPerDay.toLong())}/day on weekends. 📊 Consistency king! 👑",
                "Balanced spender! Weekdays: ${fmt(weekday.totalAmount.toLong())}, Weekends: ${fmt(weekend.totalAmount.toLong())}. ⚖️ No favorites here.",
                "Weekdays and weekends get equal love from your wallet. 🤝 ${fmt(weekday.totalAmount.toLong())} vs ${fmt(weekend.totalAmount.toLong())}."
            ))
        }

        return "• $line1\n• $line2"
    }
}
