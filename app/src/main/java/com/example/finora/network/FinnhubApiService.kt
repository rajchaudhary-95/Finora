package com.example.finora.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Finnhub Stock API endpoints.
 * Base URL: https://finnhub.io/api/v1/
 *
 * Authentication token is automatically injected by [FinnhubAuthInterceptor].
 */
interface FinnhubApiService {

    /**
     * Get real-time quote data for a US stock.
     * @param symbol Symbol of the company (e.g. AAPL, MSFT, TSLA)
     */
    @GET("quote")
    suspend fun getQuote(
        @Query("symbol") symbol: String
    ): FinnhubQuoteResponse

    /**
     * Search for best-matching symbols based on a query string (ticker or company name).
     * @param query Search query string
     */
    @GET("search")
    suspend fun searchSymbol(
        @Query("q") query: String
    ): FinnhubSearchResponse
}
