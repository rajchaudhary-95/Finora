package com.example.finora.data.db.dao

import androidx.room.*
import com.example.finora.data.db.entities.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: Transaction): Long

    @Update
    suspend fun update(tx: Transaction)

    @Delete
    suspend fun delete(tx: Transaction)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Int): Transaction?

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getByAccount(accountId: Int): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun getCountByAccount(accountId: Int): Int

    // Spending by category for a given YYYY-MM month — drives budget progress bars and spending charts
    @Query("""
        SELECT categoryId, SUM(amount) as total 
        FROM transactions 
        WHERE strftime('%Y-%m', date/1000, 'unixepoch') = :month 
        GROUP BY categoryId
    """)
    fun getSpendingByCategory(month: String): Flow<List<CategorySpending>>

    // Recurring candidates: grouped by merchant, rounded amount, and month
    @Query("""
        SELECT merchant, ROUND(amount, 0) as roundedAmount, 
               strftime('%Y-%m', date/1000, 'unixepoch') as month, COUNT(*) as count
        FROM transactions 
        WHERE date >= :sinceEpoch
        GROUP BY merchant, roundedAmount, month
    """)
    suspend fun getRecurringCandidates(sinceEpoch: Long): List<RecurringCandidate>

    @Query("SELECT * FROM transactions WHERE isRecurring = 1 ORDER BY date DESC")
    fun getRecurring(): Flow<List<Transaction>>

    // Geotagged transactions
    @Query("SELECT * FROM transactions WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY date DESC")
    fun getGeotaggedTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY date DESC")
    suspend fun getGeotagged(): List<Transaction>

    @Query("UPDATE transactions SET isRecurring = :flag WHERE merchant = :merchant AND ROUND(amount, 0) = ROUND(:amount, 0)")
    suspend fun markRecurring(merchant: String, amount: Double, flag: Boolean)
}

// Helper data classes for queries
data class CategorySpending(val categoryId: Int?, val total: Double)

data class RecurringCandidate(
    val merchant: String,
    val roundedAmount: Double,
    val month: String,
    val count: Int
)
