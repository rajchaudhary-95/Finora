package com.example.finora.data.db.dao

import androidx.room.*
import com.example.finora.data.db.entities.PortfolioHolding
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holding: PortfolioHolding): Long

    @Update
    suspend fun update(holding: PortfolioHolding)

    @Delete
    suspend fun delete(holding: PortfolioHolding)

    @Query("SELECT * FROM portfolio_holdings")
    fun getAll(): Flow<List<PortfolioHolding>>

    @Query("SELECT * FROM portfolio_holdings WHERE symbol = :symbol")
    suspend fun getBySymbol(symbol: String): PortfolioHolding?
}
