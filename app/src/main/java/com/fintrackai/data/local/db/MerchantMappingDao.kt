package com.fintrackai.data.local.db

import androidx.room.*

@Dao
interface MerchantMappingDao {
    /**
     * Latest category for this merchant (case-insensitive, trimmed), so SMS and UI keys match.
     */
    @Query(
        """
        SELECT category FROM merchant_category_mappings
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getCategory(merchant: String): String?

    @Query(
        """
        SELECT countInStats FROM merchant_category_mappings
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
        LIMIT 1
        """
    )
    suspend fun getCountInStats(merchant: String): Int?

    @Query(
        """
        UPDATE merchant_category_mappings SET countInStats = :countInStats
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
        """
    )
    suspend fun updateCountInStats(merchant: String, countInStats: Int?)

    @Query(
        """
        DELETE FROM merchant_category_mappings
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:normalizedMerchant))
        """
    )
    suspend fun deleteByNormalizedMerchantKey(normalizedMerchant: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: MerchantCategoryMappingEntity)

    @Transaction
    suspend fun replaceMerchantMapping(normalizedMerchant: String, category: String, updatedAt: Long) {
        deleteByNormalizedMerchantKey(normalizedMerchant)
        upsert(MerchantCategoryMappingEntity(normalizedMerchant, category, updatedAt))
    }

    @Query("SELECT * FROM merchant_category_mappings")
    suspend fun getAll(): List<MerchantCategoryMappingEntity>

    @Query("DELETE FROM merchant_category_mappings")
    suspend fun deleteAll()
}
