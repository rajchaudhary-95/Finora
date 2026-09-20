package com.example.finora

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.ui.accounts.AccountsActivity
import com.example.finora.ui.transactions.AddEditTransactionActivity
import com.example.finora.ui.transactions.TransactionDetailActivity
import com.example.finora.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionFlowTest {

    private lateinit var app: FinoraApp
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setup(): Unit = runBlocking {
        app = ApplicationProvider.getApplicationContext<Context>() as FinoraApp
        viewModel = TransactionsViewModel(
            app.transactionRepository,
            app.accountRepository,
            app.categoryRepository
        )

        // Clean out existing transactions and accounts for isolated tests
        val transactions = app.transactionRepository.allTransactions.first()
        transactions.forEach { app.transactionRepository.delete(it) }

        val accounts = app.accountRepository.allAccounts.first()
        accounts.forEach { app.accountRepository.delete(it) }

        // Ensure seeded categories exist
        val catCount = app.categoryRepository.getCount()
        if (catCount == 0) {
            app.categoryRepository.insert(Category(name = "Salary", type = "INCOME"))
            app.categoryRepository.insert(Category(name = "Groceries", type = "EXPENSE"))
        }
    }

    /**
     * Verifies that creating an income transaction increases account balance,
     * and creating an expense transaction decreases account balance.
     */
    @Test
    fun testCreateIncomeAndExpenseTransactions_updateAccountBalance(): Unit = runBlocking {
        // 1. Create account with initial balance $1,000.00
        val accountId = app.accountRepository.insert(
            Account(name = "Checking Account", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        val salaryCat = app.categoryRepository.getByName("Salary") ?: run {
            val id = app.categoryRepository.insert(Category(name = "Salary", type = "INCOME")).toInt()
            app.categoryRepository.getById(id)!!
        }

        val groceriesCat = app.categoryRepository.getByName("Groceries") ?: run {
            val id = app.categoryRepository.insert(Category(name = "Groceries", type = "EXPENSE")).toInt()
            app.categoryRepository.getById(id)!!
        }

        // 2. Create Income transaction of $500.00
        val incomeTx = Transaction(
            accountId = accountId,
            categoryId = salaryCat.id,
            amount = 500.0,
            merchant = "Acme Corp Salary"
        )
        viewModel.createTransaction(incomeTx)

        // Assert account balance increased to $1,500.00
        var account = app.accountRepository.getById(accountId)
        assertNotNull(account)
        assertEquals(1500.0, account!!.balance, 0.001)

        // 3. Create Expense transaction of $200.00
        val expenseTx = Transaction(
            accountId = accountId,
            categoryId = groceriesCat.id,
            amount = 200.0,
            merchant = "Trader Joe's"
        )
        viewModel.createTransaction(expenseTx)

        // Assert account balance decreased to $1,300.00
        account = app.accountRepository.getById(accountId)
        assertNotNull(account)
        assertEquals(1300.0, account!!.balance, 0.001)
    }

    /**
     * Verifies that editing a transaction amount adjusts the account balance by the net delta,
     * avoiding double counting.
     */
    @Test
    fun testEditTransactionAmount_adjustsAccountBalanceByDelta(): Unit = runBlocking {
        // 1. Create account with $1,000.00
        val accountId = app.accountRepository.insert(
            Account(name = "Checking", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        val groceriesCat = app.categoryRepository.getByName("Groceries") ?: run {
            val id = app.categoryRepository.insert(Category(name = "Groceries", type = "EXPENSE")).toInt()
            app.categoryRepository.getById(id)!!
        }

        // 2. Create $200.00 expense -> Balance becomes $800.00
        val expenseTx = Transaction(
            accountId = accountId,
            categoryId = groceriesCat.id,
            amount = 200.0,
            merchant = "Supermarket"
        )
        val txId = viewModel.createTransaction(expenseTx).toInt()

        var account = app.accountRepository.getById(accountId)
        assertEquals(800.0, account!!.balance, 0.001)

        // 3. Edit transaction amount to $250.00 (+$50 expense, so balance decreases by $50 to $750.00)
        val originalTx = app.transactionRepository.getById(txId)!!
        val updatedTx = originalTx.copy(amount = 250.0)
        viewModel.updateTransaction(updatedTx, originalTx)

        account = app.accountRepository.getById(accountId)
        assertEquals(750.0, account!!.balance, 0.001)

        // 4. Edit transaction amount to $100.00 (-$150 expense, so balance increases by $150 to $900.00)
        val currentTx = app.transactionRepository.getById(txId)!!
        val reducedTx = currentTx.copy(amount = 100.0)
        viewModel.updateTransaction(reducedTx, currentTx)

        account = app.accountRepository.getById(accountId)
        assertEquals(900.0, account!!.balance, 0.001)
    }

    /**
     * Verifies that deleting a transaction reverses its effect on the account balance.
     */
    @Test
    fun testDeleteTransaction_reversesAccountBalance(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Savings", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        val groceriesCat = app.categoryRepository.getByName("Groceries") ?: run {
            val id = app.categoryRepository.insert(Category(name = "Groceries", type = "EXPENSE")).toInt()
            app.categoryRepository.getById(id)!!
        }

        // Create $150.00 expense -> balance becomes $850.00
        val tx = Transaction(
            accountId = accountId,
            categoryId = groceriesCat.id,
            amount = 150.0,
            merchant = "Grocery Store"
        )
        val txId = viewModel.createTransaction(tx).toInt()
        assertEquals(850.0, app.accountRepository.getById(accountId)!!.balance, 0.001)

        // Delete transaction -> balance reverts (+150) back to $1000.00
        val savedTx = app.transactionRepository.getById(txId)!!
        viewModel.deleteTransaction(savedTx)

        val account = app.accountRepository.getById(accountId)
        assertEquals(1000.0, account!!.balance, 0.001)
        assertNull(app.transactionRepository.getById(txId))
    }

    /**
     * Verifies that placeholder sections for receipt (Phase 6) and location (Phase 7)
     * are visible in both AddEditTransactionActivity and TransactionDetailActivity.
     */
    @Test
    fun testPlaceholderSectionsVisibleInAddEditAndDetail(): Unit = runBlocking {
        // 1. AddEditTransactionActivity placeholders
        ActivityScenario.launch(AddEditTransactionActivity::class.java).use {
            onView(withId(R.id.btn_scan_receipt_placeholder))
                .check(matches(isDisplayed()))
                .check(matches(not(isEnabled())))

            onView(withId(R.id.btn_tag_location_placeholder))
                .check(matches(isDisplayed()))
                .check(matches(not(isEnabled())))
        }

        // 2. TransactionDetailActivity placeholders
        val accountId = app.accountRepository.insert(
            Account(name = "Test Account", type = AccountType.CASH.storageValue, balance = 100.0)
        ).toInt()
        val txId = app.transactionRepository.insert(
            Transaction(accountId = accountId, amount = 25.0, merchant = "Coffee Shop")
        ).toInt()

        val detailIntent = Intent(ApplicationProvider.getApplicationContext(), TransactionDetailActivity::class.java).apply {
            putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, txId)
        }
        ActivityScenario.launch<TransactionDetailActivity>(detailIntent).use {
            onView(withId(R.id.layout_receipt_detail_placeholder)).check(matches(isDisplayed()))
            onView(withId(R.id.layout_location_detail_placeholder)).check(matches(isDisplayed()))
        }
    }

    /**
     * End-to-end UI test:
     * 1. Creates an account
     * 2. Navigates to Transactions tab
     * 3. Creates a transaction via AddEditTransactionActivity
     * 4. Views transaction details in TransactionDetailActivity
     * 5. Edits transaction amount
     * 6. Deletes transaction and confirms in dialog
     * 7. Verifies account balance on AccountsActivity reflects correct updates
     */
    @Test
    fun testEndToEndTransactionUiFlow(): Unit = runBlocking {
        // Setup initial account with $1,000.00
        val accountId = app.accountRepository.insert(
            Account(name = "Main Checking", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        // Launch MainActivity and switch to Transactions tab
        ActivityScenario.launch(MainActivity::class.java).use {
            // Ensure initial Dashboard tab is loaded
            onView(withId(R.id.btn_manage_accounts)).check(matches(isDisplayed()))
            Thread.sleep(500)

            // Switch to Transactions tab and ensure it is loaded
            onView(withId(R.id.nav_transactions)).perform(click())
            Thread.sleep(500)
            onView(withId(R.id.fab_add_transaction)).check(matches(isDisplayed()))

            // Click FAB to add transaction
            onView(withId(R.id.fab_add_transaction)).perform(click())

            // Fill Add Transaction Form
            onView(withId(R.id.et_amount)).check(matches(isDisplayed()))
            onView(withId(R.id.et_amount)).perform(typeText("150.00"), closeSoftKeyboard())
            onView(withId(R.id.et_merchant)).perform(typeText("Costco Wholesale"), closeSoftKeyboard())
            Thread.sleep(500)
            onView(withId(R.id.btn_save_transaction)).perform(scrollTo(), click())

            // Assert transaction appears in list and click card
            Thread.sleep(500)
            onView(allOf(withId(R.id.tv_merchant), withText("Costco Wholesale"))).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.card_transaction), hasDescendant(withText("Costco Wholesale")))).perform(click())

            // In TransactionDetailActivity: assert details and placeholders
            onView(withId(R.id.tv_detail_merchant)).check(matches(withText("Costco Wholesale")))
            onView(withId(R.id.layout_receipt_detail_placeholder)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.layout_location_detail_placeholder)).perform(scrollTo()).check(matches(isDisplayed()))

            // Click Edit Transaction
            onView(withId(R.id.btn_edit_transaction)).perform(scrollTo(), click())

            // In AddEditTransactionActivity (edit mode): edit amount to $200.00
            onView(withId(R.id.et_amount)).check(matches(isDisplayed()))
            onView(withId(R.id.et_amount)).perform(clearText(), typeText("200.00"), closeSoftKeyboard())
            Thread.sleep(500)
            onView(withId(R.id.btn_save_transaction)).perform(scrollTo(), click())

            // Detail screen reflects updated amount
            Thread.sleep(500)
            onView(withId(R.id.tv_detail_merchant)).check(matches(withText("Costco Wholesale")))
            onView(withId(R.id.tv_detail_amount)).check(matches(withText("-$200.00")))

            // Delete transaction
            onView(withId(R.id.btn_delete_transaction)).perform(scrollTo(), click())
            onView(withText("Delete")).inRoot(isDialog()).perform(click())
            Thread.sleep(500)

            // Back on Transactions tab: transaction is removed
            onView(allOf(withId(R.id.tv_merchant), withText("Costco Wholesale"))).check(doesNotExist())
        }

        // Verify Accounts screen shows restored balance of $1,000.00 (₹1,000.00)
        ActivityScenario.launch(AccountsActivity::class.java).use {
            onView(withText("Main Checking")).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.tv_account_balance), withText("₹1,000.00"))).check(matches(isDisplayed()))
        }
    }
}
