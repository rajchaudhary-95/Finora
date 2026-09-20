package com.example.finora.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.entities.Transaction
import com.example.finora.repository.NetWorthRepository
import com.example.finora.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Dashboard tab.
 * Manually constructor-injected with NetWorthRepository and TransactionRepository.
 */
class DashboardViewModel(
    private val netWorthRepository: NetWorthRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    // Derived cash net worth (investment component added in Phase 11)
    val netWorth: StateFlow<Double> = netWorthRepository.getCashNetWorth()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // Recurring charges surfaced for quick review
    val recurringTransactions: StateFlow<List<Transaction>> = transactionRepository.getRecurring()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refreshDashboard() {
        // TODO: Wire full dashboard refresh in Phase 6
    }

    class Factory(
        private val netWorthRepository: NetWorthRepository,
        private val transactionRepository: TransactionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(netWorthRepository, transactionRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
