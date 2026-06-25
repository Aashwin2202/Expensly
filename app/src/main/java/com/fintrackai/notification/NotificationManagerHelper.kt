package com.fintrackai.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fintrackai.MainActivity
import com.fintrackai.R
import com.fintrackai.domain.format.AmountCompactFormatHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_DAILY_REVIEW = "daily_review"
        const val CHANNEL_WEEKLY_SUMMARY = "weekly_summary"
        const val CHANNEL_BUDGET_ALERT = "budget_alert"
        const val CHANNEL_REMINDER = "reminder_alert"

        const val NOTIF_ID_DAILY = 1001
        const val NOTIF_ID_WEEKLY = 1002
        // Budget alerts use IDs 2000+ (category hash based)
        private const val NOTIF_ID_BUDGET_BASE = 2000
        // Reminder notifications use IDs 3000+ (reminder id hash based)
        private const val NOTIF_ID_REMINDER_BASE = 3000

        // Intent extras for deep-link navigation on notification tap
        const val EXTRA_DESTINATION = "notif_destination"
        // Format: "transactions_date|YYYY-MM-DD"
        const val DEST_TRANSACTIONS_DATE_PREFIX = "transactions_date|"
        // Format: "transactions_range|YYYY-MM-DD|YYYY-MM-DD"
        const val DEST_TRANSACTIONS_RANGE_PREFIX = "transactions_range|"
        const val DEST_BUDGET = "budget"
        const val DEST_REMINDERS = "reminders"
        // Format: "weekly_summary|YYYY-MM-DD|YYYY-MM-DD"
        const val DEST_WEEKLY_SUMMARY_PREFIX = "weekly_summary|"
    }

    /** Call once at app startup to register all notification channels. */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_DAILY_REVIEW,
                "Daily Spending Review",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Nightly summary of your daily spending"
            },
            NotificationChannel(
                CHANNEL_WEEKLY_SUMMARY,
                "Weekly Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sunday evening weekly spending summary"
            },
            NotificationChannel(
                CHANNEL_BUDGET_ALERT,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts when you approach or exceed a budget"
            },
            NotificationChannel(
                CHANNEL_REMINDER,
                "Payment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upcoming and overdue payment reminders"
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    /** Send the daily review notification with varied titles and bodies. Returns true if posted. */
    fun sendDailyReview(date: String, totalSpend: Double, txCount: Int, topCategory: String?, merchantCount: Int = 0): Boolean {
        val amt = AmountCompactFormatHelper.formatCompactWithRupee(totalSpend)
        val catLabel = topCategory?.replaceFirstChar { it.uppercase() }
        val txWord = if (txCount == 1) "transaction" else "transactions"
        val merchantWord = if (merchantCount == 1) "merchant" else "merchants"

        // Seed the variant by day-of-year so it changes each day, never randomly mid-day
        val dayOfYear = LocalDate.now().dayOfYear
        val variant = dayOfYear % 6

        val (title, body) = when (variant) {
            0 -> Pair(
                "Today: $amt",
                buildString {
                    append("$txCount $txWord")
                    if (catLabel != null) append(" · $catLabel")
                }
            )
            1 -> Pair(
                "Daily recap",
                buildString {
                    append("$amt · $txCount $txWord")
                    if (catLabel != null) append(" · $catLabel")
                }
            )
            2 -> Pair(
                "$amt spent today",
                buildString {
                    if (catLabel != null) append("$catLabel · ")
                    append("$txCount $txWord")
                }
            )
            3 -> Pair(
                "Day in money",
                buildString {
                    append("$amt · $txCount $txWord")
                    if (catLabel != null) append(" · $catLabel")
                }
            )
            4 -> Pair(
                "Spending recap",
                buildString {
                    append("$amt · $txCount $txWord")
                    if (catLabel != null) append(" · $catLabel")
                }
            )
            else -> Pair(
                "Today at a glance",
                buildString {
                    append("$amt · $txCount $txWord")
                    if (catLabel != null) append(" · $catLabel")
                }
            )
        }

        return send(
            channelId = CHANNEL_DAILY_REVIEW,
            notifId = NOTIF_ID_DAILY,
            title = title,
            body = body,
            destination = "$DEST_TRANSACTIONS_DATE_PREFIX$date"
        )
    }

    /** Send the weekly summary notification with varied titles and bodies. */
    fun sendWeeklySummary(totalSpend: Double, percentChange: Double?, weekStart: String, weekEnd: String) {
        val amt = AmountCompactFormatHelper.formatCompactWithRupee(totalSpend)
        val changeClause = if (percentChange != null) {
            val pct = kotlin.math.abs(percentChange).toInt()
            if (percentChange >= 0) ", up $pct% from last week" else ", down $pct% from last week"
        } else ""

        // Seed by week-of-year so it changes every Sunday
        val weekOfYear = java.time.LocalDate.now().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val variant = weekOfYear % 5

        val (title, body) = when (variant) {
            0 -> Pair("Week in review", "$amt$changeClause")
            1 -> Pair("Weekly wrap", "$amt$changeClause")
            2 -> Pair("7 days: $amt", changeClause.ifEmpty { "Tap to review" }.trimStart(',', ' ').replaceFirstChar { it.uppercase() })
            3 -> Pair("Weekly total", "$amt$changeClause")
            else -> Pair("This week: $amt", changeClause.ifEmpty { "Tap to review" }.trimStart(',', ' ').replaceFirstChar { it.uppercase() })
        }

        send(
            channelId = CHANNEL_WEEKLY_SUMMARY,
            notifId = NOTIF_ID_WEEKLY,
            title = title,
            body = body,
            destination = "$DEST_WEEKLY_SUMMARY_PREFIX$weekStart|$weekEnd"
        )
    }

    /** Send a reminder notification for a payment due tomorrow, with varied copy. */
    fun sendReminderUpcoming(reminderId: String, merchant: String, amount: Double) {
        val notifId = NOTIF_ID_REMINDER_BASE + reminderId.hashCode().and(0xFFF)
        val amt = AmountCompactFormatHelper.formatCompactWithRupee(amount)
        val variant = LocalDate.now().dayOfYear % 4
        val (title, body) = when (variant) {
            0 -> Pair("Due tomorrow", "$merchant · $amt")
            1 -> Pair("Payment due", "$merchant · $amt tomorrow")
            2 -> Pair("Heads up: $merchant", "$amt due tomorrow")
            else -> Pair("Tomorrow: $merchant", "$amt due")
        }
        send(
            channelId = CHANNEL_REMINDER,
            notifId = notifId,
            title = title,
            body = body,
            destination = DEST_REMINDERS
        )
    }

    /** Send a reminder notification for a payment that was due yesterday, with varied copy. */
    fun sendReminderOverdue(reminderId: String, merchant: String, amount: Double) {
        val notifId = NOTIF_ID_REMINDER_BASE + reminderId.hashCode().and(0xFFF) + 1
        val amt = AmountCompactFormatHelper.formatCompactWithRupee(amount)
        val variant = LocalDate.now().dayOfYear % 4
        val (title, body) = when (variant) {
            0 -> Pair("Overdue: $merchant", "$amt was due yesterday")
            1 -> Pair("Missed payment", "$merchant · $amt overdue")
            2 -> Pair("Still unpaid: $merchant", "$amt overdue")
            else -> Pair("Payment overdue", "$merchant · $amt")
        }
        send(
            channelId = CHANNEL_REMINDER,
            notifId = notifId,
            title = title,
            body = body,
            destination = DEST_REMINDERS
        )
    }

    /** Send a budget alert notification with varied copy based on threshold and category. Returns true if posted. */
    fun sendBudgetAlert(category: String, thresholdPercent: Int): Boolean {
        val variant = category.lowercase().hashCode().and(0x7FFFFFFF) % 4

        val (title, body) = if (category.lowercase() == "overall") {
            if (thresholdPercent >= 100) {
                when (variant) {
                    0 -> Pair("Monthly budget exceeded", "You've gone over your budget this month")
                    1 -> Pair("Over budget", "Monthly spending limit crossed")
                    2 -> Pair("Budget blown", "You've exceeded your monthly budget")
                    else -> Pair("Monthly limit reached", "Over budget for this month")
                }
            } else {
                when (variant) {
                    0 -> Pair("$thresholdPercent% of budget used", "You're getting close to your monthly limit")
                    1 -> Pair("Budget at $thresholdPercent%", "$thresholdPercent% of your monthly budget spent")
                    2 -> Pair("Heads up!", "$thresholdPercent% of your monthly budget is used")
                    else -> Pair("Monthly budget: $thresholdPercent%", "Tap to review your spending")
                }
            }
        } else {
            val cat = category.replace('_', ' ').replaceFirstChar { it.uppercase() }
            if (thresholdPercent >= 100) {
                when (variant) {
                    0 -> Pair("$cat budget exceeded", "You're over the limit for $cat")
                    1 -> Pair("Over budget: $cat", "$cat has crossed its limit")
                    2 -> Pair("Budget blown: $cat", "$cat exceeded this month")
                    else -> Pair("$cat limit reached", "Over budget for $cat")
                }
            } else {
                when (variant) {
                    0 -> Pair("$cat at $thresholdPercent%", "$thresholdPercent% of $cat budget used")
                    1 -> Pair("$cat spending", "$thresholdPercent% of budget gone")
                    2 -> Pair("$cat: $thresholdPercent% used", "Tap to review $cat spend")
                    else -> Pair("Budget: $cat", "$thresholdPercent% used this month")
                }
            }
        }

        // Stable notification ID per category so it updates rather than stacks
        val notifId = NOTIF_ID_BUDGET_BASE + category.lowercase().hashCode().and(0xFFF)
        return send(
            channelId = CHANNEL_BUDGET_ALERT,
            notifId = notifId,
            title = title,
            body = body,
            destination = DEST_BUDGET
        )
    }

    /** Returns false if POST_NOTIFICATIONS permission is not granted; true if the notification was posted. */
    private fun send(channelId: String, notifId: Int, title: String, body: String, destination: String? = null): Boolean {
        if (!NotificationPermissionHelper.isGranted(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (destination != null) putExtra(EXTRA_DESTINATION, destination)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(notifId, notification)
        return true
    }

}
