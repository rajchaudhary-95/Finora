package com.example.finora

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.finora.data.db.FinoraDatabase
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.repository.*
import com.example.finora.ui.accounts.AccountsViewModel
import com.example.finora.ui.budget.BudgetViewModel
import com.example.finora.ui.dashboard.DashboardViewModel
import com.example.finora.ui.transactions.TransactionsViewModel
import com.example.finora.ui.watchlist.WatchlistViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryAndViewModelTest {

    private lateinit var db: FinoraDatabase
    private lateinit var accountRepo: AccountRepository
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var budgetRepo: BudgetRepository
    private lateinit var watchlistRepo: WatchlistRepository
    private lateinit var portfolioRepo: PortfolioRepository
    private lateinit var netWorthRepo: NetWorthRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        accountRepo = AccountRepository(db.accountDao())
        transactionRepo = TransactionRepository(db.transactionDao())
        budgetRepo = BudgetRepository(db.budgetDao())
        watchlistRepo = WatchlistRepository(db.watchlistDao())
        portfolioRepo = PortfolioRepository(db.portfolioDao())
        netWorthRepo = NetWorthRepository(db.accountDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Verifies that NetWorthRepository derives the correct cash-only net worth:
     * Σ(Account.balance) where debt/credit is negative.
     * Worked example from Plan §7.6:
     * Bank (45,000) + Cash (2,500) + Credit Card (-3,200) = 44,300
     */
    @Test
    fun testNetWorthCalculationCashOnly(): Unit = runBlocking {
        // Initial net worth with no accounts should be 0.0
        val initialNetWorth = netWorthRepo.getCashNetWorth().first()
        assertEquals(0.0, initialNetWorth, 0.001)

        // Insert accounts matching Plan §7.6 worked example
        accountRepo.insert(
            Account(name = "Bank", type = AccountType.BANK.storageValue, balance = 45000.0)
        )
        accountRepo.insert(
            Account(name = "Cash", type = AccountType.CASH.storageValue, balance = 2500.0)
        )
        accountRepo.insert(
            Account(name = "Credit Card", type = AccountType.CREDIT_CARD.storageValue, balance = -3200.0)
        )

        // Verify computed sum
        val calculatedNetWorth = netWorthRepo.getCashNetWorth().first()
        assertEquals(44300.0, calculatedNetWorth, 0.001)

        // Adjust balance and verify reactive Flow emission
        val accounts = accountRepo.allAccounts.first()
        val cashAccount = accounts.first { it.name == "Cash" }
        accountRepo.adjustBalance(cashAccount.id, 500.0) // Cash becomes 3,000

        val updatedNetWorth = netWorthRepo.getCashNetWorth().first()
        assertEquals(44800.0, updatedNetWorth, 0.001)
    }

    /**
     * Verifies all 5 ViewModel skeletons can be instantiated via factories and expose valid StateFlows.
     */
    @Test
    fun testViewModelInstantiationAndStateFlows() {
        val viewModelStore = ViewModelStore()

        // 1. DashboardViewModel
        val dashboardFactory = DashboardViewModel.Factory(netWorthRepo, transactionRepo)
        val dashboardVm = ViewModelProvider(viewModelStore, dashboardFactory)[DashboardViewModel::class.java]
        assertNotNull(dashboardVm)
        assertNotNull(dashboardVm.netWorth)
        assertNotNull(dashboardVm.recurringTransactions)
        assertEquals(0.0, dashboardVm.netWorth.value, 0.001)

        // 2. TransactionsViewModel
        val transactionsFactory = TransactionsViewModel.Factory(transactionRepo, accountRepo)
        val transactionsVm = ViewModelProvider(viewModelStore, transactionsFactory)[TransactionsViewModel::class.java]
        assertNotNull(transactionsVm)
        assertNotNull(transactionsVm.transactions)
        assertNotNull(transactionsVm.accounts)
        assertTrue(transactionsVm.transactions.value.isEmpty())

        // 3. AccountsViewModel
        val accountsFactory = AccountsViewModel.Factory(accountRepo)
        val accountsVm = ViewModelProvider(viewModelStore, accountsFactory)[AccountsViewModel::class.java]
        assertNotNull(accountsVm)
        assertNotNull(accountsVm.accounts)
        assertTrue(accountsVm.accounts.value.isEmpty())

        // 4. BudgetViewModel
        val budgetFactory = BudgetViewModel.Factory(budgetRepo, transactionRepo)
        val budgetVm = ViewModelProvider(viewModelStore, budgetFactory)[BudgetViewModel::class.java]
        assertNotNull(budgetVm)
        assertNotNull(budgetVm.budgets)
        assertTrue(budgetVm.budgets.value.isEmpty())

        // 5. WatchlistViewModel
        val watchlistFactory = WatchlistViewModel.Factory(watchlistRepo, portfolioRepo)
        val watchlistVm = ViewModelProvider(viewModelStore, watchlistFactory)[WatchlistViewModel::class.java]
        assertNotNull(watchlistVm)
        assertNotNull(watchlistVm.watchlist)
        assertNotNull(watchlistVm.holdings)
        assertTrue(watchlistVm.watchlist.value.isEmpty())
        assertTrue(watchlistVm.holdings.value.isEmpty())

        viewModelStore.clear()
    }
}
