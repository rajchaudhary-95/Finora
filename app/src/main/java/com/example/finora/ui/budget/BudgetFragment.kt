package com.example.finora.ui.budget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.databinding.FragmentBudgetBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fragment representing the Budget tab in bottom navigation.
 * Displays summary overview card, category budget progress bars, and navigation to BudgetSetupActivity.
 */
class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetViewModel by viewModels {
        val app = requireActivity().application as FinoraApp
        BudgetViewModel.Factory(
            app.budgetRepository,
            app.transactionRepository,
            app.categoryRepository
        )
    }

    private lateinit var adapter: BudgetAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupRecyclerView()
        setupActions()
        observeData()
    }

    private fun setupHeader() {
        val currentMonthFormatted = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        binding.tvHeaderMonth.text = currentMonthFormatted
    }

    private fun setupRecyclerView() {
        adapter = BudgetAdapter {
            openBudgetSetup()
        }
        binding.rvCategoryBudgets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategoryBudgets.adapter = adapter
    }

    private fun setupActions() {
        binding.btnManageBudgets.setOnClickListener {
            openBudgetSetup()
        }
        binding.btnCreateFirstBudget.setOnClickListener {
            openBudgetSetup()
        }
    }

    private fun openBudgetSetup() {
        val intent = Intent(requireContext(), BudgetSetupActivity::class.java)
        startActivity(intent)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.budgetUiModels.collect { models ->
                        adapter.submitList(models)
                        if (models.isEmpty()) {
                            binding.rvCategoryBudgets.visibility = View.GONE
                            binding.tvCategoryBudgetsHeader.visibility = View.GONE
                            binding.cardBudgetSummary.visibility = View.GONE
                            binding.layoutEmptyBudgets.visibility = View.VISIBLE
                        } else {
                            binding.rvCategoryBudgets.visibility = View.VISIBLE
                            binding.tvCategoryBudgetsHeader.visibility = View.VISIBLE
                            binding.cardBudgetSummary.visibility = View.VISIBLE
                            binding.layoutEmptyBudgets.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.budgetSummary.collect { summary ->
                        binding.tvSummaryBudgeted.text = String.format(Locale.US, "$%,.2f", summary.totalBudgeted)
                        binding.tvSummarySpent.text = String.format(Locale.US, "$%,.2f", summary.totalSpent)
                        if (summary.totalRemaining >= 0.0) {
                            binding.tvSummaryRemaining.text = String.format(Locale.US, "$%,.2f", summary.totalRemaining)
                        } else {
                            binding.tvSummaryRemaining.text = String.format(Locale.US, "-$%,.2f", Math.abs(summary.totalRemaining))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
