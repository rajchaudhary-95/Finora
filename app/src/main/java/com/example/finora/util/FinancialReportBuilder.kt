package com.example.finora.util

import com.example.finora.data.db.dao.CategorySpending
import com.example.finora.data.db.entities.Category
import com.example.finora.repository.NetWorthBreakdown
import com.example.finora.ui.budget.BudgetUiModel
import com.example.finora.util.BudgetStatus
import java.util.Locale

/**
 * Utility for formatting a human-readable financial summary report
 * to share via Android's native share sheet (Intent.ACTION_SEND).
 */
object FinancialReportBuilder {

    fun buildReport(
        monthName: String,
        netWorthBreakdown: NetWorthBreakdown,
        spendingList: List<CategorySpending>,
        categories: List<Category>,
        budgets: List<BudgetUiModel>
    ): String {
        val categoryMap = categories.associateBy { it.id }
        val sb = StringBuilder()

        sb.appendLine("📊 Finora Financial Summary — $monthName")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()

        // 1. Net Worth Section
        sb.appendLine("💰 Total Net Worth: ${formatCurrency(netWorthBreakdown.totalNetWorth)}")
        sb.appendLine("   • Cash & Bank: ${formatCurrency(netWorthBreakdown.cashBalance)}")
        sb.appendLine("   • Investments: ${formatCurrency(netWorthBreakdown.investmentValue)}")
        sb.appendLine()

        // 2. Spending Breakdown Section
        val totalSpent = spendingList.sumOf { it.total }
        sb.appendLine("💳 Total Spent this Month: ${formatCurrency(totalSpent)}")

        val topCategories = spendingList.sortedByDescending { it.total }.take(3)
        if (topCategories.isNotEmpty()) {
            sb.appendLine("Top Expense Categories:")
            topCategories.forEachIndexed { index, item ->
                val catName = categoryMap[item.categoryId]?.name ?: "Category ${item.categoryId}"
                val pct = if (totalSpent > 0.0) (item.total / totalSpent) * 100.0 else 0.0
                sb.appendLine("  ${index + 1}. $catName: ${formatCurrency(item.total)} (${String.format(Locale.US, "%.1f%%", pct)})")
            }
        } else {
            sb.appendLine("  No expenses recorded yet this month.")
        }
        sb.appendLine()

        // 3. Budgets Section
        val overLimitCount = budgets.count { it.status == BudgetStatus.OVER }
        val atRiskCount = budgets.count { it.status == BudgetStatus.AT_RISK }
        val totalBudgets = budgets.size

        sb.appendLine("🎯 Budgets Health ($totalBudgets tracked):")
        if (totalBudgets == 0) {
            sb.appendLine("  No budgets configured for this month.")
        } else {
            if (overLimitCount > 0) {
                sb.appendLine("  ⚠️ $overLimitCount budget(s) over limit!")
            }
            if (atRiskCount > 0) {
                sb.appendLine("  ⚡ $atRiskCount budget(s) nearing limit (>75% used).")
            }
            val onTrackCount = totalBudgets - overLimitCount - atRiskCount
            if (onTrackCount > 0) {
                sb.appendLine("  ✅ $onTrackCount budget(s) on track.")
            }
        }

        sb.appendLine()
        sb.appendLine("Shared from Finora Personal Finance App")
        return sb.toString()
    }

    private fun formatCurrency(amount: Double): String {
        return if (amount >= 0.0) {
            String.format(Locale.US, "$%,.2f", amount)
        } else {
            String.format(Locale.US, "-$%,.2f", Math.abs(amount))
        }
    }
}
