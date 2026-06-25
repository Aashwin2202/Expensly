package com.fintrackai.domain.transactions

sealed class TransactionLinkResult {
    data object Success : TransactionLinkResult()
    data object NotFound : TransactionLinkResult()
    data object InvalidPair : TransactionLinkResult()
    data object AlreadyLinked : TransactionLinkResult()
}

/** Result of comparing debit vs credit amounts when forming a link. */
sealed class DebitCreditLinkOutcome {
    data class NetMerged(val type: String, val amount: Double) : DebitCreditLinkOutcome()
    /** Within fuzzy tolerance: keep both rows as-is, both excluded from stats. */
    data object FuzzyCancelled : DebitCreditLinkOutcome()
}
