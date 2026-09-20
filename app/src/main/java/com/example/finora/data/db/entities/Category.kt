package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Category entity for transaction categorization and budget tracking.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,          // "INCOME" or "EXPENSE"
    val iconRes: Int = 0,
    val isSystemDefault: Boolean = false
)

enum class CategoryType(val storageValue: String) {
    INCOME("INCOME"),
    EXPENSE("EXPENSE");

    companion object {
        fun fromString(value: String): CategoryType =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}
