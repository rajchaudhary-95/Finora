package com.example.finora.ui.transactions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.Category
import com.example.finora.databinding.FragmentTransactionsBinding
import kotlinx.coroutines.launch

/**
 * Fragment displaying transactions list, category/account filter spinners, and Add Transaction FAB.
 */
class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransactionsViewModel by viewModels {
        val app = requireActivity().application as FinoraApp
        TransactionsViewModel.Factory(
            app.transactionRepository,
            app.accountRepository,
            app.categoryRepository
        )
    }

    private lateinit var transactionAdapter: TransactionAdapter
    private var accountFilterIds = listOf<Int?>()
    private var categoryFilterIds = listOf<Int?>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter { item ->
            val intent = Intent(requireContext(), TransactionDetailActivity::class.java).apply {
                putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, item.transaction.id)
            }
            startActivity(intent)
        }

        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun setupFab() {
        binding.fabAddTransaction.setOnClickListener {
            val intent = Intent(requireContext(), AddEditTransactionActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect filtered transactions list
                launch {
                    viewModel.filteredTransactions.collect { list ->
                        transactionAdapter.submitList(list)
                        binding.layoutEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                // Populate account filter spinner
                launch {
                    viewModel.accounts.collect { accounts ->
                        setupAccountSpinner(accounts)
                    }
                }

                // Populate category filter spinner
                launch {
                    viewModel.categories.collect { categories ->
                        setupCategorySpinner(categories)
                    }
                }
            }
        }
    }

    private fun setupAccountSpinner(accounts: List<Account>) {
        accountFilterIds = listOf<Int?>(null) + accounts.map { it.id }
        val accountNames = listOf("All Accounts") + accounts.map { it.name }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, accountNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFilterAccount.adapter = adapter

        binding.spinnerFilterAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in accountFilterIds.indices) {
                    viewModel.setAccountFilter(accountFilterIds[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCategorySpinner(categories: List<Category>) {
        categoryFilterIds = listOf<Int?>(null) + categories.map { it.id }
        val categoryNames = listOf("All Categories") + categories.map { it.name }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFilterCategory.adapter = adapter

        binding.spinnerFilterCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in categoryFilterIds.indices) {
                    viewModel.setCategoryFilter(categoryFilterIds[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
