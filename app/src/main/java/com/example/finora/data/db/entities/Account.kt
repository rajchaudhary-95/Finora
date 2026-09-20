package com.example.finora.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Account entity representing a financial account (Cash, Bank, Credit Card).
 *
 * Balance convention:
 * - Positive balance represents available funds / positive net worth.
 * - For credit cards, a negative balance represents money owed, which subtracts from net worth.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,          // One of "CASH", "BANK", "CREDIT_CARD"
    val balance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AccountType(val storageValue: String) {
    CASH("CASH"),
    BANK("BANK"),
    CREDIT_CARD("CREDIT_CARD");

    companion object {
        fun fromString(value: String): AccountType =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: CASH
    }
}
