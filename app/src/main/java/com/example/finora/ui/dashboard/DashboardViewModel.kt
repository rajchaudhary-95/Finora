package com.example.finora.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.dao.CategorySpending
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.repository.CategoryRepository
import com.example.finora.repository.NetWorthBreakdown
import com.example.finora.repository.NetWorthRepository
import com.example.finora.repository.TransactionRepository
import com.example.finora.util.RecurringDetector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Dashboard tab.
 * Manually constructor-injected with NetWorthRepository, TransactionRepository, and CategoryRepository.
 */
class DashboardViewModel(
    private val netWorthRepository: NetWorthRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    init {
        runRecurringDetection()
    }

    private val currentMonth: String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    // Complete net worth formula (cash + investments)
    val netWorthBreakdown: StateFlow<NetWorthBreakdown> = netWorthRepository.getNetWorthBreakdown()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetWorthBreakdown(0.0, 0.0, 0.0)
        )

    val netWorth: StateFlow<Double> = netWorthRepository.getTotalNetWorth()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // Current month spending by category (shared query with Budgets)
    val categorySpending: StateFlow<List<CategorySpending>> =
        transactionRepository.getSpendingByCategory(currentMonth)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val allCategories: StateFlow<List<Category>> =
        categoryRepository.allCategories
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Recurring charges surfaced for quick review
    val recurringTransactions: StateFlow<List<Transaction>> = transactionRepository.getRecurring()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun runRecurringDetection() {
        viewModelScope.launch {
            try {
                RecurringDetector.runDetection(transactionRepository)
            } catch (_: Exception) {
            }
        }
    }

    fun refreshDashboard() {
        runRecurringDetection()
    }

    class Factory(
        private val netWorthRepository: NetWorthRepository,
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(netWorthRepository, transactionRepository, categoryRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
