package com.example.finora.ui.budget

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.data.db.entities.Category
import com.example.finora.databinding.ActivityBudgetSetupBinding
import com.example.finora.databinding.DialogAddEditBudgetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for configuring and managing monthly category budgets.
 * Shows list of budgets with progress bars, and allows adding/editing/deleting budgets.
 */
class BudgetSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBudgetSetupBinding

    private val viewModel: BudgetViewModel by viewModels {
        val app = application as FinoraApp
        BudgetViewModel.Factory(
            app.budgetRepository,
            app.transactionRepository,
            app.categoryRepository
        )
    }

    private lateinit var adapter: BudgetAdapter
    private var availableCategories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        val currentMonthFormatted = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        binding.tvMonthIndicator.text = "Current Month: $currentMonthFormatted"
    }

    private fun setupRecyclerView() {
        adapter = BudgetAdapter { budgetUiModel ->
            showEditBudgetDialog(budgetUiModel)
        }
        binding.rvBudgets.layoutManager = LinearLayoutManager(this)
        binding.rvBudgets.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddBudget.setOnClickListener {
            showAddBudgetDialog()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.budgetUiModels.collect { models ->
                        adapter.submitList(models)
                        if (models.isEmpty()) {
                            binding.rvBudgets.visibility = View.GONE
                            binding.layoutEmptyState.visibility = View.VISIBLE
                        } else {
                            binding.rvBudgets.visibility = View.VISIBLE
                            binding.layoutEmptyState.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.availableCategoriesForNewBudget.collect { categories ->
                        availableCategories = categories
                    }
                }
            }
        }
    }

    private fun showAddBudgetDialog() {
        if (availableCategories.isEmpty()) {
            Toast.makeText(
                this,
                "All expense categories have a budget configured for this month.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddEditBudgetBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvDialogTitle.text = "Create Monthly Budget"
        dialogBinding.tvEditCategoryName.visibility = View.GONE
        dialogBinding.btnDeleteBudget.visibility = View.GONE

        val categoryNames = availableCategories.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        dialogBinding.spinnerBudgetCategory.adapter = spinnerAdapter

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSaveBudget.setOnClickListener {
            val limitStr = dialogBinding.etMonthlyLimit.text.toString().trim()
            val limit = limitStr.toDoubleOrNull()
            if (limit == null || limit <= 0.0) {
                dialogBinding.tilMonthlyLimit.error = "Enter a valid positive budget limit"
                return@setOnClickListener
            }
            dialogBinding.tilMonthlyLimit.error = null

            val selectedIndex = dialogBinding.spinnerBudgetCategory.selectedItemPosition
            if (selectedIndex !in availableCategories.indices) {
                Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedCategory = availableCategories[selectedIndex]
            val rolloverEnabled = dialogBinding.switchRollover.isChecked

            viewModel.createBudget(selectedCategory.id, limit, rolloverEnabled)
            dialog.dismiss()
            Toast.makeText(this, "Budget created for ${selectedCategory.name}", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditBudgetDialog(item: BudgetUiModel) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddEditBudgetBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvDialogTitle.text = "Edit Budget"
        dialogBinding.tvCategoryLabel.text = "Category"
        dialogBinding.spinnerBudgetCategory.visibility = View.GONE
        dialogBinding.tvEditCategoryName.visibility = View.VISIBLE
        dialogBinding.tvEditCategoryName.text = item.categoryName
        dialogBinding.btnDeleteBudget.visibility = View.VISIBLE

        dialogBinding.etMonthlyLimit.setText(String.format(Locale.US, "%.2f", item.monthlyLimit))
        dialogBinding.switchRollover.isChecked = item.budget.rolloverEnabled

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDeleteBudget.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Budget")
                .setMessage("Are you sure you want to delete the budget for ${item.categoryName}?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteBudget(item.budget)
                    dialog.dismiss()
                    Toast.makeText(this, "Budget deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialogBinding.btnSaveBudget.setOnClickListener {
            val limitStr = dialogBinding.etMonthlyLimit.text.toString().trim()
            val limit = limitStr.toDoubleOrNull()
            if (limit == null || limit <= 0.0) {
                dialogBinding.tilMonthlyLimit.error = "Enter a valid positive budget limit"
                return@setOnClickListener
            }
            dialogBinding.tilMonthlyLimit.error = null

            val rolloverEnabled = dialogBinding.switchRollover.isChecked
            val updatedBudget = item.budget.copy(
                monthlyLimit = limit,
                rolloverEnabled = rolloverEnabled
            )

            viewModel.updateBudget(updatedBudget)
            dialog.dismiss()
            Toast.makeText(this, "Budget updated for ${item.categoryName}", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
