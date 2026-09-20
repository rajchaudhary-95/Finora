package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,          // "INCOME", "EXPENSE"
    val iconRes: Int = 0,
    val isSystemDefault: Boolean = false
)
