package com.example.finora

import android.app.Application
import com.example.finora.data.db.FinoraDatabase
import com.example.finora.repository.*
import com.example.finora.util.StockPriceHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class providing database and repository singletons.
 * Triggers initial database creation and category seeding on app startup.
 */
class FinoraApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: FinoraDatabase by lazy { FinoraDatabase.getInstance(this) }

    val accountRepository: AccountRepository by lazy { AccountRepository(database.accountDao()) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val transactionRepository: TransactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database.budgetDao()) }
    val stockPriceHistoryStore: StockPriceHistoryStore by lazy { StockPriceHistoryStore(this) }
    val watchlistRepository: WatchlistRepository by lazy {
        WatchlistRepository(
            watchlistDao = database.watchlistDao(),
            historyStore = stockPriceHistoryStore
        )
    }
    val portfolioRepository: PortfolioRepository by lazy { PortfolioRepository(database.portfolioDao()) }
    val netWorthRepository: NetWorthRepository by lazy {
        NetWorthRepository(
            accountDao = database.accountDao(),
            portfolioDao = database.portfolioDao(),
            watchlistDao = database.watchlistDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure database is opened and default categories are seeded on first launch
        applicationScope.launch {
            database.openHelper.writableDatabase
        }
    }
}
