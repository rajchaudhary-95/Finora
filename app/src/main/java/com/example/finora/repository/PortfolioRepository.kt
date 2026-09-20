package com.example.finora.repository

import com.example.finora.data.db.dao.PortfolioDao
import com.example.finora.data.db.entities.PortfolioHolding
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing access to user's investment portfolio holdings.
 */
class PortfolioRepository(private val portfolioDao: PortfolioDao) {
    val allHoldings: Flow<List<PortfolioHolding>> = portfolioDao.getAll()

    suspend fun insert(holding: PortfolioHolding): Long = portfolioDao.insert(holding)
    suspend fun update(holding: PortfolioHolding) = portfolioDao.update(holding)
    suspend fun delete(holding: PortfolioHolding) = portfolioDao.delete(holding)
    suspend fun getById(id: Int): PortfolioHolding? = portfolioDao.getById(id)
    suspend fun getBySymbol(symbol: String): PortfolioHolding? = portfolioDao.getBySymbol(symbol)
}
