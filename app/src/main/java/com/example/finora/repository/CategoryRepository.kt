package com.example.finora.repository

import com.example.finora.data.db.dao.CategoryDao
import com.example.finora.data.db.entities.Category
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing category lookup and management.
 */
class CategoryRepository(private val categoryDao: CategoryDao) {
    val allCategories: Flow<List<Category>> = categoryDao.getAll()

    suspend fun insert(category: Category): Long = categoryDao.insert(category)
    suspend fun insertAll(categories: List<Category>): List<Long> = categoryDao.insertAll(categories)
    suspend fun update(category: Category) = categoryDao.update(category)
    suspend fun delete(category: Category) = categoryDao.delete(category)
    suspend fun getById(id: Int): Category? = categoryDao.getById(id)
    suspend fun getByName(name: String): Category? = categoryDao.getByName(name)
    fun getByType(type: String): Flow<List<Category>> = categoryDao.getByType(type)
    suspend fun getCount(): Int = categoryDao.getCount()
}
