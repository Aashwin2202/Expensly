package com.fintrackai.data.remote

import com.fintrackai.data.local.db.RemoteMerchantCategoryEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteMerchantCategoryMapping(
    val id: String,
    val keyword: String,
    val category: String,
    @SerialName("match_type") val matchType: String = "merchant",
    val version: Int
) {
    fun toEntity(): RemoteMerchantCategoryEntity = RemoteMerchantCategoryEntity(
        id = id,
        keyword = keyword,
        category = category,
        matchType = matchType,
        version = version,
        syncedAt = System.currentTimeMillis()
    )
}
