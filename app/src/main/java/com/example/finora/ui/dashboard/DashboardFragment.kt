package com.example.finora.ui.dashboard

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
import com.example.finora.databinding.FragmentDashboardBinding
import com.example.finora.ui.accounts.AccountsActivity
import com.example.finora.ui.nearby.NearbySpendingActivity
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        val app = requireActivity().application as FinoraApp
        DashboardViewModel.Factory(
            app.netWorthRepository,
            app.transactionRepository
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

        recurringAdapter = RecurringTransactionAdapter()
        binding.rvRecurringTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recurringAdapter
        }

        binding.btnManageAccounts.setOnClickListener {
            startActivity(Intent(requireContext(), AccountsActivity::class.java))
        }

        binding.btnNearbySpending.setOnClickListener {
            startActivity(Intent(requireContext(), NearbySpendingActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
