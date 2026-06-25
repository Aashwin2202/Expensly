package com.fintrackai.ui.transactions

object TransactionCountInStatsConstants {
    const val COUNT_IN_EXPENSE = "Count in expense"
    const val COUNT_AS_INCOME = "Count in credit"

    const val MERCHANT_COUNT_IN_STATS_DIALOG_TITLE = "Update count in stats"
    const val THIS_TRANSACTION_ONLY = "This transaction only"
    const val ALL_FOR_MERCHANT = "All from this merchant"

    fun dialogBody(merchant: String, enabled: Boolean, txType: String, occurrenceCount: Int): String {
        val label = if (txType == "debit") "expense" else "credit"
        val action = if (enabled) "Count in $label" else "Exclude from $label"
        return "\"$merchant\" appears $occurrenceCount times in your transactions.\n\n" +
            "Apply \"$action\" to only this one, or to all $occurrenceCount?"
    }
}
