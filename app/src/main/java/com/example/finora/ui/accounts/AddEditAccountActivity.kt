package com.example.finora.ui.accounts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.finora.FinoraApp
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.databinding.ActivityAddEditAccountBinding
import kotlinx.coroutines.launch

class AddEditAccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "com.example.finora.EXTRA_ACCOUNT_ID"
        const val NEW_ACCOUNT_ID = -1

        /**
         * Explicit Intent factory for navigating to AddEditAccountActivity.
         * Pass [accountId] to edit an existing account, or omit/pass [NEW_ACCOUNT_ID] for creating one.
         */
        fun createIntent(context: Context, accountId: Int = NEW_ACCOUNT_ID): Intent {
            return Intent(context, AddEditAccountActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_ID, accountId)
            }
        }
    }

    private lateinit var binding: ActivityAddEditAccountBinding
    private var currentAccountId: Int = NEW_ACCOUNT_ID
    private var loadedAccount: Account? = null

    private val viewModel: AccountsViewModel by viewModels {
        val app = application as FinoraApp
        AccountsViewModel.Factory(app.accountRepository, app.transactionRepository)
    }

    private val accountTypes = listOf(
        AccountType.BANK to "Bank",
        AccountType.CASH to "Cash",
        AccountType.CREDIT_CARD to "Credit Card"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentAccountId = intent.getIntExtra(EXTRA_ACCOUNT_ID, NEW_ACCOUNT_ID)

        setupToolbar()
        setupSpinner()
        setupListeners()

        if (currentAccountId != NEW_ACCOUNT_ID) {
            setupEditMode()
        } else {
            setupAddMode()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSpinner() {
        val typeNames = accountTypes.map { it.second }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeNames)
        binding.spinnerAccountType.adapter = spinnerAdapter
    }

    private fun setupAddMode() {
        binding.toolbar.title = "Add Account"
        binding.btnSaveAccount.text = "Create Account"
        binding.btnDeleteAccount.visibility = View.GONE
        binding.etAccountBalance.setText("0.0")
    }

    private fun setupEditMode() {
        binding.toolbar.title = "Edit Account"
        binding.btnSaveAccount.text = "Save Changes"
        binding.btnDeleteAccount.visibility = View.VISIBLE

        lifecycleScope.launch {
            val account = viewModel.getAccountById(currentAccountId)
            if (account != null) {
                loadedAccount = account
                binding.etAccountName.setText(account.name)
                val typeIndex = accountTypes.indexOfFirst { it.first == account.accountType }
                if (typeIndex >= 0) {
                    binding.spinnerAccountType.setSelection(typeIndex)
                }
                binding.etAccountBalance.setText(account.balance.toString())
            } else {
                Toast.makeText(this@AddEditAccountActivity, "Account not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupListeners() {
        binding.btnSaveAccount.setOnClickListener {
            saveAccount()
        }

        binding.btnDeleteAccount.setOnClickListener {
            confirmDeleteAccount()
        }
    }

    private fun saveAccount() {
        val name = binding.etAccountName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            binding.tilAccountName.error = "Account name is required"
            return
        }
        binding.tilAccountName.error = null

        val balanceStr = binding.etAccountBalance.text?.toString()?.trim().orEmpty()
        val balance = balanceStr.toDoubleOrNull()
        if (balance == null) {
            binding.tilAccountBalance.error = "Enter a valid numeric balance"
            return
        }
        binding.tilAccountBalance.error = null

        val selectedType = accountTypes[binding.spinnerAccountType.selectedItemPosition].first

        viewModel.saveAccount(
            name = name,
            type = selectedType,
            balance = balance,
            id = if (currentAccountId != NEW_ACCOUNT_ID) currentAccountId else 0,
            onSuccess = {
                Toast.makeText(this, "Account saved", Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun confirmDeleteAccount() {
        lifecycleScope.launch {
            val account = viewModel.getAccountById(currentAccountId)
            if (account == null) {
                Toast.makeText(this@AddEditAccountActivity, "Account not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val txCount = viewModel.getTransactionCount(currentAccountId)
            if (txCount > 0) {
                AlertDialog.Builder(this@AddEditAccountActivity)
                    .setTitle("Cannot Delete Account")
                    .setMessage("This account has $txCount associated transaction(s). Under data integrity rules (RESTRICT), an account cannot be deleted while transactions are linked to it. Please reassign or delete them first.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                AlertDialog.Builder(this@AddEditAccountActivity)
                    .setTitle("Delete Account")
                    .setMessage("Are you sure you want to delete this account? This action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteAccount(
                            account = account,
                            onSuccess = {
                                Toast.makeText(this@AddEditAccountActivity, "Account deleted", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onError = { errorMsg ->
                                Toast.makeText(this@AddEditAccountActivity, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
