package com.fintrackai.domain.transactions

object TransactionLinkConstants {
    /**
     * Debit and credit are treated as cancelling each other when |debit − credit| is at most this (₹).
     * Whole-rupee SMS/noise style matching, not decimal-exact.
     */
    const val AMOUNT_FUZZY_TOLERANCE_RUPEES = 1.0

    const val LINK_CREDIT_TITLE = "Link Credit transaction"
    const val LINK_DEBIT_TITLE = "Link Debit Transaction"
    const val LINK_SELECTION_BANNER_DEBIT = "Tap a transaction to link with"
    const val LINK_SELECTION_BANNER_CREDIT = "Tap a transaction to link with"
    const val CANCEL_COMBINE = "Cancel"
    const val ERR_INVALID_COMBINE_TAP = "Pick the opposite type (debit vs credit) that isn’t already linked."
    const val UNSPLIT = "Unlink"
    const val DELETE = "Delete"
    const val LINKED_WITH_PREFIX = "Paired with"
    const val LINK_SUCCESS = "Transactions linked successfully"
    const val ERR_NOT_FOUND = "Could not find those transactions."
    const val ERR_INVALID_PAIR = "Merge not allowed — select both debit and credit transactions."
    const val ERR_ALREADY_LINKED = "The selected transaction is already linked."
}
