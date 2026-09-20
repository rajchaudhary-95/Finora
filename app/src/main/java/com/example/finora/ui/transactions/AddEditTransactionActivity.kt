package com.example.finora.ui.transactions

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.finora.FinoraApp
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.databinding.ActivityAddEditTransactionBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for adding or editing a transaction.
 * Supports date picker, account selection, category selection, and explicit Intent ID passing.
 */
class AddEditTransactionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
    }

    private lateinit var binding: ActivityAddEditTransactionBinding

    private val viewModel: TransactionsViewModel by viewModels {
        val app = application as FinoraApp
        TransactionsViewModel.Factory(
            app.transactionRepository,
            app.accountRepository,
            app.categoryRepository
        )
    }

    private var currentTransactionId: Int = -1
    private var existingTransaction: Transaction? = null

    private var accountsList: List<Account> = emptyList()
    private var categoriesList: List<Category> = emptyList()

    private var selectedDateMillis: Long = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentTransactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        val isEditMode = currentTransactionId != -1

        setupToolbar(isEditMode)
        setupDatePicker()
        setupSaveButton(isEditMode)
        observeFormDependencies(isEditMode)
    }

    private fun setupToolbar(isEditMode: Boolean) {
        binding.toolbar.title = if (isEditMode) "Edit Transaction" else "Add Transaction"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDatePicker() {
        updateDateDisplay(selectedDateMillis)

        val openDatePicker = {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    selectedDateMillis = selectedCal.timeInMillis
                    updateDateDisplay(selectedDateMillis)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.etDate.setOnClickListener { openDatePicker() }
        binding.tilDate.setEndIconOnClickListener { openDatePicker() }
    }

    private fun updateDateDisplay(millis: Long) {
        binding.etDate.setText(dateFormat.format(Date(millis)))
    }

    private fun observeFormDependencies(isEditMode: Boolean) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect accounts
                launch {
                    viewModel.accounts.collect { accounts ->
                        accountsList = accounts
                        val accountNames = accounts.map { "${it.name} ($${String.format(Locale.US, "%.2f", it.balance)})" }
                        val adapter = ArrayAdapter(this@AddEditTransactionActivity, android.R.layout.simple_spinner_item, accountNames).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        binding.spinnerAccount.adapter = adapter

                        existingTransaction?.let { tx ->
                            val index = accountsList.indexOfFirst { it.id == tx.accountId }
                            if (index != -1) binding.spinnerAccount.setSelection(index)
                        }
                    }
                }

                // Collect categories
                launch {
                    viewModel.categories.collect { categories ->
                        categoriesList = categories
                        val categoryNames = categories.map { "${it.name} (${it.type})" }
                        val adapter = ArrayAdapter(this@AddEditTransactionActivity, android.R.layout.simple_spinner_item, categoryNames).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        binding.spinnerCategory.adapter = adapter

                        existingTransaction?.let { tx ->
                            val index = categoriesList.indexOfFirst { it.id == tx.categoryId }
                            if (index != -1) binding.spinnerCategory.setSelection(index)
                        }
                    }
                }

                // If edit mode, load existing transaction
                if (isEditMode) {
                    launch {
                        val tx = viewModel.getTransactionById(currentTransactionId)
                        if (tx != null) {
                            existingTransaction = tx
                            populateForm(tx)
                        } else {
                            Toast.makeText(this@AddEditTransactionActivity, "Transaction not found", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun populateForm(tx: Transaction) {
        binding.etAmount.setText(String.format(Locale.US, "%.2f", tx.amount))
        binding.etMerchant.setText(tx.merchant)
        binding.etNote.setText(tx.note ?: "")
        selectedDateMillis = tx.date
        updateDateDisplay(selectedDateMillis)

        // Select account in spinner
        val accountIndex = accountsList.indexOfFirst { it.id == tx.accountId }
        if (accountIndex != -1) {
            binding.spinnerAccount.setSelection(accountIndex)
        }

        // Select category in spinner
        // TODO: Phase 8 - Auto-categorization will hook in to pre-fill this category Spinner based on merchant text
        val categoryIndex = categoriesList.indexOfFirst { it.id == tx.categoryId }
        if (categoryIndex != -1) {
            binding.spinnerCategory.setSelection(categoryIndex)
        }
    }

    private fun setupSaveButton(isEditMode: Boolean) {
        binding.btnSaveTransaction.text = if (isEditMode) "Save Changes" else "Save Transaction"

        binding.btnSaveTransaction.setOnClickListener {
            val amountStr = binding.etAmount.text.toString().trim()
            val merchant = binding.etMerchant.text.toString().trim()
            val note = binding.etNote.text.toString().trim().ifEmpty { null }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                binding.tilAmount.error = "Enter a valid positive amount"
                return@setOnClickListener
            } else {
                binding.tilAmount.error = null
            }

            if (merchant.isBlank()) {
                binding.tilMerchant.error = "Merchant is required"
                return@setOnClickListener
            } else {
                binding.tilMerchant.error = null
            }

            val accountPosition = binding.spinnerAccount.selectedItemPosition
            if (accountPosition !in accountsList.indices) {
                Toast.makeText(this, "Please select an account", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedAccount = accountsList[accountPosition]

            val categoryPosition = binding.spinnerCategory.selectedItemPosition
            if (categoryPosition !in categoriesList.indices) {
                Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedCategory = categoriesList[categoryPosition]

            lifecycleScope.launch {
                if (isEditMode && existingTransaction != null) {
                    val updatedTransaction = existingTransaction!!.copy(
                        accountId = selectedAccount.id,
                        categoryId = selectedCategory.id,
                        amount = amount,
                        merchant = merchant,
                        note = note,
                        date = selectedDateMillis,
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.updateTransaction(updatedTransaction, existingTransaction!!)
                } else {
                    val newTransaction = Transaction(
                        accountId = selectedAccount.id,
                        categoryId = selectedCategory.id,
                        amount = amount,
                        merchant = merchant,
                        note = note,
                        date = selectedDateMillis,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.createTransaction(newTransaction)
                }

                finish()
            }
        }
    }
}
