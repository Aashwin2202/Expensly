package com.fintrackai.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()
}
