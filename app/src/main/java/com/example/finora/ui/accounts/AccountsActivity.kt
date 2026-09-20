package com.example.finora.ui.accounts

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.R
import com.example.finora.databinding.ActivityAccountsBinding
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class AccountsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountsBinding
    private lateinit var adapter: AccountAdapter

    private val viewModel: AccountsViewModel by viewModels {
        val app = application as FinoraApp
        AccountsViewModel.Factory(app.accountRepository, app.transactionRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeAccounts()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = AccountAdapter { account ->
            val intent = AddEditAccountActivity.createIntent(this, account.id)
            startActivity(intent)
        }
        binding.rvAccounts.layoutManager = LinearLayoutManager(this)
        binding.rvAccounts.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddAccount.setOnClickListener {
            val intent = AddEditAccountActivity.createIntent(this, AddEditAccountActivity.NEW_ACCOUNT_ID)
            startActivity(intent)
        }
    }

    private fun observeAccounts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.accounts.collect { accounts ->
                    adapter.submitList(accounts)

                    if (accounts.isEmpty()) {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.tvTotalBalance.text = "₹0.00"
                        binding.tvAccountCount.text = "0 Accounts Linked"
                    } else {
                        binding.layoutEmptyState.visibility = View.GONE

                        val total = accounts.sumOf { it.balance }
                        if (total < 0) {
                            binding.tvTotalBalance.setTextColor(ContextCompat.getColor(this@AccountsActivity, R.color.expense_red))
                            binding.tvTotalBalance.text = String.format(Locale.getDefault(), "-₹%,.2f", abs(total))
                        } else {
                            binding.tvTotalBalance.setTextColor(ContextCompat.getColor(this@AccountsActivity, R.color.card_surface))
                            binding.tvTotalBalance.text = String.format(Locale.getDefault(), "₹%,.2f", total)
                        }

                        val countText = if (accounts.size == 1) "1 Account Linked" else "${accounts.size} Accounts Linked"
                        binding.tvAccountCount.text = countText
                    }
                }
            }
        }
    }
}
