package com.fintrackai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsPatternDao {

    @Query("SELECT * FROM sms_patterns ORDER BY priority ASC")
    suspend fun getAllOrdered(): List<SmsPatternEntity>

    @Query("SELECT MAX(version) FROM sms_patterns")
    suspend fun getMaxVersion(): Int?

    @Query("SELECT COUNT(*) FROM sms_patterns")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(patterns: List<SmsPatternEntity>)

    @Query("DELETE FROM sms_patterns WHERE id NOT IN (:activeIds)")
    suspend fun deleteNotIn(activeIds: List<String>)

    @Query("DELETE FROM sms_patterns")
    suspend fun deleteAll()
}
