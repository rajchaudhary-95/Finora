package com.example.finora.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * WatchlistStock entity for tracking stock market quotes.
 * Enforces a UNIQUE constraint on symbol (e.g. "AAPL", "MSFT").
 */
@Entity(
    tableName = "watchlist_stocks",
    indices = [
        Index(value = ["symbol"], unique = true)
    ]
)
data class WatchlistStock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val displayName: String,
    val lastKnownPrice: Double = 0.0,
    val dayChangePercent: Double = 0.0,
    val lastFetchedAt: Long = 0L
)
