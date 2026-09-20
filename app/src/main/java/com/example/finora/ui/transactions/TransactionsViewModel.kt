package com.example.finora.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.repository.AccountRepository
import com.example.finora.repository.CategoryRepository
import com.example.finora.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Transactions tab, Add/Edit transaction form, and Transaction details.
 * Manages reactive transaction filtering and atomic balance adjustments on create/edit/delete.
 */
class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = transactionRepository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<Account>> = accountRepository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<Category>> = categoryRepository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filterAccountId = MutableStateFlow<Int?>(null)
    val filterCategoryId = MutableStateFlow<Int?>(null)

    val filteredTransactions: StateFlow<List<TransactionUiModel>> = combine(
        transactionRepository.allTransactions,
        accountRepository.allAccounts,
        categoryRepository.allCategories,
        filterAccountId,
        filterCategoryId
    ) { txList, accList, catList, accountFilter, categoryFilter ->
        val accountMap = accList.associateBy { it.id }
        val categoryMap = catList.associateBy { it.id }

        txList.filter { tx ->
            (accountFilter == null || tx.accountId == accountFilter) &&
            (categoryFilter == null || tx.categoryId == categoryFilter)
        }.map { tx ->
            val account = accountMap[tx.accountId]
            val category = tx.categoryId?.let { categoryMap[it] }
            TransactionUiModel(
                transaction = tx,
                accountName = account?.name ?: "Unknown Account",
                categoryName = category?.name ?: "Uncategorized",
                categoryType = category?.type ?: "EXPENSE",
                categoryIconRes = CategoryIconHelper.getIconForCategory(category)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setAccountFilter(accountId: Int?) {
        filterAccountId.value = accountId
    }

    fun setCategoryFilter(categoryId: Int?) {
        filterCategoryId.value = categoryId
    }

    suspend fun getTransactionById(id: Int): Transaction? = transactionRepository.getById(id)

    suspend fun getAccountById(id: Int): Account? = accountRepository.getById(id)

    suspend fun getCategoryById(id: Int): Category? = categoryRepository.getById(id)

    /**
     * Creates a new transaction and adjusts the associated account balance.
     * INCOME: increases balance (+amount).
     * EXPENSE: decreases balance (-amount).
     */
    suspend fun createTransaction(transaction: Transaction): Long {
        val category = transaction.categoryId?.let { categoryRepository.getById(it) }
        val isIncome = category?.type?.equals("INCOME", ignoreCase = true) == true
        val delta = if (isIncome) transaction.amount else -transaction.amount

        val insertedId = transactionRepository.insert(transaction)
        accountRepository.adjustBalance(transaction.accountId, delta)
        return insertedId
    }

    /**
     * Updates an existing transaction and adjusts the account balance by the net delta.
     * Prevents double counting by calculating the difference between new and old effects.
     */
    suspend fun updateTransaction(newTransaction: Transaction, oldTransaction: Transaction) {
        val oldCategory = oldTransaction.categoryId?.let { categoryRepository.getById(it) }
        val oldIsIncome = oldCategory?.type?.equals("INCOME", ignoreCase = true) == true
        val oldEffect = if (oldIsIncome) oldTransaction.amount else -oldTransaction.amount
        val oldReversal = -oldEffect

        val newCategory = newTransaction.categoryId?.let { categoryRepository.getById(it) }
        val newIsIncome = newCategory?.type?.equals("INCOME", ignoreCase = true) == true
        val newEffect = if (newIsIncome) newTransaction.amount else -newTransaction.amount

        if (newTransaction.accountId == oldTransaction.accountId) {
            val netDelta = oldReversal + newEffect
            accountRepository.adjustBalance(newTransaction.accountId, netDelta)
        } else {
            accountRepository.adjustBalance(oldTransaction.accountId, oldReversal)
            accountRepository.adjustBalance(newTransaction.accountId, newEffect)
        }

        transactionRepository.update(newTransaction)
    }

    /**
     * Deletes a transaction and reverses its effect on the associated account balance.
     */
    suspend fun deleteTransaction(transaction: Transaction) {
        val category = transaction.categoryId?.let { categoryRepository.getById(it) }
        val isIncome = category?.type?.equals("INCOME", ignoreCase = true) == true
        val originalEffect = if (isIncome) transaction.amount else -transaction.amount
        val reversalDelta = -originalEffect

        accountRepository.adjustBalance(transaction.accountId, reversalDelta)
        transactionRepository.delete(transaction)
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TransactionsViewModel::class.java)) {
                return TransactionsViewModel(transactionRepository, accountRepository, categoryRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
