package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolio_holdings")
data class PortfolioHolding(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val sharesOwned: Double,
    val avgBuyPrice: Double
)
