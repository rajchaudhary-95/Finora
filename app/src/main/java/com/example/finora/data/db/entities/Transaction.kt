package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Transaction entity representing a single debit or credit entry.
 *
 * Foreign Key Strategy:
 * - accountId: ForeignKey.RESTRICT prevents deleting an Account with associated transactions.
 * - categoryId: ForeignKey.SET_NULL allows transaction records to remain even if a custom category is deleted.
 *
 * Amount convention:
 * - Amount is always stored as a positive magnitude. The direction (+ vs -) is derived from the linked Category.type.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("accountId"),
        Index("categoryId")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val categoryId: Int? = null,
    val amount: Double,                      // Stored as positive magnitude
    val merchant: String,
    val note: String? = null,
    val date: Long = System.currentTimeMillis(),
    val receiptImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val isRecurring: Boolean = false,
    val isAutoCategorized: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
