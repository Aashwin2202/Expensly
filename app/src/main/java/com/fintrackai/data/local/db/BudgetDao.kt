package com.fintrackai.data.local.db

import androidx.room.*

@Dao
interface BudgetDao {
    @Query("SELECT value FROM budget_settings WHERE key = 'monthly'")
    suspend fun getMonthlyBudget(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: BudgetSettingsEntity)

    @Query("DELETE FROM budget_settings WHERE key = 'monthly'")
    suspend fun removeMonthlyBudget()

    @Query("SELECT key, value FROM budget_settings WHERE key LIKE 'category_%'")
    suspend fun getCategoryBudgets(): List<BudgetSettingsEntity>

    @Query("DELETE FROM budget_settings WHERE key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM budget_settings")
    suspend fun deleteAll()
}
