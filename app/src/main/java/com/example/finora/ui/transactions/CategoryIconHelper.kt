package com.example.finora.ui.transactions

import com.example.finora.R
import com.example.finora.data.db.entities.Category

object CategoryIconHelper {
    fun getIconForCategory(category: Category?): Int {
        if (category == null) return R.drawable.ic_transactions
        if (category.iconRes != 0) return category.iconRes
        return when (category.name.lowercase().trim()) {
            "groceries" -> R.drawable.ic_category_groceries
            "food & dining" -> R.drawable.ic_category_food
            "transport" -> R.drawable.ic_category_transport
            "shopping" -> R.drawable.ic_category_shopping
            "subscriptions" -> R.drawable.ic_category_subscriptions
            "bills & utilities" -> R.drawable.ic_category_bills
            "entertainment" -> R.drawable.ic_category_entertainment
            "salary" -> R.drawable.ic_category_salary
            "other income" -> R.drawable.ic_income
            "other expense" -> R.drawable.ic_expense
            else -> if (category.type.equals("INCOME", ignoreCase = true)) R.drawable.ic_income else R.drawable.ic_expense
        }
    }
}
