package com.example.finora.repository

import com.example.finora.data.db.dao.CategorySpending
import com.example.finora.data.db.dao.RecurringCandidate
import com.example.finora.data.db.dao.TransactionDao
import com.example.finora.data.db.entities.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing transaction data access, category spending queries, and recurring candidates.
 */
class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAll()

    suspend fun insert(tx: Transaction): Long = transactionDao.insert(tx)
    suspend fun update(tx: Transaction) = transactionDao.update(tx)
    suspend fun delete(tx: Transaction) = transactionDao.delete(tx)
    suspend fun getById(id: Int): Transaction? = transactionDao.getById(id)
    fun getByAccount(accountId: Int): Flow<List<Transaction>> = transactionDao.getByAccount(accountId)
    suspend fun getCountByAccount(accountId: Int): Int = transactionDao.getCountByAccount(accountId)

    fun getSpendingByCategory(month: String): Flow<List<CategorySpending>> =
        transactionDao.getSpendingByCategory(month)

    fun getRecurring(): Flow<List<Transaction>> = transactionDao.getRecurring()

    fun getGeotaggedTransactions(): Flow<List<Transaction>> =
        transactionDao.getGeotaggedTransactions()

    suspend fun getGeotagged(): List<Transaction> = transactionDao.getGeotagged()

    suspend fun getRecurringCandidates(sinceEpoch: Long): List<RecurringCandidate> =
        transactionDao.getRecurringCandidates(sinceEpoch)

    suspend fun markRecurring(merchant: String, amount: Double, flag: Boolean) =
        transactionDao.markRecurring(merchant, amount, flag)
}
