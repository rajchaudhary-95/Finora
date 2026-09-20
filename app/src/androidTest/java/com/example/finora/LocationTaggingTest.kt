package com.example.finora

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.ui.transactions.AddEditTransactionActivity
import com.example.finora.ui.transactions.TransactionDetailActivity
import com.example.finora.ui.transactions.TransactionsViewModel
import com.example.finora.util.HaversineUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Location Tagging (Phase 7).
 *
 * Testing note: Emulators support mock/injected location via Extended Controls (...) > Location.
 * Testing location retrieval on an emulator requires setting custom coordinates there.
 */
@RunWith(AndroidJUnit4::class)
class LocationTaggingTest {

    private lateinit var app: FinoraApp
    private lateinit var viewModel: TransactionsViewModel
    private lateinit var context: Context

    @Before
    fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        app = context as FinoraApp
        viewModel = TransactionsViewModel(
            app.transactionRepository,
            app.accountRepository,
            app.categoryRepository
        )

        // Clean out existing data
        val transactions = app.transactionRepository.allTransactions.first()
        transactions.forEach { app.transactionRepository.delete(it) }

        val accounts = app.accountRepository.allAccounts.first()
        accounts.forEach { app.accountRepository.delete(it) }

        // Seed default category if needed
        val catCount = app.categoryRepository.getCount()
        if (catCount == 0) {
            app.categoryRepository.insert(Category(name = "Groceries", type = "EXPENSE"))
        }
    }

    /**
     * Confirms that saving a transaction without tagging location
     * (or if location permission is denied) does NOT crash the app
     * and the transaction is successfully saved to Room with null coordinates.
     */
    @Test
    fun testSaveTransactionWithoutLocation_doesNotCrashAndSavesSuccessfully(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Wallet", type = AccountType.CASH.storageValue, balance = 250.0)
        ).toInt()

        ActivityScenario.launch(AddEditTransactionActivity::class.java).use {
            onView(withId(R.id.switch_tag_location)).check(matches(isDisplayed()))
            onView(withId(R.id.card_location_info)).check(matches(not(isDisplayed())))

            // Fill form without checking location switch
            onView(withId(R.id.et_amount)).perform(typeText("18.75"), closeSoftKeyboard())
            onView(withId(R.id.et_merchant)).perform(typeText("Corner Deli"), closeSoftKeyboard())

            onView(withId(R.id.btn_save_transaction)).perform(scrollTo(), click())
        }

        // Verify transaction is saved in Room with null location fields
        val saved = app.transactionRepository.allTransactions.first()
        assertEquals(1, saved.size)
        val tx = saved[0]
        assertEquals("Corner Deli", tx.merchant)
        assertEquals(18.75, tx.amount, 0.001)
        assertNull("Latitude should be null when not tagged", tx.latitude)
        assertNull("Longitude should be null when not tagged", tx.longitude)
        assertNull("Address should be null when not tagged", tx.address)
    }

    /**
     * Confirms that a transaction saved with latitude, longitude, and address
     * persists in Room and reloads accurately.
     */
    @Test
    fun testTransactionWithLocation_persistsToDatabaseAndSurvives(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Bank Checking", type = AccountType.BANK.storageValue, balance = 1500.0)
        ).toInt()

        val tx = Transaction(
            accountId = accountId,
            amount = 64.20,
            merchant = "Blue Bottle Coffee",
            latitude = 37.774929,
            longitude = -122.419416,
            address = "315 Linden St, San Francisco, CA 94102"
        )
        val txId = app.transactionRepository.insert(tx).toInt()

        val reloaded = app.transactionRepository.getById(txId)
        assertNotNull(reloaded)
        assertEquals(37.774929, reloaded!!.latitude!!, 0.000001)
        assertEquals(-122.419416, reloaded.longitude!!, 0.000001)
        assertEquals("315 Linden St, San Francisco, CA 94102", reloaded.address)
    }

    /**
     * Confirms that TransactionDetailActivity renders the location details and
     * the "Open in Maps" button when location coordinates are present, and hides
     * the entire section when coordinates are null.
     */
    @Test
    fun testTransactionDetail_displaysLocationAndMapsButtonWhenPresent_hidesWhenNull(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Credit Card", type = AccountType.CREDIT_CARD.storageValue, balance = 300.0)
        ).toInt()

        // 1. Transaction WITH location
        val txWithLocId = app.transactionRepository.insert(
            Transaction(
                accountId = accountId,
                amount = 120.0,
                merchant = "Apple Store",
                latitude = 37.4220,
                longitude = -122.0841,
                address = "1600 Amphitheatre Pkwy, Mountain View, CA"
            )
        ).toInt()

        val intentWithLoc = Intent(context, TransactionDetailActivity::class.java).apply {
            putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, txWithLocId)
        }
        ActivityScenario.launch<TransactionDetailActivity>(intentWithLoc).use {
            onView(withId(R.id.layout_location_detail_section)).check(matches(isDisplayed()))
            onView(withId(R.id.tv_detail_address)).check(matches(withText("1600 Amphitheatre Pkwy, Mountain View, CA")))
            onView(withId(R.id.btn_open_in_maps)).check(matches(isDisplayed()))
        }

        // 2. Transaction WITHOUT location
        val txWithoutLocId = app.transactionRepository.insert(
            Transaction(
                accountId = accountId,
                amount = 30.0,
                merchant = "Online Subscription",
                latitude = null,
                longitude = null,
                address = null
            )
        ).toInt()

        val intentWithoutLoc = Intent(context, TransactionDetailActivity::class.java).apply {
            putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, txWithoutLocId)
        }
        ActivityScenario.launch<TransactionDetailActivity>(intentWithoutLoc).use {
            onView(withId(R.id.layout_location_detail_section)).check(matches(not(isDisplayed())))
        }
    }

    /**
     * Confirms that TransactionDao.getGeotaggedTransactions() returns only transactions
     * with non-null latitude and longitude coordinates.
     */
    @Test
    fun testGeotaggedTransactionsQuery_returnsOnlyGeotagged(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Checking", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        // Geotagged 1
        app.transactionRepository.insert(
            Transaction(accountId = accountId, amount = 10.0, merchant = "Store 1", latitude = 37.1, longitude = -122.1)
        )
        // Geotagged 2
        app.transactionRepository.insert(
            Transaction(accountId = accountId, amount = 20.0, merchant = "Store 2", latitude = 37.2, longitude = -122.2)
        )
        // Non-geotagged
        app.transactionRepository.insert(
            Transaction(accountId = accountId, amount = 30.0, merchant = "Store 3", latitude = null, longitude = null)
        )

        val geotagged = app.transactionRepository.getGeotagged()
        assertEquals(2, geotagged.size)
        assertTrue(geotagged.all { it.latitude != null && it.longitude != null })
    }

    /**
     * Confirms that Haversine distance calculations properly sort geotagged transactions
     * nearest-first from a known reference point.
     */
    @Test
    fun testNearbySpending_distanceSortingOrdersNearestFirst() {
        val currentLat = 37.4220
        val currentLon = -122.0841

        // Near (~1.5 km away)
        val nearLat = 37.4300
        val nearLon = -122.0900
        val nearDist = HaversineUtil.calculateDistanceKm(currentLat, currentLon, nearLat, nearLon)

        // Mid (~8.0 km away)
        val midLat = 37.4800
        val midLon = -122.1400
        val midDist = HaversineUtil.calculateDistanceKm(currentLat, currentLon, midLat, midLon)

        // Far (~25.0 km away)
        val farLat = 37.6000
        val farLon = -122.3000
        val farDist = HaversineUtil.calculateDistanceKm(currentLat, currentLon, farLat, farLon)

        val unsortedDistances = listOf(farDist, nearDist, midDist)
        val sorted = unsortedDistances.sorted()

        assertEquals(nearDist, sorted[0], 0.001)
        assertEquals(midDist, sorted[1], 0.001)
        assertEquals(farDist, sorted[2], 0.001)
    }
}
