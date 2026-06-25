package com.fintrackai.ui.transactions

import com.fintrackai.domain.model.Transaction

data class PendingMerchantCategoryChange(
    val transaction: Transaction,
    val categoryId: String,
    val merchantOccurrenceCount: Int
)
