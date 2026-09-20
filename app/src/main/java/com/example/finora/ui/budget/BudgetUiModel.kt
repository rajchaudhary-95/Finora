package com.example.finora.ui.budget

import com.example.finora.data.db.entities.Budget
import com.example.finora.data.db.entities.Category
import com.example.finora.util.BudgetStatus

/**
 * UI representation of a budget entry with computed spending, effective limit, and status.
 */
data class BudgetUiModel(
    val budget: Budget,
    val category: Category?,
    val categoryName: String,
    val categoryIconRes: Int,
    val monthlyLimit: Double,
    val rolloverAmount: Double,
    val effectiveLimit: Double,
    val spentSoFar: Double,
    val remaining: Double,
    val progressPercent: Int,
    val status: BudgetStatus
)
