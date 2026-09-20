package com.example.finora.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.R
import com.example.finora.databinding.FragmentDashboardBinding
import com.example.finora.databinding.ItemBudgetGlanceBinding
import com.example.finora.ui.accounts.AccountsActivity
import com.example.finora.ui.budget.BudgetSetupActivity
import com.example.finora.ui.budget.BudgetUiModel
import com.example.finora.ui.budget.BudgetViewModel
import com.example.finora.util.BudgetStatus
import com.example.finora.ui.nearby.NearbySpendingActivity
import com.example.finora.ui.watchlist.WatchlistActivity
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        val app = requireActivity().application as FinoraApp
        DashboardViewModel.Factory(
            app.netWorthRepository,
            app.transactionRepository,
            app.categoryRepository
        )
    }

    // Reusing BudgetViewModel from Activity scope per requirements
    private val budgetViewModel: BudgetViewModel by activityViewModels {
        val app = requireActivity().application as FinoraApp
        BudgetViewModel.Factory(
            app.budgetRepository,
            app.transactionRepository,
            app.categoryRepository
        )
    }

    private lateinit var recurringAdapter: RecurringTransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNavigationActions()
        setupRecurringRecyclerView()
        setupPieChartAppearance()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDashboard()
    }

    private fun setupNavigationActions() {
        binding.btnNavAccounts.setOnClickListener {
            startActivity(Intent(requireContext(), AccountsActivity::class.java))
        }

        binding.btnNavWatchlist.setOnClickListener {
            startActivity(Intent(requireContext(), WatchlistActivity::class.java))
        }

        binding.btnNavNearby.setOnClickListener {
            startActivity(Intent(requireContext(), NearbySpendingActivity::class.java))
        }

        binding.btnManageBudgetsGlance.setOnClickListener {
            startActivity(Intent(requireContext(), BudgetSetupActivity::class.java))
        }
    }

    private fun setupRecurringRecyclerView() {
        recurringAdapter = RecurringTransactionAdapter()
        binding.rvRecurringTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recurringAdapter
        }
    }

    private fun setupPieChartAppearance() {
        val chart = binding.pieChartSpending
        chart.description.isEnabled = false
        chart.setUsePercentValues(true)
        chart.isDrawHoleEnabled = true
        chart.setHoleColor(Color.TRANSPARENT)
        chart.holeRadius = 55f
        chart.transparentCircleRadius = 60f
        chart.setDrawEntryLabels(false)
        chart.centerText = "Expenses"
        chart.setCenterTextSize(14f)
        chart.setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))

        val legend = chart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.isWordWrapEnabled = true
        legend.textSize = 11f
        legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        binding.tvSpendingSubtitle.text = monthName
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Net Worth Breakdown
                launch {
                    viewModel.netWorthBreakdown.collect { breakdown ->
                        if (breakdown.totalNetWorth >= 0.0) {
                            binding.tvNetWorthAmount.text = String.format(Locale.US, "$%,.2f", breakdown.totalNetWorth)
                        } else {
                            binding.tvNetWorthAmount.text = String.format(Locale.US, "-$%,.2f", Math.abs(breakdown.totalNetWorth))
                        }
                        binding.tvNetWorthBreakdown.text = String.format(
                            Locale.US,
                            "Cash: $%,.2f • Investments: $%,.2f",
                            breakdown.cashBalance,
                            breakdown.investmentValue
                        )
                    }
                }

                // Category Spending Pie Chart
                launch {
                    combine(viewModel.categorySpending, viewModel.allCategories) { spendingList, categories ->
                        Pair(spendingList, categories)
                    }.collect { (spendingList, categories) ->
                        updateSpendingChart(spendingList, categories)
                    }
                }

                // Budgets at a Glance
                launch {
                    budgetViewModel.budgetUiModels.collect { budgetModels ->
                        updateBudgetsGlance(budgetModels)
                    }
                }

                // Recurring Transactions
                launch {
                    viewModel.recurringTransactions.collect { recurringList ->
                        recurringAdapter.submitList(recurringList)
                        if (recurringList.isEmpty()) {
                            binding.rvRecurringTransactions.visibility = View.GONE
                            binding.tvRecurringEmpty.visibility = View.VISIBLE
                            binding.tvRecurringCount.visibility = View.GONE
                        } else {
                            binding.rvRecurringTransactions.visibility = View.VISIBLE
                            binding.tvRecurringEmpty.visibility = View.GONE
                            binding.tvRecurringCount.visibility = View.VISIBLE
                            binding.tvRecurringCount.text = "${recurringList.size} active"
                        }
                    }
                }
            }
        }
    }

    private fun updateSpendingChart(
        spendingList: List<com.example.finora.data.db.dao.CategorySpending>,
        categories: List<com.example.finora.data.db.entities.Category>
    ) {
        val totalSpent = spendingList.sumOf { it.total }
        binding.tvTotalMonthSpent.text = String.format(Locale.US, "$%,.2f", totalSpent)

        if (spendingList.isEmpty() || totalSpent <= 0.0) {
            binding.pieChartSpending.visibility = View.GONE
            binding.tvSpendingEmpty.visibility = View.VISIBLE
            return
        }

        binding.pieChartSpending.visibility = View.VISIBLE
        binding.tvSpendingEmpty.visibility = View.GONE

        val categoryMap = categories.associateBy { it.id }
        val entries = mutableListOf<PieEntry>()

        spendingList.forEach { item ->
            val catName = categoryMap[item.categoryId]?.name ?: "Category ${item.categoryId}"
            entries.add(PieEntry(item.total.toFloat(), catName))
        }

        val sliceColors = listOf(
            ContextCompat.getColor(requireContext(), R.color.primary_teal),
            ContextCompat.getColor(requireContext(), R.color.mint_accent),
            ContextCompat.getColor(requireContext(), R.color.warning_amber),
            Color.parseColor("#4A90E2"), // Blue
            Color.parseColor("#9B51E0"), // Purple
            Color.parseColor("#E91E63"), // Pink
            Color.parseColor("#FF6F00"), // Deep Orange
            Color.parseColor("#00897B")  // Teal Accent
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            sliceSpace = 2.5f
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            valueFormatter = PercentFormatter(binding.pieChartSpending)
        }

        binding.pieChartSpending.data = PieData(dataSet)
        binding.pieChartSpending.animateY(600, Easing.EaseInOutQuad)
        binding.pieChartSpending.invalidate()
    }

    private fun updateBudgetsGlance(models: List<BudgetUiModel>) {
        binding.layoutBudgetsGlanceList.removeAllViews()

        if (models.isEmpty()) {
            binding.layoutBudgetsGlanceList.visibility = View.GONE
            binding.tvBudgetsGlanceEmpty.visibility = View.VISIBLE
            return
        }

        binding.layoutBudgetsGlanceList.visibility = View.VISIBLE
        binding.tvBudgetsGlanceEmpty.visibility = View.GONE

        // Prioritize budgets over 90% used, or show top active budgets
        val highRisk = models.filter { it.progressPercent >= 90 }
        val displayList = if (highRisk.isNotEmpty()) {
            highRisk
        } else {
            models.sortedByDescending { it.progressPercent }.take(3)
        }

        val inflater = LayoutInflater.from(requireContext())
        for (item in displayList) {
            val itemBinding = ItemBudgetGlanceBinding.inflate(
                inflater,
                binding.layoutBudgetsGlanceList,
                false
            )

            itemBinding.tvGlanceCategoryName.text = item.category?.name ?: "Category ${item.budget.categoryId}"
            itemBinding.tvGlanceSpentLimit.text = String.format(
                Locale.US,
                "$%,.2f of $%,.2f",
                item.spentSoFar,
                item.effectiveLimit
            )
            itemBinding.pbGlanceProgress.progress = item.progressPercent

            val (tintColor, badgeBg) = when (item.status) {
                BudgetStatus.OK -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.income_green),
                    R.drawable.bg_income_badge
                )
                BudgetStatus.AT_RISK -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.warning_amber),
                    R.drawable.bg_auto_badge
                )
                BudgetStatus.OVER -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.expense_red),
                    R.drawable.bg_expense_badge
                )
            }

            itemBinding.pbGlanceProgress.progressTintList =
                android.content.res.ColorStateList.valueOf(tintColor)

            itemBinding.tvGlancePercentBadge.text = "${item.progressPercent}% used"
            itemBinding.tvGlancePercentBadge.setTextColor(tintColor)
            itemBinding.tvGlancePercentBadge.setBackgroundResource(badgeBg)

            binding.layoutBudgetsGlanceList.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
