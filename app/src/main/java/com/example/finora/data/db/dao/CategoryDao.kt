package com.example.finora.data.db.dao

import androidx.room.*
import com.example.finora.data.db.entities.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Int): Category?

    @Query("SELECT * FROM categories WHERE type = :type")
    fun getByType(type: String): Flow<List<Category>>
}
