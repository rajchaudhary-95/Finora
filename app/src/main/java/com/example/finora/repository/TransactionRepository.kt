package com.example.finora.repository

import com.example.finora.data.db.dao.TransactionDao
import com.example.finora.data.db.entities.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAll()

    suspend fun insert(tx: Transaction) = transactionDao.insert(tx)
    suspend fun update(tx: Transaction) = transactionDao.update(tx)
    suspend fun delete(tx: Transaction) = transactionDao.delete(tx)
    suspend fun getById(id: Int) = transactionDao.getById(id)
    fun getByAccount(accountId: Int) = transactionDao.getByAccount(accountId)
    suspend fun getSpendingByCategory(month: String) = transactionDao.getSpendingByCategory(month)
    fun getRecurring() = transactionDao.getRecurring()
    suspend fun getGeotagged() = transactionDao.getGeotagged()
}
