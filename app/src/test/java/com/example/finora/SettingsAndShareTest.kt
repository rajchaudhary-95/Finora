package com.example.finora

import com.example.finora.data.db.dao.CategorySpending
import com.example.finora.data.db.entities.Budget
import com.example.finora.data.db.entities.Category
import com.example.finora.repository.NetWorthBreakdown
import com.example.finora.ui.budget.BudgetUiModel
import com.example.finora.util.BudgetStatus
import com.example.finora.util.FinancialReportBuilder
import org.junit.Assert.*
import org.junit.Test

class SettingsAndShareTest {

    @Test
    fun financialReportBuilder_formatsSummaryCorrectly() {
        val breakdown = NetWorthBreakdown(
            totalNetWorth = 15250.50,
            cashBalance = 10000.00,
            investmentValue = 5250.50
        )

        val categories = listOf(
            Category(id = 1, name = "Food & Dining", type = "EXPENSE", isSystemDefault = true),
            Category(id = 2, name = "Groceries", type = "EXPENSE", isSystemDefault = true),
            Category(id = 3, name = "Transport", type = "EXPENSE", isSystemDefault = true),
            Category(id = 4, name = "Entertainment", type = "EXPENSE", isSystemDefault = true)
        )

        val spending = listOf(
            CategorySpending(categoryId = 1, total = 450.0),
            CategorySpending(categoryId = 2, total = 300.0),
            CategorySpending(categoryId = 3, total = 150.0),
            CategorySpending(categoryId = 4, total = 50.0)
        )

        val dummyBudget = Budget(
            id = 1,
            categoryId = 1,
            month = "2026-09",
            monthlyLimit = 500.0,
            rolloverEnabled = true
        )

        val budgets = listOf(
            BudgetUiModel(
                budget = dummyBudget,
                category = categories[0],
                categoryName = "Food & Dining",
                categoryIconRes = 0,
                monthlyLimit = 500.0,
                rolloverAmount = 0.0,
                effectiveLimit = 500.0,
                spentSoFar = 450.0,
                remaining = 50.0,
                progressPercent = 90,
                status = BudgetStatus.AT_RISK
            ),
            BudgetUiModel(
                budget = dummyBudget.copy(id = 2, categoryId = 2),
                category = categories[1],
                categoryName = "Groceries",
                categoryIconRes = 0,
                monthlyLimit = 250.0,
                rolloverAmount = 0.0,
                effectiveLimit = 250.0,
                spentSoFar = 300.0,
                remaining = -50.0,
                progressPercent = 120,
                status = BudgetStatus.OVER
            ),
            BudgetUiModel(
                budget = dummyBudget.copy(id = 3, categoryId = 3),
                category = categories[2],
                categoryName = "Transport",
                categoryIconRes = 0,
                monthlyLimit = 300.0,
                rolloverAmount = 0.0,
                effectiveLimit = 300.0,
                spentSoFar = 150.0,
                remaining = 150.0,
                progressPercent = 50,
                status = BudgetStatus.OK
            )
        )

        val report = FinancialReportBuilder.buildReport(
            monthName = "September 2026",
            netWorthBreakdown = breakdown,
            spendingList = spending,
            categories = categories,
            budgets = budgets
        )

        assertTrue(report.contains("Finora Financial Summary — September 2026"))
        assertTrue(report.contains("Total Net Worth: $15,250.50"))
        assertTrue(report.contains("Cash & Bank: $10,000.00"))
        assertTrue(report.contains("Investments: $5,250.50"))
        assertTrue(report.contains("Total Spent this Month: $950.00"))
        // Check top 3 categories are listed
        assertTrue(report.contains("1. Food & Dining: $450.00 (47.4%)"))
        assertTrue(report.contains("2. Groceries: $300.00 (31.6%)"))
        assertTrue(report.contains("3. Transport: $150.00 (15.8%)"))
        // Check 4th category is NOT in top 3
        assertFalse(report.contains("4. Entertainment"))

        // Check budget health
        assertTrue(report.contains("Budgets Health (3 tracked):"))
        assertTrue(report.contains("1 budget(s) over limit!"))
        assertTrue(report.contains("1 budget(s) nearing limit (>75% used)."))
        assertTrue(report.contains("1 budget(s) on track."))
    }

    @Test
    fun financialReportBuilder_handlesEmptyDataCleanly() {
        val breakdown = NetWorthBreakdown(0.0, 0.0, 0.0)
        val report = FinancialReportBuilder.buildReport(
            monthName = "September 2026",
            netWorthBreakdown = breakdown,
            spendingList = emptyList(),
            categories = emptyList(),
            budgets = emptyList()
        )

        assertTrue(report.contains("Total Net Worth: $0.00"))
        assertTrue(report.contains("No expenses recorded yet this month."))
        assertTrue(report.contains("No budgets configured for this month."))
    }

    @Test
    fun category_systemDefaultFlagProtectsDefaultCategories() {
        val defaultCategory = Category(
            id = 1,
            name = "Groceries",
            type = "EXPENSE",
            isSystemDefault = true
        )
        val userCategory = Category(
            id = 100,
            name = "Gym Membership",
            type = "EXPENSE",
            isSystemDefault = false
        )

        assertTrue("Default category must be identified as system default", defaultCategory.isSystemDefault)
        assertFalse("User category must not be identified as system default", userCategory.isSystemDefault)
    }
}
