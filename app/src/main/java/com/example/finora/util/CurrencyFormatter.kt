package com.example.finora.util

import java.util.Locale
import kotlin.math.abs

/**
 * Centralized utility for currency formatting throughout Finora.
 * Standardized on Indian Rupee (₹).
 */
object CurrencyFormatter {

    const val SYMBOL = "₹"

    /**
     * Formats an amount with currency symbol.
     * Examples:
     *   1250.0 -> "₹1,250.00"
     *   -45.2 -> "-₹45.20"
     *   0.0 -> "₹0.00"
     */
    fun format(amount: Double): String {
        return if (amount < 0) {
            String.format(Locale.getDefault(), "-₹%,.2f", abs(amount))
        } else {
            String.format(Locale.getDefault(), "₹%,.2f", amount)
        }
    }

    /**
     * Formats an amount with explicit plus (+) or minus (-) sign.
     * Examples:
     *   1250.0 -> "+₹1,250.00"
     *   -45.2 -> "-₹45.20"
     */
    fun formatWithSign(amount: Double): String {
        return when {
            amount > 0 -> String.format(Locale.getDefault(), "+₹%,.2f", amount)
            amount < 0 -> String.format(Locale.getDefault(), "-₹%,.2f", abs(amount))
            else -> String.format(Locale.getDefault(), "₹%,.2f", 0.0)
        }
    }

    /**
     * Formats a single-decimal float for chart axes (e.g. Y-axis).
     * Example:
     *   150.0f -> "₹150.0"
     */
    fun formatAxis(value: Float): String {
        return String.format(Locale.getDefault(), "₹%.1f", value)
    }

    /**
     * Formats an amount without decimal digits if preferred, or standard.
     */
    fun formatWhole(amount: Double): String {
        return if (amount < 0) {
            String.format(Locale.getDefault(), "-₹%,d", abs(amount).toLong())
        } else {
            String.format(Locale.getDefault(), "₹%,d", amount.toLong())
        }
    }
}
