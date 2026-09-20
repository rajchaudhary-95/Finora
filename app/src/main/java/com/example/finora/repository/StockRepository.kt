package com.example.finora.repository

import com.example.finora.data.db.dao.PortfolioDao
import com.example.finora.data.db.dao.WatchlistDao
import com.example.finora.data.db.entities.PortfolioHolding
import com.example.finora.data.db.entities.WatchlistStock
import kotlinx.coroutines.flow.Flow

class StockRepository(
    private val watchlistDao: WatchlistDao,
    private val portfolioDao: PortfolioDao
) {
    val allStocks: Flow<List<WatchlistStock>> = watchlistDao.getAll()
    val allHoldings: Flow<List<PortfolioHolding>> = portfolioDao.getAll()

    suspend fun insertWatchlist(stock: WatchlistStock) = watchlistDao.insert(stock)
    suspend fun deleteWatchlist(stock: WatchlistStock) = watchlistDao.delete(stock)
    
    suspend fun insertHolding(holding: PortfolioHolding) = portfolioDao.insert(holding)
    suspend fun deleteHolding(holding: PortfolioHolding) = portfolioDao.delete(holding)
}
