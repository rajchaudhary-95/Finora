package com.example.finora.util

import com.example.finora.data.db.entities.Category
import java.util.Locale

/**
 * Rule-based categorization engine per Finora Implementation Plan §7.3.
 *
 * Maps merchant keywords to default categories using lowercase substring matching.
 * No ML dependencies and no external network calls.
 */
class CategorizationRuleEngine(
    private val categoryLookup: Map<String, Category> = DEFAULT_CATEGORIES.associateBy { it.name }
) {

    constructor(categories: List<Category>) : this(categories.associateBy { it.name })

    // Normalizing lookup map keys to lowercase for robust lookup
    private val normalizedLookup: Map<String, Category> = categoryLookup.mapKeys {
        it.key.trim().lowercase(Locale.ROOT)
    }

    companion object {
        val DEFAULT_CATEGORIES: List<Category> = listOf(
            Category(id = 1, name = "Groceries", type = "EXPENSE", isSystemDefault = true),
            Category(id = 2, name = "Food & Dining", type = "EXPENSE", isSystemDefault = true),
            Category(id = 3, name = "Transport", type = "EXPENSE", isSystemDefault = true),
            Category(id = 4, name = "Shopping", type = "EXPENSE", isSystemDefault = true),
            Category(id = 5, name = "Subscriptions", type = "EXPENSE", isSystemDefault = true),
            Category(id = 6, name = "Bills & Utilities", type = "EXPENSE", isSystemDefault = true),
            Category(id = 7, name = "Entertainment", type = "EXPENSE", isSystemDefault = true),
            Category(id = 8, name = "Salary", type = "INCOME", isSystemDefault = true),
            Category(id = 9, name = "Other Income", type = "INCOME", isSystemDefault = true),
            Category(id = 10, name = "Other Expense", type = "EXPENSE", isSystemDefault = true)
        )

        /**
         * Seeded keyword-to-category mapping with at least 3-4 keywords per default category.
         */
        val KEYWORD_MAP: Map<String, String> = mapOf(
            // Transport
            "uber" to "Transport",
            "ola" to "Transport",
            "lyft" to "Transport",
            "rapido" to "Transport",
            "metro" to "Transport",
            "cab" to "Transport",
            "taxi" to "Transport",

            // Food & Dining
            "swiggy" to "Food & Dining",
            "zomato" to "Food & Dining",
            "doordash" to "Food & Dining",
            "starbucks" to "Food & Dining",
            "mcdonald" to "Food & Dining",
            "dominos" to "Food & Dining",
            "kfc" to "Food & Dining",
            "subway" to "Food & Dining",
            "burger king" to "Food & Dining",
            "restaurant" to "Food & Dining",
            "cafe" to "Food & Dining",

            // Shopping
            "amazon" to "Shopping",
            "flipkart" to "Shopping",
            "myntra" to "Shopping",
            "zara" to "Shopping",
            "h&m" to "Shopping",
            "walmart" to "Shopping",
            "target" to "Shopping",
            "nike" to "Shopping",
            "adidas" to "Shopping",
            "ikea" to "Shopping",

            // Subscriptions
            "netflix" to "Subscriptions",
            "spotify" to "Subscriptions",
            "prime video" to "Subscriptions",
            "hotstar" to "Subscriptions",
            "disney+" to "Subscriptions",
            "hulu" to "Subscriptions",
            "apple music" to "Subscriptions",
            "youtube premium" to "Subscriptions",
            "patreon" to "Subscriptions",

            // Bills & Utilities
            "electricity" to "Bills & Utilities",
            "broadband" to "Bills & Utilities",
            "recharge" to "Bills & Utilities",
            "water bill" to "Bills & Utilities",
            "gas bill" to "Bills & Utilities",
            "utility" to "Bills & Utilities",
            "airtel" to "Bills & Utilities",
            "jio" to "Bills & Utilities",
            "verizon" to "Bills & Utilities",
            "at&t" to "Bills & Utilities",

            // Groceries
            "bigbasket" to "Groceries",
            "grofers" to "Groceries",
            "blinkit" to "Groceries",
            "dmart" to "Groceries",
            "trader joe" to "Groceries",
            "kroger" to "Groceries",
            "safeway" to "Groceries",
            "whole foods" to "Groceries",
            "supermarket" to "Groceries",
            "grocery" to "Groceries",

            // Entertainment
            "cinema" to "Entertainment",
            "movie" to "Entertainment",
            "amc" to "Entertainment",
            "pvr" to "Entertainment",
            "inox" to "Entertainment",
            "bookmyshow" to "Entertainment",
            "concert" to "Entertainment",
            "ticketmaster" to "Entertainment",
            "theatre" to "Entertainment",

            // Salary
            "salary" to "Salary",
            "payroll" to "Salary",
            "stipend" to "Salary",
            "direct deposit" to "Salary",
            "wage" to "Salary",

            // Other Income
            "dividend" to "Other Income",
            "interest" to "Other Income",
            "cashback" to "Other Income",
            "refund" to "Other Income",
            "bonus" to "Other Income",

            // Other Expense
            "atm withdrawal" to "Other Expense",
            "misc" to "Other Expense",
            "miscellaneous" to "Other Expense",
            "fee" to "Other Expense",
            "penalty" to "Other Expense"
        )
    }

    /**
     * Suggests a Category based on lowercase substring matching of the merchant text against known keywords.
     *
     * @param merchantText Raw merchant string entered by the user.
     * @return The matched Category, or null if no match is found or the input is blank.
     */
    fun suggestCategory(merchantText: String): Category? {
        val lower = merchantText.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return null

        val match = KEYWORD_MAP.entries.firstOrNull { (keyword, _) -> lower.contains(keyword) }
        return match?.let { normalizedLookup[it.value.lowercase(Locale.ROOT)] }
    }
}
