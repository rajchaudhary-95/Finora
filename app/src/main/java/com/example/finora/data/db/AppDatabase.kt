package com.example.finora.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.finora.data.db.dao.*
import com.example.finora.data.db.entities.*

@Database(
    entities = [Account::class, Category::class, Transaction::class,
                Budget::class, WatchlistStock::class, PortfolioHolding::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finora_db"
                )
                .addCallback(SeedCallback())    // seeds default categories on first run
                .build().also { INSTANCE = it }
            }
    }

    // Seeds 10 default categories the first time the DB is created
    private class SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // These run on the DB thread — use a simple insert statement
            val defaults = listOf(
                "'Groceries','EXPENSE',1", "'Food & Dining','EXPENSE',1",
                "'Transport','EXPENSE',1", "'Subscriptions','EXPENSE',1",
                "'Shopping','EXPENSE',1", "'Utilities','EXPENSE',1",
                "'Healthcare','EXPENSE',1", "'Entertainment','EXPENSE',1",
                "'Salary','INCOME',1", "'Freelance','INCOME',1"
            )
            defaults.forEach {
                db.execSQL("INSERT INTO categories (name,type,isSystemDefault) VALUES ($it)")
            }
        }
    }
}
