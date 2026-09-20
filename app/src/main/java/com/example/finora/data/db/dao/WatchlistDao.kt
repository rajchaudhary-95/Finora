package com.example.finora.data.db.dao

import androidx.room.*
import com.example.finora.data.db.entities.WatchlistStock
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock: WatchlistStock): Long

    @Update
    suspend fun update(stock: WatchlistStock)

    @Delete
    suspend fun delete(stock: WatchlistStock)

    @Query("SELECT * FROM watchlist_stocks ORDER BY symbol ASC")
    fun getAll(): Flow<List<WatchlistStock>>

    @Query("SELECT * FROM watchlist_stocks WHERE symbol = :symbol")
    suspend fun getBySymbol(symbol: String): WatchlistStock?

    @Query("UPDATE watchlist_stocks SET lastKnownPrice = :price, dayChangePercent = :change, lastFetchedAt = :fetchedAt WHERE symbol = :symbol")
    suspend fun updatePrice(symbol: String, price: Double, change: Double, fetchedAt: Long)
}
