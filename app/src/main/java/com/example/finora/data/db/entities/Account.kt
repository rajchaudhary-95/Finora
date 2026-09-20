package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,          // "CASH", "BANK", "CREDIT_CARD"
    val balance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
