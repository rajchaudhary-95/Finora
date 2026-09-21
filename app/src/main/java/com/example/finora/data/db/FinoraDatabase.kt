package com.example.finora.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        PortfolioHolding::class,
        User::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FinoraDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: FinoraDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `passwordHash` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")
            }
        }

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
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(true)
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
