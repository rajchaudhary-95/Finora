package com.example.finora.ui.transactions

import com.example.finora.data.db.entities.Transaction

/**
 * UI presentation model combining Transaction data with associated Account and Category names/icons.
 */
data class TransactionUiModel(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String,
    val categoryType: String, // "INCOME" or "EXPENSE"
    val categoryIconRes: Int
) {
    val isIncome: Boolean get() = categoryType.equals("INCOME", ignoreCase = true)
}
