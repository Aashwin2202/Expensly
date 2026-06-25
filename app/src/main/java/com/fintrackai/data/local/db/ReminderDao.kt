package com.fintrackai.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY reminder_date ASC")
    fun getAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY reminder_date ASC")
    suspend fun getAllOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET paid_on = :paidOn WHERE id = :id")
    suspend fun setPaidOn(id: String, paidOn: String?)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
