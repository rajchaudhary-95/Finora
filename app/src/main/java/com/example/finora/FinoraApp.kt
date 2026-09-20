package com.example.finora

import android.app.Application
import com.example.finora.data.db.FinoraDatabase
import com.example.finora.repository.*
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
    val watchlistRepository: WatchlistRepository by lazy { WatchlistRepository(database.watchlistDao()) }
    val portfolioRepository: PortfolioRepository by lazy { PortfolioRepository(database.portfolioDao()) }
    val stockRepository: StockRepository by lazy { StockRepository(database.watchlistDao(), database.portfolioDao()) }
    val netWorthRepository: NetWorthRepository by lazy { NetWorthRepository(database.accountDao()) }

    override fun onCreate() {
        super.onCreate()
        // Ensure database is opened and default categories are seeded on first launch
        applicationScope.launch {
            database.openHelper.writableDatabase
        }
    }
}
