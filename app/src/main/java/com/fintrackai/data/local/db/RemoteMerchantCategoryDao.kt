package com.fintrackai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteMerchantCategoryDao {

    @Query("SELECT * FROM remote_merchant_category_mappings WHERE match_type = 'merchant' ORDER BY LENGTH(keyword) DESC")
    suspend fun getMerchantMappings(): List<RemoteMerchantCategoryEntity>

    @Query("SELECT * FROM remote_merchant_category_mappings WHERE match_type = 'word' ORDER BY LENGTH(keyword) DESC")
    suspend fun getWordMappings(): List<RemoteMerchantCategoryEntity>

    @Query("SELECT MAX(version) FROM remote_merchant_category_mappings")
    suspend fun getMaxVersion(): Int?

    @Query("SELECT COUNT(*) FROM remote_merchant_category_mappings")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<RemoteMerchantCategoryEntity>)

    @Query("DELETE FROM remote_merchant_category_mappings WHERE id NOT IN (:activeIds)")
    suspend fun deleteNotIn(activeIds: List<String>)

    @Query("DELETE FROM remote_merchant_category_mappings")
    suspend fun deleteAll()
}
