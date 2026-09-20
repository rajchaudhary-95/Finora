package com.example.finora.util

import com.example.finora.data.db.dao.RecurringCandidate
import com.example.finora.data.db.entities.Transaction
import com.example.finora.repository.TransactionRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Routine for recurring-charge detection per Finora Implementation Plan §7.4.
 *
 * Evaluates transactions over a 3-month window, groups by (merchant, amount rounded to whole unit),
 * and flags transactions as recurring if matches appear across at least 2 distinct calendar months.
 */
object RecurringDetector {

    data class RecurringGroupKey(
        val merchant: String,
        val roundedAmount: Long
    )

    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    /**
     * Formats an epoch millisecond timestamp to a "yyyy-MM" calendar month string.
     */
    fun formatYearMonth(timestampMillis: Long): String {
        return monthFormat.format(Date(timestampMillis))
    }

    /**
     * Pure grouping logic for testing and in-memory evaluation:
     * Takes a list of transactions, groups by (merchant.lowercase().trim(), amount.roundToLong()),
     * counts distinct calendar months ("yyyy-MM") for each group,
     * and returns a new list of transactions with isRecurring flagged true or false.
     *
     * Rules:
     * - A group qualifies as recurring if and only if it has transactions in >= 2 distinct calendar months.
     * - One-off transactions (only 1 month) are flagged false.
     * - Same-merchant with different rounded amounts belong to different groups and do not match each other.
     * - Same-merchant same-amount in the same calendar month has distinct months = 1, so not recurring.
     */
    fun evaluateRecurringTransactions(transactions: List<Transaction>): List<Transaction> {
        val groupMonthMap = mutableMapOf<RecurringGroupKey, MutableSet<String>>()

        for (tx in transactions) {
            val key = RecurringGroupKey(
                merchant = tx.merchant.trim().lowercase(Locale.ROOT),
                roundedAmount = Math.round(tx.amount)
            )
            val month = formatYearMonth(tx.date)
            groupMonthMap.getOrPut(key) { mutableSetOf() }.add(month)
        }

        val qualifyingKeys = groupMonthMap.filterValues { it.size >= 2 }.keys

        return transactions.map { tx ->
            val key = RecurringGroupKey(
                merchant = tx.merchant.trim().lowercase(Locale.ROOT),
                roundedAmount = Math.round(tx.amount)
            )
            tx.copy(isRecurring = key in qualifyingKeys)
        }
    }

    /**
     * Identifies recurring group keys from a list of RecurringCandidates.
     */
    fun findRecurringGroups(candidates: List<RecurringCandidate>): Set<RecurringGroupKey> {
        return candidates
            .groupBy {
                RecurringGroupKey(
                    merchant = it.merchant.trim().lowercase(Locale.ROOT),
                    roundedAmount = Math.round(it.roundedAmount)
                )
            }
            .filter { (_, groupCandidates) ->
                val distinctMonths = groupCandidates.map { it.month }.toSet()
                distinctMonths.size >= 2
            }
            .keys
    }

    /**
     * Runs the recurring detection routine against the Room database.
     * Fetches candidates from the last 3 calendar months using TransactionDao.getRecurringCandidates(sinceEpoch),
     * evaluates qualifying recurring groups, resets outdated flags, and marks qualifying transactions.
     *
     * @param transactionRepository TransactionRepository instance
     * @param referenceDateMillis Base timestamp (defaults to current time)
     */
    suspend fun runDetection(
        transactionRepository: TransactionRepository,
        referenceDateMillis: Long = System.currentTimeMillis()
    ) {
        // Calculate beginning of the month 2 months prior (covering 3 calendar months total)
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceDateMillis
            add(Calendar.MONTH, -2)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sinceEpoch = cal.timeInMillis

        val candidates = transactionRepository.getRecurringCandidates(sinceEpoch)
        val recurringGroups = findRecurringGroups(candidates)

        // Reset all transactions currently marked recurring
        transactionRepository.resetAllRecurring()

        // Mark all transactions belonging to qualifying recurring groups
        for (group in recurringGroups) {
            transactionRepository.setRecurringFlag(
                merchant = group.merchant,
                amount = group.roundedAmount.toDouble(),
                flag = true
            )
        }
    }
}
