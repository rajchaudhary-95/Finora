package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"],
                              childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryId: Int,
    val monthlyLimit: Double,
    val rolloverEnabled: Boolean = false,
    val month: String          // "YYYY-MM" e.g. "2024-03"
)
