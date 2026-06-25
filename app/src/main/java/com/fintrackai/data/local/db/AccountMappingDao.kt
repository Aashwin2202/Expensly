package com.fintrackai.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountMappingDao {
    @Query("SELECT bankName, accountType, is_confident AS isConfident FROM account_mappings WHERE last4Digits = :last4Digits AND bankName = :bankName")
    suspend fun get(last4Digits: String, bankName: String): AccountMappingRow?

    @Query("SELECT * FROM account_mappings")
    suspend fun getAll(): List<AccountMappingEntity>

    @Query("SELECT * FROM account_mappings ORDER BY bankName ASC, last4Digits ASC")
    fun getAllFlow(): Flow<List<AccountMappingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: AccountMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<AccountMappingEntity>)

    @Query("DELETE FROM account_mappings")
    suspend fun deleteAll()

    // Updates balance on all rows matching last4Digits — used when bank/type are unknown (e.g. balance-only SMS)
    @Query("UPDATE account_mappings SET availableBalance = :balance, balanceUpdatedAt = :updatedAt WHERE last4Digits = :last4Digits AND (balanceUpdatedAt IS NULL OR balanceUpdatedAt < :updatedAt)")
    suspend fun updateBalanceByDigits(last4Digits: String, balance: Double, updatedAt: Long)

    // User-initiated type correction — marks as confident so the badge is removed
    @Query("UPDATE account_mappings SET accountType = :type, is_confident = 1, updatedAt = :ts WHERE last4Digits = :last4 AND bankName = :bank")
    suspend fun updateType(last4: String, bank: String, type: String, ts: Long)

    @Query("DELETE FROM account_mappings WHERE last4Digits = :last4 AND bankName = :bank")
    suspend fun deleteByKey(last4: String, bank: String)
}

data class AccountMappingRow(val bankName: String, val accountType: String, val isConfident: Boolean = true)
