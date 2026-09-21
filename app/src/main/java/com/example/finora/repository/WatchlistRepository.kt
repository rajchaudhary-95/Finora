package com.example.finora.repository

import android.util.Log
import com.example.finora.data.db.dao.WatchlistDao
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.network.ApiClient
import com.example.finora.network.FinnhubApiService
import com.example.finora.network.FinnhubQuoteResponse
import com.example.finora.network.FinnhubSearchResult
import com.example.finora.util.StockPriceHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Repository providing access to watchlisted stock quotes and Finnhub market data.
 * Implements 60-second caching discipline per Implementation Plan §7.5.
 */
class WatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val apiService: FinnhubApiService = ApiClient.createFinnhubService(),
    private val historyStore: StockPriceHistoryStore? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "WatchlistRepository"
        const val CACHE_DURATION_MS = 60_000L // 60-second cache window
    }

    val allStocks: Flow<List<WatchlistStock>> = watchlistDao.getAll()

    suspend fun insert(stock: WatchlistStock): Long = watchlistDao.insert(stock)
    suspend fun update(stock: WatchlistStock) = watchlistDao.update(stock)
    suspend fun delete(stock: WatchlistStock) = watchlistDao.delete(stock)
    suspend fun getById(id: Int): WatchlistStock? = watchlistDao.getById(id)
    suspend fun getBySymbol(symbol: String): WatchlistStock? = watchlistDao.getBySymbol(symbol.uppercase().trim())
    suspend fun updatePrice(symbol: String, price: Double, change: Double, fetchedAt: Long) =
        watchlistDao.updatePrice(symbol.uppercase().trim(), price, change, fetchedAt)

    /**
     * Determines whether a stock's quote should be re-fetched from the network.
     * Returns true if never fetched or if cache is older than CACHE_DURATION_MS (60s).
     */
    fun shouldRefetch(stock: WatchlistStock, currentTimeMillis: Long = clock()): Boolean {
        if (stock.lastFetchedAt <= 0L) return true
        val ageMillis = currentTimeMillis - stock.lastFetchedAt
        return ageMillis > CACHE_DURATION_MS
    }

    /**
     * Fetches a quote for [symbol].
     * If [forceRefresh] is false and the stock was fetched within the last 60 seconds,
     * skips the network call and returns the cached [WatchlistStock].
     */
    suspend fun fetchQuote(
        symbol: String,
        forceRefresh: Boolean = false
    ): Result<WatchlistStock> = withContext(Dispatchers.IO) {
        val cleanSymbol = symbol.uppercase().trim()
        val cachedStock = watchlistDao.getBySymbol(cleanSymbol)

        // Cache hit check
        if (!forceRefresh && cachedStock != null && !shouldRefetch(cachedStock)) {
            Log.d(TAG, "Serving cached quote for $cleanSymbol (age: ${clock() - cachedStock.lastFetchedAt}ms)")
            return@withContext Result.success(cachedStock)
        }

        try {
            Log.d(TAG, "Making network quote call for $cleanSymbol (forceRefresh=$forceRefresh)")
            val quote = apiService.getQuote(cleanSymbol)

            if (!quote.isValid()) {
                val errorMsg = "Symbol '$cleanSymbol' not found or returned invalid quote."
                Log.w(TAG, errorMsg)
                return@withContext Result.failure(IllegalArgumentException(errorMsg))
            }

            val now = clock()
            watchlistDao.updatePrice(
                symbol = cleanSymbol,
                price = quote.currentPrice,
                change = quote.changePercent,
                fetchedAt = now
            )

            // Save to local sample history for MPAndroidChart
            historyStore?.addSample(cleanSymbol, quote.currentPrice, now)

            val updatedStock = watchlistDao.getBySymbol(cleanSymbol) ?: WatchlistStock(
                symbol = cleanSymbol,
                displayName = cleanSymbol,
                lastKnownPrice = quote.currentPrice,
                dayChangePercent = quote.changePercent,
                lastFetchedAt = now
            )

            Result.success(updatedStock)
        } catch (e: HttpException) {
            val friendlyMsg = when (e.code()) {
                429 -> "Rate limit reached (60 calls/min). Showing cached prices."
                401, 403 -> "Finnhub API key unauthorized. Configure in Settings or local.properties."
                else -> "Market data unavailable (HTTP ${e.code()})."
            }
            Log.e(TAG, "HttpException for $cleanSymbol: $friendlyMsg", e)
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: IOException) {
            val friendlyMsg = "No internet connection — showing cached prices."
            Log.e(TAG, "Network failure for $cleanSymbol: $friendlyMsg", e)
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching quote for $cleanSymbol", e)
            Result.failure(e)
        }
    }

    /**
     * Refreshes quotes for all stocks currently in the watchlist.
     */
    suspend fun refreshAllWatchlist(forceRefresh: Boolean = false): List<Result<WatchlistStock>> =
        withContext(Dispatchers.IO) {
            val stocks = watchlistDao.getAll().firstOrNull() ?: emptyList()
            stocks.map { stock ->
                fetchQuote(stock.symbol, forceRefresh)
            }
        }

    /**
     * Searches for stock symbols matching [query].
     */
    suspend fun searchSymbols(query: String): Result<List<FinnhubSearchResult>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        try {
            Log.d(TAG, "Searching symbols for query: $cleanQuery")
            val response = apiService.searchSymbol(cleanQuery)
            // Filter out empty symbols
            val validResults = response.result.filter { it.symbol.isNotBlank() }
            Result.success(validResults)
        } catch (e: HttpException) {
            val friendlyMsg = when (e.code()) {
                429 -> "Rate limit reached. Please wait a moment."
                401, 403 -> "Finnhub API key unauthorized. Configure in Settings or local.properties."
                else -> "Symbol search unavailable (HTTP ${e.code()})."
            }
            Log.e(TAG, "Search HttpException: $friendlyMsg", e)
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: IOException) {
            val friendlyMsg = "No internet connection for search."
            Log.e(TAG, "Search IOException: $friendlyMsg", e)
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Log.e(TAG, "Search unexpected error", e)
            Result.failure(e)
        }
    }
}
