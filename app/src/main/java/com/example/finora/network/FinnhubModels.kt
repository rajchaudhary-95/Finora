package com.example.finora.network

import com.google.gson.annotations.SerializedName

/**
 * Data model for Finnhub Quote API response (/quote).
 */
data class FinnhubQuoteResponse(
    @SerializedName("c") val currentPrice: Double = 0.0,
    @SerializedName("d") val change: Double = 0.0,
    @SerializedName("dp") val changePercent: Double = 0.0,
    @SerializedName("h") val highPriceOfDay: Double = 0.0,
    @SerializedName("l") val lowPriceOfDay: Double = 0.0,
    @SerializedName("o") val openPriceOfDay: Double = 0.0,
    @SerializedName("pc") val previousClosePrice: Double = 0.0,
    @SerializedName("t") val timestamp: Long = 0L
) {
    /**
     * Finnhub returns all zeros (c == 0.0 && pc == 0.0) when a symbol is invalid / not found.
     */
    fun isValid(): Boolean = currentPrice != 0.0 || previousClosePrice != 0.0
}

/**
 * Data model for Finnhub Symbol Search API response (/search).
 */
data class FinnhubSearchResponse(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("result") val result: List<FinnhubSearchResult> = emptyList()
)

/**
 * Single stock search result from Finnhub search.
 */
data class FinnhubSearchResult(
    @SerializedName("description") val description: String = "",
    @SerializedName("displaySymbol") val displaySymbol: String = "",
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("type") val type: String = ""
)
