package com.example.finora.repository

import com.example.finora.data.db.dao.BudgetDao
import com.example.finora.data.db.entities.Budget
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) {
    fun getByMonth(month: String): Flow<List<Budget>> = budgetDao.getByMonth(month)

    suspend fun insert(budget: Budget) = budgetDao.insert(budget)
    suspend fun update(budget: Budget) = budgetDao.update(budget)
    suspend fun delete(budget: Budget) = budgetDao.delete(budget)
    suspend fun getById(id: Int) = budgetDao.getById(id)
}
