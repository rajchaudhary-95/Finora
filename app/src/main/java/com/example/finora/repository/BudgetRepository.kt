package com.example.finora.repository

import com.example.finora.data.db.dao.BudgetDao
import com.example.finora.data.db.entities.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing monthly budget management and tracking.
 */
class BudgetRepository(private val budgetDao: BudgetDao) {
    val allBudgets: Flow<List<Budget>> = budgetDao.getAll()

    fun getForMonth(month: String): Flow<List<Budget>> = budgetDao.getForMonth(month)
    suspend fun getListForMonth(month: String): List<Budget> = budgetDao.getListForMonth(month)
    fun getByMonth(month: String): Flow<List<Budget>> = budgetDao.getByMonth(month)

    suspend fun insert(budget: Budget): Long = budgetDao.insert(budget)
    suspend fun update(budget: Budget) = budgetDao.update(budget)
    suspend fun delete(budget: Budget) = budgetDao.delete(budget)
    suspend fun getById(id: Int): Budget? = budgetDao.getById(id)
    suspend fun getByCategoryAndMonth(categoryId: Int, month: String): Budget? =
        budgetDao.getByCategoryAndMonth(categoryId, month)
}
