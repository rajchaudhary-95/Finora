package com.example.finora.util

import com.example.finora.data.db.entities.Budget
import com.example.finora.data.db.entities.Category
import java.util.Locale

/**
 * Budget status representing spending health relative to effective monthly limit.
 * - OK: < 75% used (green)
 * - AT_RISK: 75%–100% used (amber)
 * - OVER: > 100% used (red)
 */
enum class BudgetStatus {
    OK,
    AT_RISK,
    OVER
}

/**
 * Pure calculation logic for the Budget module per Finora Implementation Plan §4.7 & §6.8.
 */
object BudgetCalculator {

    /**
     * Given a month string "YYYY-MM", returns the previous calendar month string "YYYY-MM".
     */
    fun getPreviousMonth(month: String): String {
        val parts = month.split("-")
        require(parts.size == 2) { "Invalid month format: $month. Expected YYYY-MM" }
        var year = parts[0].toInt()
        var m = parts[1].toInt()
        if (m <= 1) {
            year -= 1
            m = 12
        } else {
            m -= 1
        }
        return String.format(Locale.US, "%04d-%02d", year, m)
    }

    /**
     * Calculates the rollover contribution from the previous month.
     * Rollover means last month's unspent amount — if positive — adds to this month's effective limit.
     * If rollover is disabled or no previous month's budget exists, returns 0.0.
     * If previous month was over budget (unspent <= 0), returns 0.0 (does not penalize current month).
     */
    fun calculateRollover(
        rolloverEnabled: Boolean,
        previousMonthLimit: Double?,
        previousMonthSpent: Double
    ): Double {
        if (!rolloverEnabled || previousMonthLimit == null) {
            return 0.0
        }
        val unspent = previousMonthLimit - previousMonthSpent
        return if (unspent > 0.0) unspent else 0.0
    }

    /**
     * Computes the effective monthly limit, factoring in rollover if enabled.
     */
    fun computeEffectiveLimit(monthlyLimit: Double, rolloverAmount: Double): Double {
        return monthlyLimit + rolloverAmount
    }

    /**
     * Computes remaining budget amount: effectiveLimit - spentSoFar.
     */
    fun computeRemaining(effectiveLimit: Double, spentSoFar: Double): Double {
        return effectiveLimit - spentSoFar
    }

    /**
     * Computes progress percentage (0..100) for progress bar display.
     */
    fun computeProgressPercent(spentSoFar: Double, effectiveLimit: Double): Int {
        if (effectiveLimit <= 0.0) return if (spentSoFar > 0.0) 100 else 0
        val percent = (spentSoFar / effectiveLimit) * 100.0
        return percent.coerceIn(0.0, 100.0).toInt()
    }

    /**
     * Determines budget status based on percentage of effective limit used:
     * - OK: < 75%
     * - AT_RISK: 75% - 100%
     * - OVER: > 100%
     */
    fun determineStatus(spentSoFar: Double, effectiveLimit: Double): BudgetStatus {
        if (effectiveLimit <= 0.0) {
            return if (spentSoFar > 0.0) BudgetStatus.OVER else BudgetStatus.OK
        }
        val percent = (spentSoFar / effectiveLimit) * 100.0
        return when {
            percent < 75.0 -> BudgetStatus.OK
            percent <= 100.0 -> BudgetStatus.AT_RISK
            else -> BudgetStatus.OVER
        }
    }

    /**
     * Filters available categories for creating a new budget in the current month:
     * - Must be EXPENSE type
     * - Must NOT have an existing budget for the current month
     */
    fun filterAvailableCategories(
        categories: List<Category>,
        currentMonthBudgets: List<Budget>
    ): List<Category> {
        val budgetedCategoryIds = currentMonthBudgets.map { it.categoryId }.toSet()
        return categories.filter { category ->
            category.type.equals("EXPENSE", ignoreCase = true) && category.id !in budgetedCategoryIds
        }
    }
}
