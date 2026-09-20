package com.example.finora

import com.example.finora.data.db.entities.Category
import com.example.finora.util.CategorizationRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CategorizationRuleEngineTest {

    private lateinit var engine: CategorizationRuleEngine

    @Before
    fun setUp() {
        engine = CategorizationRuleEngine()
    }

    @Test
    fun testTransportKeywords() {
        assertEquals("Transport", engine.suggestCategory("Uber trip")?.name)
        assertEquals("Transport", engine.suggestCategory("Ola Cabs")?.name)
        assertEquals("Transport", engine.suggestCategory("Lyft Ride 123")?.name)
        assertEquals("Transport", engine.suggestCategory("Rapido Bike")?.name)
        assertEquals("Transport", engine.suggestCategory("City Metro Station")?.name)
        assertEquals("Transport", engine.suggestCategory("Yellow Cab Co")?.name)
        assertEquals("Transport", engine.suggestCategory("Airport Taxi")?.name)
    }

    @Test
    fun testFoodAndDiningKeywords() {
        assertEquals("Food & Dining", engine.suggestCategory("Swiggy Order")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("Zomato Delivery")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("DoorDash Dinner")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("Starbucks Coffee")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("McDonald's Meal")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("Dominos Pizza")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("Corner Cafe")?.name)
    }

    @Test
    fun testShoppingKeywords() {
        assertEquals("Shopping", engine.suggestCategory("Amazon Marketplace")?.name)
        assertEquals("Shopping", engine.suggestCategory("Flipkart Internet")?.name)
        assertEquals("Shopping", engine.suggestCategory("Myntra Fashion")?.name)
        assertEquals("Shopping", engine.suggestCategory("Zara Clothing")?.name)
        assertEquals("Shopping", engine.suggestCategory("Target Supercenter")?.name)
        assertEquals("Shopping", engine.suggestCategory("Nike Outlet")?.name)
    }

    @Test
    fun testSubscriptionsKeywords() {
        assertEquals("Subscriptions", engine.suggestCategory("Netflix monthly")?.name)
        assertEquals("Subscriptions", engine.suggestCategory("Spotify Premium")?.name)
        assertEquals("Subscriptions", engine.suggestCategory("Prime Video")?.name)
        assertEquals("Subscriptions", engine.suggestCategory("Hotstar VIP")?.name)
        assertEquals("Subscriptions", engine.suggestCategory("Apple Music")?.name)
    }

    @Test
    fun testBillsAndUtilitiesKeywords() {
        assertEquals("Bills & Utilities", engine.suggestCategory("State Electricity Board")?.name)
        assertEquals("Bills & Utilities", engine.suggestCategory("Airtel Broadband")?.name)
        assertEquals("Bills & Utilities", engine.suggestCategory("Mobile Recharge")?.name)
        assertEquals("Bills & Utilities", engine.suggestCategory("City Water Bill")?.name)
        assertEquals("Bills & Utilities", engine.suggestCategory("Verizon Wireless")?.name)
    }

    @Test
    fun testGroceriesKeywords() {
        assertEquals("Groceries", engine.suggestCategory("BigBasket Fresh")?.name)
        assertEquals("Groceries", engine.suggestCategory("Blinkit Groceries")?.name)
        assertEquals("Groceries", engine.suggestCategory("DMart Ready")?.name)
        assertEquals("Groceries", engine.suggestCategory("Trader Joe's")?.name)
        assertEquals("Groceries", engine.suggestCategory("Whole Foods Market")?.name)
    }

    @Test
    fun testEntertainmentKeywords() {
        assertEquals("Entertainment", engine.suggestCategory("AMC Theatres")?.name)
        assertEquals("Entertainment", engine.suggestCategory("PVR Cinemas")?.name)
        assertEquals("Entertainment", engine.suggestCategory("BookMyShow Movie")?.name)
        assertEquals("Entertainment", engine.suggestCategory("Ticketmaster Concert")?.name)
    }

    @Test
    fun testIncomeAndExpenseKeywords() {
        assertEquals("Salary", engine.suggestCategory("Monthly Salary Direct Deposit")?.name)
        assertEquals("Salary", engine.suggestCategory("Company Payroll")?.name)
        assertEquals("Other Income", engine.suggestCategory("Quarterly Dividend")?.name)
        assertEquals("Other Income", engine.suggestCategory("Bank Account Interest")?.name)
        assertEquals("Other Expense", engine.suggestCategory("ATM Withdrawal Fee")?.name)
        assertEquals("Other Expense", engine.suggestCategory("Late Penalty Charge")?.name)
    }

    @Test
    fun testCaseInsensitiveAndSubstringMatching() {
        assertEquals("Transport", engine.suggestCategory("PAYMENT TO UBER TRIP #482")?.name)
        assertEquals("Food & Dining", engine.suggestCategory("starbucks reserve")?.name)
        assertEquals("Subscriptions", engine.suggestCategory("NETFLIX.COM PAYMENT")?.name)
    }

    @Test
    fun testUnrecognizedMerchantReturnsNull() {
        assertNull(engine.suggestCategory("Unknown Corner Store 12345"))
        assertNull(engine.suggestCategory("Acme Widget Supply"))
        assertNull(engine.suggestCategory("Local Locksmith Service"))
    }

    @Test
    fun testEmptyOrBlankMerchantReturnsNull() {
        assertNull(engine.suggestCategory(""))
        assertNull(engine.suggestCategory("   "))
        assertNull(engine.suggestCategory("\t\n"))
    }

    @Test
    fun testCustomCategoryLookup() {
        val customTransport = Category(id = 99, name = "Transport", type = "EXPENSE")
        val customEngine = CategorizationRuleEngine(mapOf("Transport" to customTransport))

        val result = customEngine.suggestCategory("Uber Cab")
        assertNotNull(result)
        assertEquals(99, result?.id)
        assertEquals("Transport", result?.name)

        // Category not in custom map returns null even if keyword is known
        val foodResult = customEngine.suggestCategory("Starbucks")
        assertNull(foodResult)
    }
}
