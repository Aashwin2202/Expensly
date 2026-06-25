package com.fintrackai.ui.transactions

import com.fintrackai.domain.model.Transaction

data class PendingMerchantCountInStatsChange(
    val transaction: Transaction,
    val countInStats: Boolean,
    val merchantOccurrenceCount: Int
)
