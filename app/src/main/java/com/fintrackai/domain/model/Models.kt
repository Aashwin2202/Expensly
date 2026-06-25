package com.fintrackai.domain.model

data class Transaction(
    val id: String,
    val merchant: String,
    val amount: Double,
    val type: String,
    val category: String,
    val date: String,
    val time: String,
    val accounts: String,
    val reference: String? = null,
    val countInStats: Boolean = true,
    val originalSms: String? = null,
    val smsSender: String? = null,
    /** SHA-256 of sender + normalized SMS body; used to dedupe when [reference] is missing. */
    val smsDedupeHash: String? = null,
    /** Shared group UUID for all transactions in a link group (null = not linked). */
    val linkGroupId: String? = null,
    /** When true, row is hidden from lists; it is a secondary in a link group. */
    val linkSuppressed: Boolean = false,
    /** On the visible merged row: amount/type before linking (for unsplit). */
    val linkStashedAmount: Double? = null,
    val linkStashedType: String? = null,
    /** True when extracted via generic fallback (patternIndex == 7); used to flag for later reprocessing. */
    val isWeakMatch: Boolean = false
)

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String,
    val timestamp: String
)

data class CustomCategory(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val createdAt: Long,
    val hidden: Boolean = false
)

data class AccountMapping(
    val bankName: String,
    val accountType: String,
    val isConfident: Boolean = true
)

/** Resolved bank account or card from SMS last-4 mappings (for home overview). */
data class MappedAccount(
    val last4Digits: String,
    val bankName: String,
    val isCreditCard: Boolean,
    val isDebitCard: Boolean = false,
    val availableBalance: Double? = null,
    val balanceUpdatedAt: Long? = null,
    val isConfident: Boolean = true,
    val isHidden: Boolean = false
)

/** Aggregated spend per account string (matches React Native home “Accounts / Cards”). */
data class AccountSummary(
    val accountKey: String,
    val title: String,
    val totalAmount: Double,       // current month spend
    val allTimeTotal: Double = 0.0, // all-time total spend
    val availableBalance: Double? = null,
    /** ISO date string YYYY-MM-DD when balance was last reported */
    val balanceUpdatedDate: String? = null,
    /** False when card type was inferred from weak signals (generic “card” word, masked number).
     *  True when classified from an explicit “credit card”/”debit card” phrase or sender code. */
    val isConfident: Boolean = true
)

data class MonthlyStats(
    val income: Double,
    val expense: Double,
    val balance: Double
)

data class CategoryStat(
    val category: String,
    val amount: Double
)

data class MerchantStat(
    val merchant: String,
    val amount: Double,
    val transactionCount: Int,
    val excluded: Boolean = false
)

data class MonthTrend(
    val month: String,
    val monthKey: String,
    val amount: Double
)

/** One calendar day in the home monthly expense bar chart (debit totals). */
data class DailyExpenseDay(
    val dayOfMonth: Int,
    val dateKey: String,
    val amount: Double,
    val isFuture: Boolean,
    val isToday: Boolean
)

data class TopMerchant(
    val merchant: String,
    val amount: Double
)

data class WeeklyCategoryComparison(
    val thisWeekCategories: List<CategoryStat>,
    val lastWeekCategories: List<CategoryStat>,
    val topMerchant: TopMerchant? = null
)

data class RepeatedMerchant(
    val merchant: String,
    val transactionCount: Int,
    val totalAmount: Double
)

data class TimeDistributionItem(
    val hour: Int,
    val count: Int,
    val totalAmount: Double
)

data class SpendingBucket(
    val count: Int,
    val totalAmount: Double,
    val percentage: Double
)

data class TimeBasedPatterns(
    val lateNightSpending: SpendingBucket,
    val weekdaySpending: SpendingBucket,
    val weekendSpending: SpendingBucket,
    val peakSpendingHour: TimeDistributionItem,
    val timeDistribution: List<TimeDistributionItem>
)

data class DateRange(
    val minDate: String,
    val maxDate: String,
    val count: Int
)

data class Reminder(
    val id: String,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    val frequency: String,
    val reminder_date: String,
    val created_at: Long,
    val last_transaction_date: String? = null,
    val paid_on: String? = null          // ISO date when user marked paid this cycle
)

data class SmsMessage(
    val body: String,
    val timestamp: Long,
    val address: String,
    val date: String? = null,
    val time: String? = null
)

data class VelocityData(
    val spentThisMonth: Double,
    val daysElapsed: Int,
    val daysInMonth: Int,
    val previousMonthsTotals: List<Double>
)

data class DailySpendingComparison(
    val today: Double,
    val pastWeeks: List<Double>
)

data class RecurringTransaction(
    val merchant: String,
    val amount: Double,
    val interval: Int,
    val lastDate: String? = null
)

enum class MerchantCategoryApplyMode {
    THIS_TRANSACTION_ONLY,
    ALL_FOR_MERCHANT
}
