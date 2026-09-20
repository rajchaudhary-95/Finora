package com.example.finora.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.finora.data.db.dao.*
import com.example.finora.data.db.entities.*

@Database(
    entities = [
        Account::class,
        Category::class,
        Transaction::class,
        Budget::class,
        WatchlistStock::class,
        PortfolioHolding::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FinoraDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        @Volatile
        private var INSTANCE: FinoraDatabase? = null

        fun getInstance(context: Context): FinoraDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, "finora_db").also { INSTANCE = it }
            }

        fun buildDatabase(context: Context, dbName: String = "finora_db"): FinoraDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FinoraDatabase::class.java,
                dbName
            )
            .addCallback(SeedCallback())
            .build()
    }

    /**
     * Seeds the 10 default categories specified in Implementation Plan §6.7 on first database creation.
     */
    class SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val defaults = listOf(
                "'Groceries','EXPENSE',0,1",
                "'Food & Dining','EXPENSE',0,1",
                "'Transport','EXPENSE',0,1",
                "'Shopping','EXPENSE',0,1",
                "'Subscriptions','EXPENSE',0,1",
                "'Bills & Utilities','EXPENSE',0,1",
                "'Entertainment','EXPENSE',0,1",
                "'Salary','INCOME',0,1",
                "'Other Income','INCOME',0,1",
                "'Other Expense','EXPENSE',0,1"
            )
            defaults.forEach { values ->
                db.execSQL("INSERT INTO categories (name, type, iconRes, isSystemDefault) VALUES ($values)")
            }
        }
    }
}

/**
 * Typealias for backwards compatibility.
 */
typealias AppDatabase = FinoraDatabase
