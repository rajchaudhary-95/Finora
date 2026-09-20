package com.example.finora.repository

import com.example.finora.data.db.dao.AccountDao
import com.example.finora.data.db.entities.Account
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing account data operations and balance adjustments.
 */
class AccountRepository(private val accountDao: AccountDao) {
    val allAccounts: Flow<List<Account>> = accountDao.getAll()

    suspend fun insert(account: Account): Long = accountDao.insert(account)
    suspend fun update(account: Account) = accountDao.update(account)
    suspend fun delete(account: Account) = accountDao.delete(account)
    suspend fun getById(id: Int): Account? = accountDao.getById(id)
    suspend fun adjustBalance(accountId: Int, delta: Double) = accountDao.adjustBalance(accountId, delta)
}
