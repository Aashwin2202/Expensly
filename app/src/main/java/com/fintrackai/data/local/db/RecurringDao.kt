package com.fintrackai.data.local.db

import androidx.room.*

@Dao
interface RecurringDao {
    @Query("SELECT merchant, amount FROM dismissed_recurring")
    suspend fun getAllDismissed(): List<DismissedRecurringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismiss(entry: DismissedRecurringEntity)

    @Query("DELETE FROM dismissed_recurring")
    suspend fun deleteAll()
}
