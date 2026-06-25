package com.fintrackai.data.local.db

import androidx.room.*

@Dao
interface MerchantNameMappingDao {

    /**
     * Returns the user-corrected merchant name for [originalName] (case-insensitive, trimmed),
     * or null if no correction has been recorded.
     */
    @Query(
        """
        SELECT correctedName FROM merchant_name_mappings
        WHERE LOWER(TRIM(originalName)) = LOWER(TRIM(:originalName))
        LIMIT 1
        """
    )
    suspend fun getCorrectedName(originalName: String): String?

    @Query("SELECT * FROM merchant_name_mappings")
    suspend fun getAll(): List<MerchantNameMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: MerchantNameMappingEntity)
}
