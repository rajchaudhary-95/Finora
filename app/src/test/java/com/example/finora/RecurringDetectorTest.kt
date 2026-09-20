package com.example.finora

import com.example.finora.data.db.dao.RecurringCandidate
import com.example.finora.data.db.entities.Transaction
import com.example.finora.util.RecurringDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringDetectorTest {

    private fun createDateMillis(year: Int, monthIndex0: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex0)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun testSynthetic3MonthDataset_recurringChargesFlagged() {
        // Month 1: July 2026 (index 6)
        // Month 2: August 2026 (index 7)
        // Month 3: September 2026 (index 8)
        val month1Date = createDateMillis(2026, 6, 15)
        val month2Date = createDateMillis(2026, 7, 15)
        val month3Date = createDateMillis(2026, 8, 15)

        val tx1 = Transaction(id = 1, accountId = 1, merchant = "Netflix", amount = 15.99, date = month1Date)
        val tx2 = Transaction(id = 2, accountId = 1, merchant = "Netflix", amount = 15.99, date = month2Date)
        val tx3 = Transaction(id = 3, accountId = 1, merchant = "Netflix", amount = 15.99, date = month3Date)

        // Gym membership appears in 2 of the 3 months (Month 1 & Month 3)
        val gym1 = Transaction(id = 4, accountId = 1, merchant = "City Fitness", amount = 50.0, date = month1Date)
        val gym2 = Transaction(id = 5, accountId = 1, merchant = "City Fitness", amount = 50.0, date = month3Date)

        val transactions = listOf(tx1, tx2, tx3, gym1, gym2)
        val evaluated = RecurringDetector.evaluateRecurringTransactions(transactions)

        assertTrue("Netflix Month 1 should be flagged recurring", evaluated.first { it.id == 1 }.isRecurring)
        assertTrue("Netflix Month 2 should be flagged recurring", evaluated.first { it.id == 2 }.isRecurring)
        assertTrue("Netflix Month 3 should be flagged recurring", evaluated.first { it.id == 3 }.isRecurring)
        assertTrue("Gym Month 1 should be flagged recurring (2 distinct months)", evaluated.first { it.id == 4 }.isRecurring)
        assertTrue("Gym Month 3 should be flagged recurring (2 distinct months)", evaluated.first { it.id == 5 }.isRecurring)
    }

    @Test
    fun testOneOffTransaction_notFlagged() {
        val month2Date = createDateMillis(2026, 7, 10)
        val oneOff = Transaction(id = 10, accountId = 1, merchant = "Best Buy", amount = 149.99, date = month2Date)

        val evaluated = RecurringDetector.evaluateRecurringTransactions(listOf(oneOff))
        assertFalse("One-off transaction must NOT be flagged as recurring", evaluated[0].isRecurring)
    }

    @Test
    fun testSameMerchantDifferentAmount_notFlagged() {
        val month1Date = createDateMillis(2026, 6, 5)
        val month2Date = createDateMillis(2026, 7, 12)

        // Uber with $25 in Month 1 and $65 in Month 2
        val uber1 = Transaction(id = 20, accountId = 1, merchant = "Uber", amount = 25.0, date = month1Date)
        val uber2 = Transaction(id = 21, accountId = 1, merchant = "Uber", amount = 65.0, date = month2Date)

        val evaluated = RecurringDetector.evaluateRecurringTransactions(listOf(uber1, uber2))
        assertFalse("Uber with different amounts must NOT be flagged recurring", evaluated.first { it.id == 20 }.isRecurring)
        assertFalse("Uber with different amounts must NOT be flagged recurring", evaluated.first { it.id == 21 }.isRecurring)
    }

    @Test
    fun testSameMerchantSameAmountSameMonth_notFlagged() {
        // Two transactions in July 2026 (Month index 6)
        val date1 = createDateMillis(2026, 6, 3)
        val date2 = createDateMillis(2026, 6, 25)

        val coffee1 = Transaction(id = 30, accountId = 1, merchant = "Blue Bottle", amount = 6.0, date = date1)
        val coffee2 = Transaction(id = 31, accountId = 1, merchant = "Blue Bottle", amount = 6.0, date = date2)

        val evaluated = RecurringDetector.evaluateRecurringTransactions(listOf(coffee1, coffee2))
        assertFalse("Transactions in only 1 calendar month must NOT be flagged", evaluated.first { it.id == 30 }.isRecurring)
        assertFalse("Transactions in only 1 calendar month must NOT be flagged", evaluated.first { it.id == 31 }.isRecurring)
    }

    @Test
    fun testRoundedAmountGrouping() {
        val month1Date = createDateMillis(2026, 6, 1)
        val month2Date = createDateMillis(2026, 7, 1)

        // $9.99 and $10.04 round to $10
        val sub1 = Transaction(id = 40, accountId = 1, merchant = "Spotify", amount = 9.99, date = month1Date)
        val sub2 = Transaction(id = 41, accountId = 1, merchant = "Spotify", amount = 10.04, date = month2Date)

        val evaluated = RecurringDetector.evaluateRecurringTransactions(listOf(sub1, sub2))
        assertTrue("Spotify rounded to whole unit in 2 distinct months should be flagged recurring", evaluated.first { it.id == 40 }.isRecurring)
        assertTrue("Spotify rounded to whole unit in 2 distinct months should be flagged recurring", evaluated.first { it.id == 41 }.isRecurring)
    }

    @Test
    fun testFindRecurringGroupsFromCandidates() {
        val candidates = listOf(
            RecurringCandidate(merchant = "Netflix", roundedAmount = 16.0, month = "2026-07", count = 1),
            RecurringCandidate(merchant = "Netflix", roundedAmount = 16.0, month = "2026-08", count = 1),
            RecurringCandidate(merchant = "OneOffStore", roundedAmount = 50.0, month = "2026-08", count = 1),
            RecurringCandidate(merchant = "ElectricBill", roundedAmount = 100.0, month = "2026-07", count = 1),
            RecurringCandidate(merchant = "ElectricBill", roundedAmount = 180.0, month = "2026-08", count = 1)
        )

        val groups = RecurringDetector.findRecurringGroups(candidates)

        assertEquals(1, groups.size)
        val key = groups.first()
        assertEquals("netflix", key.merchant)
        assertEquals(16L, key.roundedAmount)
    }
}
