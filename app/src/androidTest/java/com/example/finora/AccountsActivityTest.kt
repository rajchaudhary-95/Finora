package com.example.finora

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.ui.accounts.AccountsActivity
import com.example.finora.ui.accounts.AddEditAccountActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsActivityTest {

    private lateinit var app: FinoraApp

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        app = context.applicationContext as FinoraApp
        // Clean accounts before test run
        val accounts = app.accountRepository.allAccounts.first()
        for (acc in accounts) {
            app.accountRepository.delete(acc)
        }
    }

    private fun waitUntilDoesNotExist(matcher: Matcher<View>, timeoutMs: Long = 3000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                onView(matcher).check(doesNotExist())
                return
            } catch (e: Throwable) {
                Thread.sleep(100)
            }
        }
        onView(matcher).check(doesNotExist())
    }

    /**
     * Requirement: Editing an account passes its ID via Intent extras — confirmed in code & unit tests.
     */
    @Test
    fun testExplicitIntentExtrasPassing() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. New account intent: defaults to NEW_ACCOUNT_ID (-1)
        val newIntent = AddEditAccountActivity.createIntent(context)
        assertEquals(
            AddEditAccountActivity.NEW_ACCOUNT_ID,
            newIntent.getIntExtra(AddEditAccountActivity.EXTRA_ACCOUNT_ID, 0)
        )
        assertEquals(AddEditAccountActivity::class.java.name, newIntent.component?.className)

        // 2. Edit account intent: passes exact account ID
        val editIntent = AddEditAccountActivity.createIntent(context, accountId = 101)
        assertEquals(
            101,
            editIntent.getIntExtra(AddEditAccountActivity.EXTRA_ACCOUNT_ID, -1)
        )
        assertEquals(AddEditAccountActivity::class.java.name, editIntent.component?.className)
    }

    /**
     * Requirement: Adding an account through UI makes it appear in list and updates net worth.
     */
    @Test
    fun testAddAccountAppearsInListAndUpdatesNetWorth() {
        ActivityScenario.launch(AccountsActivity::class.java).use {
            // Tap FAB to add new account
            onView(withId(R.id.fab_add_account)).perform(click())

            // Fill form
            onView(withId(R.id.et_account_name)).perform(typeText("Salary Account"), closeSoftKeyboard())
            onView(withId(R.id.et_account_balance)).perform(clearText(), typeText("50000.0"), closeSoftKeyboard())

            // Save
            onView(withId(R.id.btn_save_account)).perform(click())

            // Verify account appears in list
            onView(withText("Salary Account")).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.tv_account_balance), withText("₹50,000.00"))).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.tv_total_balance), withText("₹50,000.00"))).check(matches(isDisplayed()))

            // Verify NetWorthRepository reflects the added balance
            runBlocking {
                val netWorth = app.netWorthRepository.getCashNetWorth().first()
                assertEquals(50000.0, netWorth, 0.01)
            }
        }
    }

    /**
     * Requirement: Editing updates displayed balance and net worth calculation.
     */
    @Test
    fun testEditAccountUpdatesBalanceAndNetWorth() {
        // Pre-insert an account
        runBlocking {
            app.accountRepository.insert(
                Account(name = "Savings Account", type = AccountType.BANK.storageValue, balance = 20000.0)
            )
        }

        ActivityScenario.launch(AccountsActivity::class.java).use {
            // Tap on existing account to edit
            onView(withText("Savings Account")).perform(click())

            // Edit balance to 35000.0
            onView(withId(R.id.et_account_balance)).perform(clearText(), typeText("35000.0"), closeSoftKeyboard())

            // Save
            onView(withId(R.id.btn_save_account)).perform(click())

            // Verify updated balance in list and summary
            onView(withText("Savings Account")).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.tv_account_balance), withText("₹35,000.00"))).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.tv_total_balance), withText("₹35,000.00"))).check(matches(isDisplayed()))

            // Verify NetWorthRepository reflects updated balance
            runBlocking {
                val netWorth = app.netWorthRepository.getCashNetWorth().first()
                assertEquals(35000.0, netWorth, 0.01)
            }
        }
    }

    /**
     * Requirement: Deleting an account through UI removes it and updates net worth.
     */
    @Test
    fun testDeleteAccountRemovesFromListAndUpdatesNetWorth() {
        // Pre-insert an account
        runBlocking {
            app.accountRepository.insert(
                Account(name = "Emergency Cash", type = AccountType.CASH.storageValue, balance = 5000.0)
            )
        }

        ActivityScenario.launch(AccountsActivity::class.java).use {
            // Confirm account is visible initially
            onView(withText("Emergency Cash")).check(matches(isDisplayed()))

            // Tap to edit
            onView(withText("Emergency Cash")).perform(click())

            // Tap Delete Account
            onView(withId(R.id.btn_delete_account)).perform(click())

            // Confirm AlertDialog
            onView(withText("Delete")).perform(click())

            // Verify empty state appears and account is not displayed
            onView(withId(R.id.layout_empty_state)).check(matches(isDisplayed()))
            onView(allOf(withText("Emergency Cash"), isDisplayed())).check(doesNotExist())
            onView(allOf(withId(R.id.tv_total_balance), withText("₹0.00"))).check(matches(isDisplayed()))

            // Verify NetWorthRepository reflects deletion
            runBlocking {
                val netWorth = app.netWorthRepository.getCashNetWorth().first()
                assertEquals(0.0, netWorth, 0.01)
                val remainingAccounts = app.accountRepository.allAccounts.first()
                assertEquals(0, remainingAccounts.size)
            }
        }
    }
}
