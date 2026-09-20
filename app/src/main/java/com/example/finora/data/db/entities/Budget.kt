package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Budget entity representing monthly spending limits per category.
 *
 * Enforces a unique constraint on (categoryId, month) so a category cannot have multiple budgets in the same month.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("categoryId"),
        Index(value = ["categoryId", "month"], unique = true)
    ]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryId: Int,
    val monthlyLimit: Double,
    val rolloverEnabled: Boolean = false,
    val month: String          // Format "YYYY-MM", e.g. "2024-03"
)
