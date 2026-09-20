package com.example.finora.repository

import com.example.finora.data.db.dao.AccountDao
import com.example.finora.data.db.dao.PortfolioDao
import com.example.finora.data.db.dao.WatchlistDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Data model representing the components of a user's net worth.
 */
data class NetWorthBreakdown(
    val totalNetWorth: Double,
    val cashBalance: Double,
    val investmentValue: Double
)

/**
 * Repository for computing total net worth per Implementation Plan §4.7:
 * Total Net Worth = Σ(Account.balance) + Σ(PortfolioHolding.sharesOwned × WatchlistStock.lastKnownPrice)
 *
 * Coupling Decision (Option A):
 * Portfolio valuation is coupled to WatchlistStock as the single pricing authority.
 * If a held symbol is not present in WatchlistStock or has zero price, its investment
 * contribution evaluates to 0.0.
 */
class NetWorthRepository(
    private val accountDao: AccountDao,
    private val portfolioDao: PortfolioDao? = null,
    private val watchlistDao: WatchlistDao? = null
) {

    /**
     * Emits the reactive total net worth combining cash accounts and portfolio investments.
     */
    fun getTotalNetWorth(): Flow<Double> =
        getNetWorthBreakdown().map { it.totalNetWorth }

    /**
     * Emits full reactive breakdown of cash vs investment components.
     */
    fun getNetWorthBreakdown(): Flow<NetWorthBreakdown> {
        val accountsFlow = accountDao.getAll()
        val holdingsFlow = portfolioDao?.getAll() ?: flowOf(emptyList())
        val watchlistFlow = watchlistDao?.getAll() ?: flowOf(emptyList())

        return combine(accountsFlow, holdingsFlow, watchlistFlow) { accounts, holdings, stocks ->
            val cashTotal = accounts.sumOf { it.balance }

            val stockPriceMap = stocks.associate { stock ->
                stock.symbol.uppercase().trim() to stock.lastKnownPrice
            }

            val investmentTotal = holdings.sumOf { holding ->
                val cleanSymbol = holding.symbol.uppercase().trim()
                val price = stockPriceMap[cleanSymbol] ?: 0.0
                holding.sharesOwned * price
            }

            NetWorthBreakdown(
                totalNetWorth = cashTotal + investmentTotal,
                cashBalance = cashTotal,
                investmentValue = investmentTotal
            )
        }
    }

    /**
     * Emits the reactive cash net worth as account balances update.
     */
    fun getCashNetWorth(): Flow<Double> =
        accountDao.getAll().map { accounts ->
            accounts.sumOf { it.balance }
        }

    /**
     * Fetches a one-shot snapshot of current total net worth.
     */
    suspend fun getTotalNetWorthSnapshot(): Double =
        getTotalNetWorth().first()
}
