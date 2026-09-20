package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"],
                   childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class, parentColumns = ["id"],
                   childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("accountId"), Index("categoryId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val categoryId: Int? = null,
    val amount: Double,        // negative = expense, positive = income
    val merchant: String,
    val note: String? = null,
    val date: Long = System.currentTimeMillis(),
    val receiptImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val isRecurring: Boolean = false,
    val isAutoCategorized: Boolean = false
)
