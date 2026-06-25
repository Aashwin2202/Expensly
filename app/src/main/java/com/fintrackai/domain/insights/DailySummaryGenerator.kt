package com.fintrackai.domain.insights

import java.text.NumberFormat
import java.util.Locale

object DailySummaryGenerator {
    private fun formatAmount(amount: Double): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(Math.round(amount))
    }

    private val DAILY_SUMMARY_NO_SPEND = listOf(
        "₹0 today—your wallet is taking a well-deserved nap. 😴 Let it sleep!",
        "Zero rupees spent today. 🙌 Are you a monk or just really good at saying no?",
        "Today's spend: ₹0. The budget gods are smiling down on you. 😇"
    )

    private val DAILY_SUMMARY_LESS = listOf(
        "You've spent ₹{amount} today—under your usual average. 🎉 Keep it up!",
        "₹{amount} today is below average. 💪 More like above average at self-control.",
        "Only ₹{amount} today. 😊 The savings account is smiling in the corner."
    )

    private val DAILY_SUMMARY_EQUAL = listOf(
        "You've spent ₹{amount} today—right on the money (literally). ⚖️",
        "₹{amount} today, perfectly in line with your average. 🎯 Consistency wins.",
        "Today's spend: ₹{amount}. Not too hot, not too cold. Just right. ✨"
    )

    private val DAILY_SUMMARY_MORE = listOf(
        "You've spent ₹{amount} today—a bit more than usual. 🤔",
        "₹{amount} today, a bit above usual. 😅 Maybe skip that extra late-night snack tomorrow?",
        "Today's spend: ₹{amount}. 💸 The budget says: be a little more mindful tomorrow."
    )

    private val DAILY_SUMMARY_MUCH_MORE = listOf(
        "₹{amount} today?! 😬 Your wallet just coughed. Might want to let it recover.",
        "Today's spend: ₹{amount}—that's quite a lot. 😳 Maybe ease up a little tomorrow?",
        "Whoa, ₹{amount} today. 🔥 You're on fire—and so is your wallet."
    )

    private val DAILY_SUMMARY_NO_HISTORY = listOf(
        "You've spent ₹{amount} today. 📊 Nothing to compare yet—keep tracking and we'll roast (or praise) you later.",
        "Today's spend: ₹{amount}. 🌱 The data is still collecting. Patience, young saver.",
        "₹{amount} spent today. 🚀 More days = better insights. You've got this!"
    )

    private val BUDGET_PER_DAY = listOf(
        "Aim for ₹{perDay}/day to stay in budget. 🎯",
        "To stay on track: about ₹{perDay} per day from here. 📅",
        "Stick to ~₹{perDay} per day and you'll nail this month. 💪"
    )

    private val OVER_BUDGET = listOf(
        "You're over budget this month. 😬 Time to channel your inner minimalist—or give the budget a raise. Your call!",
        "Budget exceeded. 😅 Two options: spend less till month-end or bump the budget.",
        "Oops—over budget. 🙈 Tighten the belt a little or adjust the number. No judgment!"
    )

    private val NO_BUDGET_MOTIVATE = listOf(
        "Set a budget in the Budget tab to see how much you can spend per day. 🎯",
        "No budget yet? 💡 Set one to get a daily spending target and keep your month on track.",
        "Tip: Set a monthly budget to unlock daily targets and stay on top of your spending. 📊"
    )

    data class BudgetContext(val remaining: Double, val daysLeftInMonth: Int)

    fun getDailySummaryText(
        today: Double,
        pastWeeks: List<Double>,
        budgetContext: BudgetContext?
    ): String {
        val dayOfMonth = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        val avg = if (pastWeeks.isNotEmpty()) pastWeeks.sum() / pastWeeks.size else 0.0
        val amountStr = formatAmount(Math.round(today).toDouble())

        val main: String = when {
            today == 0.0 -> DAILY_SUMMARY_NO_SPEND[dayOfMonth % DAILY_SUMMARY_NO_SPEND.size]
            avg == 0.0 -> DAILY_SUMMARY_NO_HISTORY[dayOfMonth % DAILY_SUMMARY_NO_HISTORY.size].replace("{amount}", amountStr)
            else -> {
                val ratio = today / avg
                when {
                    ratio < 0.9 -> DAILY_SUMMARY_LESS[dayOfMonth % DAILY_SUMMARY_LESS.size].replace("{amount}", amountStr)
                    ratio <= 1.1 -> DAILY_SUMMARY_EQUAL[dayOfMonth % DAILY_SUMMARY_EQUAL.size].replace("{amount}", amountStr)
                    ratio < 1.5 -> DAILY_SUMMARY_MORE[dayOfMonth % DAILY_SUMMARY_MORE.size].replace("{amount}", amountStr)
                    else -> DAILY_SUMMARY_MUCH_MORE[dayOfMonth % DAILY_SUMMARY_MUCH_MORE.size].replace("{amount}", amountStr)
                }
            }
        }

        val bullet = { line: String -> "• $line" }

        if (budgetContext == null) {
            val noBudgetLine = NO_BUDGET_MOTIVATE[dayOfMonth % NO_BUDGET_MOTIVATE.size]
            return "${bullet(main)}\n${bullet(noBudgetLine)}"
        }

        val daysLeft = maxOf(0, budgetContext.daysLeftInMonth)
        if (budgetContext.remaining < 0) {
            val overLine = OVER_BUDGET[dayOfMonth % OVER_BUDGET.size]
            return "${bullet(main)}\n${bullet(overLine)}"
        }

        if (budgetContext.remaining >= 0 && daysLeft > 0) {
            val perDay = Math.round(budgetContext.remaining / daysLeft)
            val perDayStr = formatAmount(perDay.toDouble())
            val budgetLine = BUDGET_PER_DAY[dayOfMonth % BUDGET_PER_DAY.size].replace("{perDay}", perDayStr)
            val budgetBullet = "You have ₹${formatAmount(Math.round(budgetContext.remaining).toDouble())} left. $budgetLine"
            return "${bullet(main)}\n${bullet(budgetBullet)}"
        }

        return "${bullet(main)}\n${bullet("You have ₹${formatAmount(Math.round(budgetContext.remaining).toDouble())} left this month.")}"
    }
}
