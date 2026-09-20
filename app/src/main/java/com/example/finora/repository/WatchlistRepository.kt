package com.example.finora.repository

import com.example.finora.data.db.dao.WatchlistDao
import com.example.finora.data.db.entities.WatchlistStock
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing access to watchlisted stock quotes.
 */
class WatchlistRepository(private val watchlistDao: WatchlistDao) {
    val allStocks: Flow<List<WatchlistStock>> = watchlistDao.getAll()

    suspend fun insert(stock: WatchlistStock): Long = watchlistDao.insert(stock)
    suspend fun update(stock: WatchlistStock) = watchlistDao.update(stock)
    suspend fun delete(stock: WatchlistStock) = watchlistDao.delete(stock)
    suspend fun getById(id: Int): WatchlistStock? = watchlistDao.getById(id)
    suspend fun getBySymbol(symbol: String): WatchlistStock? = watchlistDao.getBySymbol(symbol)
    suspend fun updatePrice(symbol: String, price: Double, change: Double, fetchedAt: Long) =
        watchlistDao.updatePrice(symbol, price, change, fetchedAt)
}
