package com.fintrackai.domain.merchant

import java.util.Locale

object MerchantMappingKeys {
    fun normalizeMerchantKey(merchant: String): String = merchant.trim().lowercase(Locale.ROOT)

    fun normalizeCategoryKey(category: String): String = category.trim().lowercase(Locale.ROOT)
}
