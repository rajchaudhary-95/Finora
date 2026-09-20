package com.example.finora.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.entities.Budget
import com.example.finora.data.db.entities.Category
import com.example.finora.repository.BudgetRepository
import com.example.finora.repository.CategoryRepository
import com.example.finora.repository.TransactionRepository
import com.example.finora.util.BudgetCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BudgetSummary(
    val totalBudgeted: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalRemaining: Double = 0.0
)

/**
 * ViewModel backing both BudgetFragment and BudgetSetupActivity.
 * Computes live progress, effective limits with rollover, and category availability.
 */
class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    )
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgetUiModels: StateFlow<List<BudgetUiModel>> = _selectedMonth.flatMapLatest { month ->
        val prevMonth = BudgetCalculator.getPreviousMonth(month)
        combine(
            budgetRepository.getForMonth(month),
            transactionRepository.getSpendingByCategory(month),
            categoryRepository.allCategories,
            budgetRepository.getForMonth(prevMonth),
            transactionRepository.getSpendingByCategory(prevMonth)
        ) { currentBudgets, currentSpending, categories, prevBudgets, prevSpending ->
            val currentSpendingMap = currentSpending.associate { (it.categoryId ?: -1) to it.total }
            val prevSpendingMap = prevSpending.associate { (it.categoryId ?: -1) to it.total }
            val prevBudgetMap = prevBudgets.associateBy { it.categoryId }
            val categoryMap = categories.associateBy { it.id }

            currentBudgets.map { budget ->
                val category = categoryMap[budget.categoryId]
                val categoryName = category?.name ?: "Unknown"
                val categoryIconRes = category?.iconRes ?: 0
                val currentSpent = currentSpendingMap[budget.categoryId] ?: 0.0

                val prevBudget = prevBudgetMap[budget.categoryId]
                val prevSpent = prevSpendingMap[budget.categoryId] ?: 0.0
                val rolloverAmount = BudgetCalculator.calculateRollover(
                    rolloverEnabled = budget.rolloverEnabled,
                    previousMonthLimit = prevBudget?.monthlyLimit,
                    previousMonthSpent = prevSpent
                )

                val effectiveLimit = BudgetCalculator.computeEffectiveLimit(budget.monthlyLimit, rolloverAmount)
                val remaining = BudgetCalculator.computeRemaining(effectiveLimit, currentSpent)
                val progressPercent = BudgetCalculator.computeProgressPercent(currentSpent, effectiveLimit)
                val status = BudgetCalculator.determineStatus(currentSpent, effectiveLimit)

                BudgetUiModel(
                    budget = budget,
                    category = category,
                    categoryName = categoryName,
                    categoryIconRes = categoryIconRes,
                    monthlyLimit = budget.monthlyLimit,
                    rolloverAmount = rolloverAmount,
                    effectiveLimit = effectiveLimit,
                    spentSoFar = currentSpent,
                    remaining = remaining,
                    progressPercent = progressPercent,
                    status = status
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val availableCategoriesForNewBudget: StateFlow<List<Category>> = _selectedMonth.flatMapLatest { month ->
        combine(
            categoryRepository.allCategories,
            budgetRepository.getForMonth(month)
        ) { categories, budgets ->
            BudgetCalculator.filterAvailableCategories(categories, budgets)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val budgetSummary: StateFlow<BudgetSummary> = budgetUiModels.map { models ->
        val totalBudgeted = models.sumOf { it.effectiveLimit }
        val totalSpent = models.sumOf { it.spentSoFar }
        BudgetSummary(
            totalBudgeted = totalBudgeted,
            totalSpent = totalSpent,
            totalRemaining = totalBudgeted - totalSpent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetSummary()
    )

    fun setSelectedMonth(month: String) {
        _selectedMonth.value = month
    }

    fun createBudget(categoryId: Int, limit: Double, rolloverEnabled: Boolean) {
        viewModelScope.launch {
            val budget = Budget(
                categoryId = categoryId,
                monthlyLimit = limit,
                rolloverEnabled = rolloverEnabled,
                month = _selectedMonth.value
            )
            budgetRepository.insert(budget)
        }
    }

    fun updateBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.update(budget)
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.delete(budget)
        }
    }

    class Factory(
        private val budgetRepository: BudgetRepository,
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
                return BudgetViewModel(budgetRepository, transactionRepository, categoryRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
