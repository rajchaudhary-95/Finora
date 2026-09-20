package com.example.finora

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.finora.data.db.FinoraDatabase
import com.example.finora.data.db.dao.*
import com.example.finora.data.db.entities.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinoraDatabaseTest {

    private lateinit var db: FinoraDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var portfolioDao: PortfolioDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        accountDao = db.accountDao()
        categoryDao = db.categoryDao()
        transactionDao = db.transactionDao()
        budgetDao = db.budgetDao()
        watchlistDao = db.watchlistDao()
        portfolioDao = db.portfolioDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    /**
     * Test 1: Insert one row into each entity and verify all fields are preserved.
     * Spot-checks: isAutoCategorized, rolloverEnabled, dayChangePercent, createdAt, updatedAt.
     */
    @Test
    fun testInsertAndReadEveryEntity() {
        runBlocking {
            // 1. Account
            val account = Account(
                name = "Main Checking",
                type = AccountType.BANK.storageValue,
                balance = 5420.50
            )
            val accountId = accountDao.insert(account).toInt()
            val readAccount = accountDao.getById(accountId)
            assertNotNull(readAccount)
            assertEquals("Main Checking", readAccount?.name)
            assertEquals("BANK", readAccount?.type)
            assertEquals(5420.50, readAccount?.balance ?: 0.0, 0.001)

            // 2. Category
            val category = Category(
                name = "Groceries",
                type = CategoryType.EXPENSE.storageValue,
                iconRes = 101,
                isSystemDefault = true
            )
            val categoryId = categoryDao.insert(category).toInt()
            val readCategory = categoryDao.getById(categoryId)
            assertNotNull(readCategory)
            assertEquals("Groceries", readCategory?.name)
            assertEquals("EXPENSE", readCategory?.type)
            assertEquals(101, readCategory?.iconRes)
            assertTrue(readCategory?.isSystemDefault == true)

            // 3. WatchlistStock
            val stock = WatchlistStock(
                symbol = "AAPL",
                displayName = "Apple Inc.",
                lastKnownPrice = 185.92,
                dayChangePercent = 1.45,
                lastFetchedAt = 1710000000000L
            )
            val stockId = watchlistDao.insert(stock).toInt()
            val readStock = watchlistDao.getBySymbol("AAPL")
            assertNotNull(readStock)
            assertEquals("AAPL", readStock?.symbol)
            assertEquals("Apple Inc.", readStock?.displayName)
            assertEquals(185.92, readStock?.lastKnownPrice ?: 0.0, 0.001)
            assertEquals(1.45, readStock?.dayChangePercent ?: 0.0, 0.001) // spot check
            assertEquals(1710000000000L, readStock?.lastFetchedAt)

            // 4. PortfolioHolding (links to WatchlistStock.symbol)
            val holding = PortfolioHolding(
                symbol = "AAPL",
                sharesOwned = 15.5,
                avgBuyPrice = 160.0
            )
            val holdingId = portfolioDao.insert(holding).toInt()
            val readHolding = portfolioDao.getBySymbol("AAPL")
            assertNotNull(readHolding)
            assertEquals("AAPL", readHolding?.symbol)
            assertEquals(15.5, readHolding?.sharesOwned ?: 0.0, 0.001)
            assertEquals(160.0, readHolding?.avgBuyPrice ?: 0.0, 0.001)

            // 5. Budget
            val budget = Budget(
                categoryId = categoryId,
                monthlyLimit = 600.0,
                rolloverEnabled = true, // spot check
                month = "2026-09"
            )
            val budgetId = budgetDao.insert(budget).toInt()
            val readBudget = budgetDao.getById(budgetId)
            assertNotNull(readBudget)
            assertEquals(categoryId, readBudget?.categoryId)
            assertEquals(600.0, readBudget?.monthlyLimit ?: 0.0, 0.001)
            assertTrue(readBudget?.rolloverEnabled == true)
            assertEquals("2026-09", readBudget?.month)

            // 6. Transaction
            val txTime = System.currentTimeMillis()
            val transaction = Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = 84.75, // positive magnitude per spec
                merchant = "Supermarket",
                note = "Weekly grocery run",
                date = txTime,
                receiptImagePath = "/storage/receipt_123.jpg",
                latitude = 37.422,
                longitude = -122.084,
                address = "1600 Amphitheatre Pkwy, Mountain View, CA",
                isRecurring = false,
                isAutoCategorized = true, // spot check
                createdAt = txTime,
                updatedAt = txTime
            )
            val txId = transactionDao.insert(transaction).toInt()
            val readTx = transactionDao.getById(txId)
            assertNotNull(readTx)
            assertEquals(accountId, readTx?.accountId)
            assertEquals(categoryId, readTx?.categoryId)
            assertEquals(84.75, readTx?.amount ?: 0.0, 0.001)
            assertEquals("Supermarket", readTx?.merchant)
            assertEquals("Weekly grocery run", readTx?.note)
            assertEquals("/storage/receipt_123.jpg", readTx?.receiptImagePath)
            assertEquals(37.422, readTx?.latitude ?: 0.0, 0.0001)
            assertEquals(-122.084, readTx?.longitude ?: 0.0, 0.0001)
            assertEquals("1600 Amphitheatre Pkwy, Mountain View, CA", readTx?.address)
            assertFalse(readTx?.isRecurring ?: true)
            assertTrue(readTx?.isAutoCategorized == true) // spot check
            assertEquals(txTime, readTx?.createdAt)
            assertEquals(txTime, readTx?.updatedAt)
        }
    }

    /**
     * Test 2: The unique constraint on WatchlistStock.symbol must reject duplicate inserts.
     */
    @Test
    fun testDuplicateSymbolFailsToInsert() {
        runBlocking {
            val stock1 = WatchlistStock(symbol = "GOOGL", displayName = "Alphabet Inc.")
            watchlistDao.insert(stock1)

            val stock2 = WatchlistStock(symbol = "GOOGL", displayName = "Duplicate Alphabet")
            try {
                watchlistDao.insert(stock2)
                fail("Expected SQLiteConstraintException when inserting duplicate WatchlistStock symbol")
            } catch (e: SQLiteConstraintException) {
                // Expected exception thrown due to UNIQUE constraint
                assertTrue(
                    e.message?.contains("UNIQUE", ignoreCase = true) == true ||
                    e.message?.contains("watchlist_stocks.symbol", ignoreCase = true) == true ||
                    e.message != null
                )
            }
        }
    }

    /**
     * Test 3: The unique constraint on Budget(categoryId, month) must reject duplicate inserts.
     */
    @Test
    fun testDuplicateBudgetForSameMonthFails() {
        runBlocking {
            val categoryId = categoryDao.insert(
                Category(name = "Dining", type = "EXPENSE")
            ).toInt()

            val b1 = Budget(categoryId = categoryId, monthlyLimit = 300.0, month = "2026-09")
            budgetDao.insert(b1)

            val b2 = Budget(categoryId = categoryId, monthlyLimit = 400.0, month = "2026-09")
            try {
                budgetDao.insert(b2)
                fail("Expected SQLiteConstraintException on duplicate (categoryId, month) budget")
            } catch (e: SQLiteConstraintException) {
                // Expected
            }
        }
    }

    /**
     * Test 4: ForeignKey RESTRICT on Transaction -> Account prevents deleting account with transactions.
     */
    @Test
    fun testAccountDeleteRestrictedWithTransactions() {
        runBlocking {
            val accountId = accountDao.insert(Account(name = "Protected", type = "CASH")).toInt()
            val account = accountDao.getById(accountId)!!

            val tx = Transaction(
                accountId = accountId,
                amount = 50.0,
                merchant = "Coffee Shop"
            )
            transactionDao.insert(tx)

            try {
                accountDao.delete(account)
                fail("Expected SQLiteConstraintException when deleting account with linked transactions")
            } catch (e: SQLiteConstraintException) {
                // Expected: ForeignKey RESTRICT prevents deletion
            }
        }
    }

    /**
     * Test 5: Verify default categories seeded via SeedCallback when created.
     */
    @Test
    fun testDefaultSeededCategories() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase("test_seed_db")
            val diskDb = FinoraDatabase.buildDatabase(context, "test_seed_db")
                .also {
                    // Open the database to trigger onCreate callback
                    it.openHelper.writableDatabase
                }

            // Read seeded categories
            val categories = diskDb.categoryDao().getAll().first()
            assertTrue("Default categories should be seeded (at least 10)", categories.size >= 10)

            val categoryNames = categories.map { it.name }
            assertTrue(categoryNames.contains("Groceries"))
            assertTrue(categoryNames.contains("Food & Dining"))
            assertTrue(categoryNames.contains("Transport"))
            assertTrue(categoryNames.contains("Shopping"))
            assertTrue(categoryNames.contains("Subscriptions"))
            assertTrue(categoryNames.contains("Bills & Utilities"))
            assertTrue(categoryNames.contains("Entertainment"))
            assertTrue(categoryNames.contains("Salary"))
            assertTrue(categoryNames.contains("Other Income"))
            assertTrue(categoryNames.contains("Other Expense"))

            diskDb.close()
            context.deleteDatabase("test_seed_db")
        }
    }
}
