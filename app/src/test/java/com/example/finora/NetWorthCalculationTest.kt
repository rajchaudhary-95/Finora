package com.example.finora

import com.example.finora.data.db.dao.AccountDao
import com.example.finora.data.db.dao.PortfolioDao
import com.example.finora.data.db.dao.WatchlistDao
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.PortfolioHolding
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.repository.NetWorthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetWorthCalculationTest {

    private lateinit var fakeAccountDao: FakeAccountDao
    private lateinit var fakePortfolioDao: FakePortfolioDao
    private lateinit var fakeWatchlistDao: FakeWatchlistDao
    private lateinit var repository: NetWorthRepository

    @Before
    fun setup() {
        fakeAccountDao = FakeAccountDao()
        fakePortfolioDao = FakePortfolioDao()
        fakeWatchlistDao = FakeWatchlistDao()
        repository = NetWorthRepository(
            accountDao = fakeAccountDao,
            portfolioDao = fakePortfolioDao,
            watchlistDao = fakeWatchlistDao
        )
    }

    @Test
    fun testCombinedNetWorth_matchesHandCalculatedValue() = runBlocking {
        // 1. Manually seed Accounts:
        // Checking: $15,000.00
        // Savings:  $30,000.00
        // Credit Card liability: -$3,500.00
        // Expected Cash Total: $41,500.00
        fakeAccountDao.accountsFlow.value = listOf(
            Account(id = 1, name = "Checking", type = "BANK", balance = 15000.0),
            Account(id = 2, name = "Savings", type = "BANK", balance = 30000.0),
            Account(id = 3, name = "Credit Card", type = "CREDIT_CARD", balance = -3500.0)
        )

        // 2. Manually seed Portfolio Holdings:
        // AAPL:  20 shares (avg buy: $150.00)
        // MSFT:  10 shares (avg buy: $280.00)
        // GOOGL:  5 shares (avg buy: $120.00)
        fakePortfolioDao.holdingsFlow.value = listOf(
            PortfolioHolding(id = 1, symbol = "AAPL", sharesOwned = 20.0, avgBuyPrice = 150.0),
            PortfolioHolding(id = 2, symbol = "MSFT", sharesOwned = 10.0, avgBuyPrice = 280.0),
            PortfolioHolding(id = 3, symbol = "GOOGL", sharesOwned = 5.0, avgBuyPrice = 120.0)
        )

        // 3. Manually seed Watchlist Cached Prices:
        // AAPL:  $200.00 -> 20 * 200 = $4,000.00
        // MSFT:  $350.00 -> 10 * 350 = $3,500.00
        // GOOGL: $180.00 ->  5 * 180 =   $900.00
        // Expected Investments Total: $4,000 + $3,500 + $900 = $8,400.00
        fakeWatchlistDao.stocksFlow.value = listOf(
            WatchlistStock(id = 1, symbol = "AAPL", displayName = "Apple", lastKnownPrice = 200.0),
            WatchlistStock(id = 2, symbol = "MSFT", displayName = "Microsoft", lastKnownPrice = 350.0),
            WatchlistStock(id = 3, symbol = "GOOGL", displayName = "Alphabet", lastKnownPrice = 180.0)
        )

        // Expected Total Net Worth = $41,500.00 + $8,400.00 = $49,900.00
        val breakdown = repository.getNetWorthBreakdown().first()

        assertEquals(41500.0, breakdown.cashBalance, 0.001)
        assertEquals(8400.0, breakdown.investmentValue, 0.001)
        assertEquals(49900.0, breakdown.totalNetWorth, 0.001)
    }

    @Test
    fun testUnwatchedSymbolInPortfolio_safelyDefaultsToZeroWithoutCrashing() = runBlocking {
        fakeAccountDao.accountsFlow.value = listOf(
            Account(id = 1, name = "Cash", type = "CASH", balance = 5000.0)
        )

        // Symbol XYZ is held but NOT in WatchlistStock
        fakePortfolioDao.holdingsFlow.value = listOf(
            PortfolioHolding(id = 1, symbol = "XYZ", sharesOwned = 100.0, avgBuyPrice = 10.0)
        )

        fakeWatchlistDao.stocksFlow.value = emptyList()

        val breakdown = repository.getNetWorthBreakdown().first()
        assertEquals(5000.0, breakdown.cashBalance, 0.001)
        // XYZ has no cached price, contributes 0.0
        assertEquals(0.0, breakdown.investmentValue, 0.001)
        assertEquals(5000.0, breakdown.totalNetWorth, 0.001)
    }

    @Test
    fun testNegativeNetWorth_whenDebtsExceedAssets() = runBlocking {
        fakeAccountDao.accountsFlow.value = listOf(
            Account(id = 1, name = "Checking", type = "BANK", balance = 1000.0),
            Account(id = 2, name = "Card Debt", type = "CREDIT_CARD", balance = -6000.0)
        )
        fakePortfolioDao.holdingsFlow.value = emptyList()
        fakeWatchlistDao.stocksFlow.value = emptyList()

        val breakdown = repository.getNetWorthBreakdown().first()
        assertEquals(-5000.0, breakdown.totalNetWorth, 0.001)
    }

    @Test
    fun testReactivePriceUpdate_updatesNetWorthImmediately() = runBlocking {
        fakeAccountDao.accountsFlow.value = listOf(
            Account(id = 1, name = "Bank", type = "BANK", balance = 10000.0)
        )
        fakePortfolioDao.holdingsFlow.value = listOf(
            PortfolioHolding(id = 1, symbol = "AAPL", sharesOwned = 10.0, avgBuyPrice = 100.0)
        )
        fakeWatchlistDao.stocksFlow.value = listOf(
            WatchlistStock(id = 1, symbol = "AAPL", displayName = "Apple", lastKnownPrice = 100.0)
        )

        // Initial: $10,000 cash + $1,000 stock = $11,000
        var breakdown = repository.getNetWorthBreakdown().first()
        assertEquals(11000.0, breakdown.totalNetWorth, 0.001)

        // Price jumps to $250.00
        fakeWatchlistDao.stocksFlow.value = listOf(
            WatchlistStock(id = 1, symbol = "AAPL", displayName = "Apple", lastKnownPrice = 250.0)
        )

        // Updated: $10,000 cash + $2,500 stock = $12,500
        breakdown = repository.getNetWorthBreakdown().first()
        assertEquals(12500.0, breakdown.totalNetWorth, 0.001)
    }

    // =========================================================================
    // Fakes
    // =========================================================================

    private class FakeAccountDao : AccountDao {
        val accountsFlow = MutableStateFlow<List<Account>>(emptyList())

        override suspend fun insert(account: Account): Long = 0L
        override suspend fun update(account: Account) {}
        override suspend fun delete(account: Account) {}
        override fun getAll(): Flow<List<Account>> = accountsFlow
        override suspend fun getById(id: Int): Account? = accountsFlow.value.firstOrNull { it.id == id }
        override suspend fun adjustBalance(accountId: Int, delta: Double) {}
    }

    private class FakePortfolioDao : PortfolioDao {
        val holdingsFlow = MutableStateFlow<List<PortfolioHolding>>(emptyList())

        override suspend fun insert(holding: PortfolioHolding): Long = 0L
        override suspend fun update(holding: PortfolioHolding) {}
        override suspend fun delete(holding: PortfolioHolding) {}
        override fun getAll(): Flow<List<PortfolioHolding>> = holdingsFlow
        override suspend fun getById(id: Int): PortfolioHolding? = holdingsFlow.value.firstOrNull { it.id == id }
        override suspend fun getBySymbol(symbol: String): PortfolioHolding? =
            holdingsFlow.value.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
    }

    private class FakeWatchlistDao : WatchlistDao {
        val stocksFlow = MutableStateFlow<List<WatchlistStock>>(emptyList())

        override suspend fun insert(stock: WatchlistStock): Long = 0L
        override suspend fun update(stock: WatchlistStock) {}
        override suspend fun delete(stock: WatchlistStock) {}
        override fun getAll(): Flow<List<WatchlistStock>> = stocksFlow
        override suspend fun getById(id: Int): WatchlistStock? = stocksFlow.value.firstOrNull { it.id == id }
        override suspend fun getBySymbol(symbol: String): WatchlistStock? =
            stocksFlow.value.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
        override suspend fun updatePrice(symbol: String, price: Double, change: Double, fetchedAt: Long) {}
    }
}
