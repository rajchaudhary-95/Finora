package com.example.finora.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data model for a single price sample point in time.
 */
data class StockPricePoint(
    val timestamp: Long,
    val price: Double
)

/**
 * Storage for locally-accumulated stock price samples.
 * Used to construct recent price history charts in StockDetailActivity
 * without depending on restricted historical-candle API endpoints.
 */
class StockPriceHistoryStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "finora_stock_price_history"
        private const val KEY_PREFIX_SAMPLES = "samples_"
        private const val MAX_SAMPLES_PER_SYMBOL = 100
    }

    @Synchronized
    fun addSample(symbol: String, price: Double, timestamp: Long = System.currentTimeMillis()) {
        if (price <= 0.0) return
        val key = KEY_PREFIX_SAMPLES + symbol.uppercase().trim()
        val currentSamples = getSamples(symbol).toMutableList()

        // Avoid adding duplicate samples with the exact same price within 10 seconds
        val lastSample = currentSamples.lastOrNull()
        if (lastSample != null && lastSample.price == price && (timestamp - lastSample.timestamp) < 10_000L) {
            return
        }

        currentSamples.add(StockPricePoint(timestamp, price))

        // Keep at most MAX_SAMPLES_PER_SYMBOL
        val trimmed = if (currentSamples.size > MAX_SAMPLES_PER_SYMBOL) {
            currentSamples.takeLast(MAX_SAMPLES_PER_SYMBOL)
        } else {
            currentSamples
        }

        val json = gson.toJson(trimmed)
        prefs.edit().putString(key, json).apply()
    }

    @Synchronized
    fun getSamples(symbol: String): List<StockPricePoint> {
        val key = KEY_PREFIX_SAMPLES + symbol.uppercase().trim()
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<StockPricePoint>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clearSamples(symbol: String) {
        val key = KEY_PREFIX_SAMPLES + symbol.uppercase().trim()
        prefs.edit().remove(key).apply()
    }
}
