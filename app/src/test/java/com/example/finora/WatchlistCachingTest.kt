package com.example.finora

import com.example.finora.data.db.dao.WatchlistDao
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.network.FinnhubApiService
import com.example.finora.network.FinnhubQuoteResponse
import com.example.finora.network.FinnhubSearchResponse
import com.example.finora.network.FinnhubSearchResult
import com.example.finora.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class WatchlistCachingTest {

    private lateinit var fakeDao: FakeWatchlistDao
    private lateinit var fakeApi: FakeFinnhubApiService
    private lateinit var repository: WatchlistRepository
    private var mockCurrentTime = 1_000_000L

    @Before
    fun setup() {
        fakeDao = FakeWatchlistDao()
        fakeApi = FakeFinnhubApiService()
        mockCurrentTime = 1_000_000L
        repository = WatchlistRepository(
            watchlistDao = fakeDao,
            apiService = fakeApi,
            historyStore = null,
            clock = { mockCurrentTime }
        )
    }

    @Test
    fun testCachingWithin60Seconds_skipsNetworkCall() = runBlocking {
        // First quote fetch at t = 1,000,000L
        fakeApi.quoteResponse = FinnhubQuoteResponse(
            currentPrice = 150.0,
            change = 2.5,
            changePercent = 1.7,
            timestamp = 1000L
        )

        val firstResult = repository.fetchQuote("AAPL", forceRefresh = true)
        assertTrue(firstResult.isSuccess)
        assertEquals(1, fakeApi.quoteCallCount)
        assertEquals(150.0, firstResult.getOrNull()?.lastKnownPrice ?: 0.0, 0.001)

        // Advance mock clock by 45 seconds (< 60s cache threshold)
        mockCurrentTime += 45_000L

        // Change API response to verify if network is contacted or cached data is served
        fakeApi.quoteResponse = FinnhubQuoteResponse(
            currentPrice = 999.0,
            change = 0.0,
            changePercent = 0.0
        )

        val secondResult = repository.fetchQuote("AAPL", forceRefresh = false)
        assertTrue(secondResult.isSuccess)
        // Network count must NOT increase
        assertEquals(1, fakeApi.quoteCallCount)
        // Cached price (150.0) must be served, not 999.0
        assertEquals(150.0, secondResult.getOrNull()?.lastKnownPrice ?: 0.0, 0.001)
    }

    @Test
    fun testCachingAfter60Seconds_makesNetworkCall() = runBlocking {
        // First fetch
        fakeApi.quoteResponse = FinnhubQuoteResponse(currentPrice = 150.0, changePercent = 1.0)
        repository.fetchQuote("AAPL", forceRefresh = true)
        assertEquals(1, fakeApi.quoteCallCount)

        // Advance mock clock by 61 seconds (> 60s cache threshold)
        mockCurrentTime += 61_000L

        fakeApi.quoteResponse = FinnhubQuoteResponse(currentPrice = 155.0, changePercent = 3.3)
        val refreshedResult = repository.fetchQuote("AAPL", forceRefresh = false)

        assertTrue(refreshedResult.isSuccess)
        // Network count MUST increase
        assertEquals(2, fakeApi.quoteCallCount)
        assertEquals(155.0, refreshedResult.getOrNull()?.lastKnownPrice ?: 0.0, 0.001)
    }

    @Test
    fun testForceRefresh_makesNetworkCallEvenWithin60Seconds() = runBlocking {
        // Initial fetch
        fakeApi.quoteResponse = FinnhubQuoteResponse(currentPrice = 200.0, changePercent = 0.5)
        repository.fetchQuote("MSFT", forceRefresh = true)
        assertEquals(1, fakeApi.quoteCallCount)

        // Only 10 seconds elapsed
        mockCurrentTime += 10_000L

        fakeApi.quoteResponse = FinnhubQuoteResponse(currentPrice = 205.0, changePercent = 2.5)
        // Pull-to-refresh / forceRefresh = true
        val forceResult = repository.fetchQuote("MSFT", forceRefresh = true)

        assertTrue(forceResult.isSuccess)
        assertEquals(2, fakeApi.quoteCallCount)
        assertEquals(205.0, forceResult.getOrNull()?.lastKnownPrice ?: 0.0, 0.001)
    }

    @Test
    fun testFailedApiResponse_ioException_handledGracefully() = runBlocking {
        fakeApi.shouldThrow = IOException("Simulated airplane mode / network down")

        val result = repository.fetchQuote("TSLA", forceRefresh = true)
        // Must return failure, not throw an unhandled exception
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(errorMsg.contains("No internet connection") || errorMsg.contains("Simulated"))
    }

    @Test
    fun testFailedApiResponse_rateLimit429_handledGracefully() = runBlocking {
        val errorBody = "{\"error\":\"API limit reached\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApi.shouldThrow = HttpException(Response.error<Any>(429, errorBody))

        val result = repository.fetchQuote("NVDA", forceRefresh = true)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(errorMsg.contains("Rate limit reached"))
    }

    @Test
    fun testInvalidSymbol_allZerosQuote_returnsFailure() = runBlocking {
        // Finnhub returns 0.0 for c and pc on invalid symbols
        fakeApi.quoteResponse = FinnhubQuoteResponse(currentPrice = 0.0, previousClosePrice = 0.0)

        val result = repository.fetchQuote("INVALID_TICKER", forceRefresh = true)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(errorMsg.contains("not found or returned invalid quote"))
    }

    @Test
    fun testSearchSymbols_returnsMatchingResultsOrHandlesFailure() = runBlocking {
        fakeApi.searchResponse = FinnhubSearchResponse(
            count = 2,
            result = listOf(
                FinnhubSearchResult("Apple Inc", "AAPL", "AAPL", "Common Stock"),
                FinnhubSearchResult("Apple Hospitality REIT", "APLE", "APLE", "REIT")
            )
        )

        val searchResult = repository.searchSymbols("Apple")
        assertTrue(searchResult.isSuccess)
        val results = searchResult.getOrNull() ?: emptyList()
        assertEquals(2, results.size)
        assertEquals("AAPL", results[0].symbol)

        // Failure handling
        fakeApi.shouldThrow = IOException("Search timeout")
        val failedSearch = repository.searchSymbols("Apple")
        assertTrue(failedSearch.isFailure)
    }

    // =========================================================================
    // Test Doubles (Fakes)
    // =========================================================================

    private class FakeFinnhubApiService : FinnhubApiService {
        var quoteResponse: FinnhubQuoteResponse = FinnhubQuoteResponse(currentPrice = 100.0, changePercent = 1.0)
        var searchResponse: FinnhubSearchResponse = FinnhubSearchResponse()
        var shouldThrow: Throwable? = null
        var quoteCallCount = 0
        var searchCallCount = 0

        override suspend fun getQuote(symbol: String): FinnhubQuoteResponse {
            quoteCallCount++
            shouldThrow?.let { throw it }
            return quoteResponse
        }

        override suspend fun searchSymbol(query: String): FinnhubSearchResponse {
            searchCallCount++
            shouldThrow?.let { throw it }
            return searchResponse
        }
    }

    private class FakeWatchlistDao : WatchlistDao {
        private val stocks = mutableMapOf<String, WatchlistStock>()

        override suspend fun insert(stock: WatchlistStock): Long {
            stocks[stock.symbol.uppercase()] = stock
            return stocks.size.toLong()
        }

        override suspend fun update(stock: WatchlistStock) {
            stocks[stock.symbol.uppercase()] = stock
        }

        override suspend fun delete(stock: WatchlistStock) {
            stocks.remove(stock.symbol.uppercase())
        }

        override fun getAll(): Flow<List<WatchlistStock>> {
            return flowOf(stocks.values.toList())
        }

        override suspend fun getById(id: Int): WatchlistStock? {
            return stocks.values.firstOrNull { it.id == id }
        }

        override suspend fun getBySymbol(symbol: String): WatchlistStock? {
            return stocks[symbol.uppercase().trim()]
        }

        override suspend fun updatePrice(symbol: String, price: Double, change: Double, fetchedAt: Long) {
            val key = symbol.uppercase().trim()
            val existing = stocks[key] ?: WatchlistStock(symbol = key, displayName = key)
            stocks[key] = existing.copy(
                lastKnownPrice = price,
                dayChangePercent = change,
                lastFetchedAt = fetchedAt
            )
        }
    }
}
