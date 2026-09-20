package com.example.finora

import com.example.finora.data.db.entities.Budget
import com.example.finora.data.db.entities.Category
import com.example.finora.util.BudgetCalculator
import com.example.finora.util.BudgetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculationTest {

    @Test
    fun testProgressAndRemainingCalculation_movesWithTransactions() {
        val monthlyLimit = 200.0
        val rolloverAmount = 0.0
        val effectiveLimit = BudgetCalculator.computeEffectiveLimit(monthlyLimit, rolloverAmount)
        assertEquals(200.0, effectiveLimit, 0.001)

        // 1. Initial state (no spending)
        val initialSpent = 0.0
        val remaining1 = BudgetCalculator.computeRemaining(effectiveLimit, initialSpent)
        val progress1 = BudgetCalculator.computeProgressPercent(initialSpent, effectiveLimit)
        val status1 = BudgetCalculator.determineStatus(initialSpent, effectiveLimit)
        assertEquals(200.0, remaining1, 0.001)
        assertEquals(0, progress1)
        assertEquals(BudgetStatus.OK, status1)

        // 2. Add expense transaction of $50.00 (25% spent)
        val spentAfterTx1 = 50.0
        val remaining2 = BudgetCalculator.computeRemaining(effectiveLimit, spentAfterTx1)
        val progress2 = BudgetCalculator.computeProgressPercent(spentAfterTx1, effectiveLimit)
        val status2 = BudgetCalculator.determineStatus(spentAfterTx1, effectiveLimit)
        assertEquals(150.0, remaining2, 0.001)
        assertEquals(25, progress2)
        assertEquals(BudgetStatus.OK, status2)

        // 3. Add expense transaction bringing total to $160.00 (80% spent -> AT_RISK)
        val spentAfterTx2 = 160.0
        val remaining3 = BudgetCalculator.computeRemaining(effectiveLimit, spentAfterTx2)
        val progress3 = BudgetCalculator.computeProgressPercent(spentAfterTx2, effectiveLimit)
        val status3 = BudgetCalculator.determineStatus(spentAfterTx2, effectiveLimit)
        assertEquals(40.0, remaining3, 0.001)
        assertEquals(80, progress3)
        assertEquals(BudgetStatus.AT_RISK, status3)

        // 4. Add expense transaction bringing total to $220.00 (110% spent -> OVER)
        val spentAfterTx3 = 220.0
        val remaining4 = BudgetCalculator.computeRemaining(effectiveLimit, spentAfterTx3)
        val progress4 = BudgetCalculator.computeProgressPercent(spentAfterTx3, effectiveLimit)
        val status4 = BudgetCalculator.determineStatus(spentAfterTx3, effectiveLimit)
        assertEquals(-20.0, remaining4, 0.001)
        assertEquals(100, progress4)
        assertEquals(BudgetStatus.OVER, status4)
    }

    @Test
    fun testRolloverMath_twoMonthTestCase_positiveRemainderCarriedForward() {
        // Month 1 (2026-08): limit = $300.00, spent = $220.00 -> unspent = $80.00
        val month1Limit = 300.0
        val month1Spent = 220.0

        // Month 2 (2026-09): limit = $250.00, rolloverEnabled = true
        val month2BaseLimit = 250.0
        val rolloverEnabled = true

        val rolloverAmount = BudgetCalculator.calculateRollover(
            rolloverEnabled = rolloverEnabled,
            previousMonthLimit = month1Limit,
            previousMonthSpent = month1Spent
        )
        assertEquals(80.0, rolloverAmount, 0.001)

        val effectiveLimit = BudgetCalculator.computeEffectiveLimit(month2BaseLimit, rolloverAmount)
        assertEquals(330.0, effectiveLimit, 0.001)

        // Add Month 2 transactions totaling $165.00
        val month2Spent = 165.0
        val remaining = BudgetCalculator.computeRemaining(effectiveLimit, month2Spent)
        val progress = BudgetCalculator.computeProgressPercent(month2Spent, effectiveLimit)
        val status = BudgetCalculator.determineStatus(month2Spent, effectiveLimit)

        assertEquals(165.0, remaining, 0.001)
        assertEquals(50, progress)
        assertEquals(BudgetStatus.OK, status)
    }

    @Test
    fun testRolloverMath_overBudgetPriorMonth_doesNotReduceCurrentLimit() {
        // Month 1: limit = $200.00, spent = $250.00 -> unspent = -$50.00
        val month1Limit = 200.0
        val month1Spent = 250.0

        // Month 2: limit = $200.00, rolloverEnabled = true
        val month2Limit = 200.0
        val rolloverAmount = BudgetCalculator.calculateRollover(
            rolloverEnabled = true,
            previousMonthLimit = month1Limit,
            previousMonthSpent = month1Spent
        )
        // Negative balance must not penalize current month (max(0, -50) = 0)
        assertEquals(0.0, rolloverAmount, 0.001)

        val effectiveLimit = BudgetCalculator.computeEffectiveLimit(month2Limit, rolloverAmount)
        assertEquals(200.0, effectiveLimit, 0.001)
    }

    @Test
    fun testRolloverDisabled_ignoresPriorMonthUnspent() {
        val month1Limit = 300.0
        val month1Spent = 100.0 // unspent $200.00

        val rolloverAmount = BudgetCalculator.calculateRollover(
            rolloverEnabled = false,
            previousMonthLimit = month1Limit,
            previousMonthSpent = month1Spent
        )
        assertEquals(0.0, rolloverAmount, 0.001)

        val effectiveLimit = BudgetCalculator.computeEffectiveLimit(250.0, rolloverAmount)
        assertEquals(250.0, effectiveLimit, 0.001)
    }

    @Test
    fun testRolloverNoPriorBudget_contributesZero() {
        val rolloverAmount = BudgetCalculator.calculateRollover(
            rolloverEnabled = true,
            previousMonthLimit = null,
            previousMonthSpent = 0.0
        )
        assertEquals(0.0, rolloverAmount, 0.001)
    }

    @Test
    fun testCategoryPickerExclusion_excludesBudgetedCategoriesThisMonth() {
        val foodCategory = Category(id = 1, name = "Food & Dining", type = "EXPENSE")
        val transportCategory = Category(id = 2, name = "Transport", type = "EXPENSE")
        val groceriesCategory = Category(id = 3, name = "Groceries", type = "EXPENSE")
        val salaryCategory = Category(id = 4, name = "Salary", type = "INCOME")

        val allCategories = listOf(foodCategory, transportCategory, groceriesCategory, salaryCategory)

        // Existing budget in current month for Food & Dining (id = 1)
        val currentMonthBudgets = listOf(
            Budget(id = 10, categoryId = 1, monthlyLimit = 200.0, month = "2026-09")
        )

        val available = BudgetCalculator.filterAvailableCategories(allCategories, currentMonthBudgets)

        // Should include Transport (2) and Groceries (3)
        assertEquals(2, available.size)
        assertTrue(available.any { it.id == 2 })
        assertTrue(available.any { it.id == 3 })

        // Must NOT include Food & Dining (already budgeted this month)
        assertFalse(available.any { it.id == 1 })

        // Must NOT include Salary (INCOME type)
        assertFalse(available.any { it.id == 4 })
    }

    @Test
    fun testProgressBarColorCodingStatus() {
        val limit = 100.0

        // < 75% -> OK (Green)
        assertEquals(BudgetStatus.OK, BudgetCalculator.determineStatus(0.0, limit))
        assertEquals(BudgetStatus.OK, BudgetCalculator.determineStatus(50.0, limit))
        assertEquals(BudgetStatus.OK, BudgetCalculator.determineStatus(74.99, limit))

        // 75% - 100% -> AT_RISK (Amber)
        assertEquals(BudgetStatus.AT_RISK, BudgetCalculator.determineStatus(75.0, limit))
        assertEquals(BudgetStatus.AT_RISK, BudgetCalculator.determineStatus(90.0, limit))
        assertEquals(BudgetStatus.AT_RISK, BudgetCalculator.determineStatus(100.0, limit))

        // > 100% -> OVER (Red)
        assertEquals(BudgetStatus.OVER, BudgetCalculator.determineStatus(100.01, limit))
        assertEquals(BudgetStatus.OVER, BudgetCalculator.determineStatus(150.0, limit))
    }

    @Test
    fun testGetPreviousMonth() {
        assertEquals("2026-08", BudgetCalculator.getPreviousMonth("2026-09"))
        assertEquals("2026-01", BudgetCalculator.getPreviousMonth("2026-02"))
        // Year boundary rollover
        assertEquals("2025-12", BudgetCalculator.getPreviousMonth("2026-01"))
    }
}
