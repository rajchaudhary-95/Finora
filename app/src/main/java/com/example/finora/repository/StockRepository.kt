package com.example.finora.repository

import com.example.finora.data.db.dao.PortfolioDao
import com.example.finora.data.db.dao.WatchlistDao
import com.example.finora.data.db.entities.PortfolioHolding
import com.example.finora.data.db.entities.WatchlistStock
import kotlinx.coroutines.flow.Flow

/**
 * Combined StockRepository for Watchlist and Portfolio operations.
 */
class StockRepository(
    private val watchlistDao: WatchlistDao,
    private val portfolioDao: PortfolioDao
) {
    val watchlistRepository = WatchlistRepository(watchlistDao)
    val portfolioRepository = PortfolioRepository(portfolioDao)

    val allStocks: Flow<List<WatchlistStock>> = watchlistRepository.allStocks
    val allHoldings: Flow<List<PortfolioHolding>> = portfolioRepository.allHoldings

    suspend fun insertWatchlist(stock: WatchlistStock): Long = watchlistRepository.insert(stock)
    suspend fun deleteWatchlist(stock: WatchlistStock) = watchlistRepository.delete(stock)

    suspend fun insertHolding(holding: PortfolioHolding): Long = portfolioRepository.insert(holding)
    suspend fun deleteHolding(holding: PortfolioHolding) = portfolioRepository.delete(holding)
}
