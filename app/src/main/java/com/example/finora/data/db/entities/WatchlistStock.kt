package com.example.finora.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist_stocks")
data class WatchlistStock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(index = true) val symbol: String,
    val displayName: String,
    val lastKnownPrice: Double = 0.0,
    val lastFetchedAt: Long = 0L,
    val dayChangePercent: Double = 0.0
)
