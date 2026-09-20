package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PortfolioHolding entity representing stock shares owned by the user.
 *
 * Foreign Key Strategy:
 * - Links to WatchlistStock.symbol with ForeignKey.CASCADE.
 * - Enforces uniqueness on symbol so a stock has a consolidated holding position.
 */
@Entity(
    tableName = "portfolio_holdings",
    foreignKeys = [
        ForeignKey(
            entity = WatchlistStock::class,
            parentColumns = ["symbol"],
            childColumns = ["symbol"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["symbol"], unique = true)
    ]
)
data class PortfolioHolding(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val sharesOwned: Double,
    val avgBuyPrice: Double
)
