package com.example.finora.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.repository.AccountRepository
import com.example.finora.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Accounts list and account creation/editing.
 * Manually constructor-injected with AccountRepository and optional TransactionRepository.
 */
class AccountsViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository? = null
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = accountRepository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getAccountById(id: Int): Account? {
        return accountRepository.getById(id)
    }

    suspend fun getTransactionCount(accountId: Int): Int {
        return transactionRepository?.getCountByAccount(accountId) ?: 0
    }

    fun saveAccount(
        name: String,
        type: AccountType,
        balance: Double,
        id: Int = 0,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (name.isBlank()) {
            onError?.invoke("Account name cannot be empty")
            return
        }

        viewModelScope.launch {
            try {
                if (id <= 0) {
                    val newAccount = Account(
                        name = name.trim(),
                        type = type.storageValue,
                        balance = balance
                    )
                    accountRepository.insert(newAccount)
                } else {
                    val existing = accountRepository.getById(id)
                    val updated = (existing ?: Account(name = name.trim(), type = type.storageValue, balance = balance)).copy(
                        id = id,
                        name = name.trim(),
                        type = type.storageValue,
                        balance = balance
                    )
                    accountRepository.update(updated)
                }
                onSuccess?.invoke()
            } catch (e: Exception) {
                onError?.invoke(e.localizedMessage ?: "Failed to save account")
            }
        }
    }

    fun deleteAccount(
        account: Account,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val txCount = transactionRepository?.getCountByAccount(account.id) ?: 0
                if (txCount > 0) {
                    onError?.invoke("Cannot delete account with $txCount associated transactions.")
                    return@launch
                }
                accountRepository.delete(account)
                onSuccess?.invoke()
            } catch (e: Exception) {
                onError?.invoke(e.localizedMessage ?: "Failed to delete account")
            }
        }
    }

    class Factory(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AccountsViewModel::class.java)) {
                return AccountsViewModel(accountRepository, transactionRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
