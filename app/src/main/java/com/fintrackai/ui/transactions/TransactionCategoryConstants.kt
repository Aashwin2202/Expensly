package com.fintrackai.ui.transactions

object TransactionCategoryConstants {
    const val MERCHANT_CATEGORY_DIALOG_TITLE = "Update category"
    const val THIS_TRANSACTION_ONLY = "This transaction only"
    const val ALL_FOR_MERCHANT = "All from this merchant"
    const val CANCEL = "Cancel"

    fun dialogBody(merchant: String, categoryLabel: String, occurrenceCount: Int): String =
        "\"$merchant\" appears $occurrenceCount times in your transactions.\n\n" +
            "Apply \"$categoryLabel\" to only this one, or to all $occurrenceCount?"
}
