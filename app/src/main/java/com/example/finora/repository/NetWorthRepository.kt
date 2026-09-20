package com.example.finora.repository

import com.example.finora.data.db.dao.AccountDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository for computing total net worth.
 *
 * Current Phase (Cash-only partial sum):
 * - Derives net worth as Σ(Account.balance), where positive balances represent available funds
 *   and negative balances (e.g. credit card balances) represent liabilities.
 * - The investment side of net worth (Σ(PortfolioHolding.sharesOwned * lastKnownPrice)) is
 *   added in later phases once live market pricing is connected.
 */
class NetWorthRepository(private val accountDao: AccountDao) {

    /**
     * Emits the reactive cash net worth as account balances update.
     */
    fun getCashNetWorth(): Flow<Double> =
        accountDao.getAll().map { accounts ->
            accounts.sumOf { it.balance }
        }

    /**
     * Fetches a one-shot snapshot of current cash net worth.
     */
    suspend fun getCashNetWorthSnapshot(): Double =
        getCashNetWorth().first()
}
