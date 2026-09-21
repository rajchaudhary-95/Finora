package com.example.finora.ui.settings

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.FinoraApp
import com.example.finora.R
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.CategoryType
import com.example.finora.databinding.ActivitySettingsBinding
import com.example.finora.databinding.DialogAddEditCategoryBinding
import com.example.finora.databinding.ItemCategorySettingBinding
import com.example.finora.network.ApiClient
import com.example.finora.util.ApiKeyStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_SETTINGS = "finora_settings"
        const val KEY_DEFAULT_ROLLOVER = "key_default_budget_rollover"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var categoryAdapter: CategorySettingsAdapter

    private val categoryRepository by lazy {
        (application as FinoraApp).categoryRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

        setupToolbar()
        setupUserProfile()
        setupCategoryList()
        setupBudgetPreferences()
        setupFinnhubApiKey()
        setupAboutInfo()
        observeCategories()
    }

    private fun setupUserProfile() {
        val sessionManager = com.example.finora.util.UserSessionManager.getInstance(this)
        val name = sessionManager.getCurrentUserName()
        val email = sessionManager.getCurrentUserEmail()

        binding.tvUserName.text = if (name.isNotBlank()) name else "Finora User"
        binding.tvUserEmail.text = if (email.isNotBlank()) email else "Logged In"

        binding.btnLogout.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of your Finora account?")
                .setPositiveButton("Log Out") { _, _ ->
                    sessionManager.clearSession()
                    val intent = android.content.Intent(this, com.example.finora.ui.auth.LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupCategoryList() {
        categoryAdapter = CategorySettingsAdapter(
            onEditCategory = { category -> showEditCategoryDialog(category) },
            onDeleteCategory = { category -> confirmDeleteCategory(category) }
        )

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = categoryAdapter
        }

        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun setupBudgetPreferences() {
        val currentRolloverPref = prefs.getBoolean(KEY_DEFAULT_ROLLOVER, false)
        binding.switchDefaultRollover.isChecked = currentRolloverPref

        binding.switchDefaultRollover.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DEFAULT_ROLLOVER, isChecked).apply()
            val msg = if (isChecked) {
                "Default rollover enabled for new budgets"
            } else {
                "Default rollover disabled for new budgets"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFinnhubApiKey() {
        val currentKey = ApiKeyStore.getApiKey(this)
        if (currentKey.isNotBlank()) {
            binding.etFinnhubKey.setText(currentKey)
            binding.tvApiKeyStatus.text = "Status: Key configured"
            binding.tvApiKeyStatus.setTextColor(ContextCompat.getColor(this, R.color.income_green))
        } else {
            binding.tvApiKeyStatus.text = "Status: Not configured (enter a free key from finnhub.io)"
            binding.tvApiKeyStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        binding.btnTestSaveKey.setOnClickListener {
            val enteredKey = ApiKeyStore.sanitizeApiKey(binding.etFinnhubKey.text?.toString().orEmpty())
            if (enteredKey.isBlank()) {
                binding.tilFinnhubKey.error = "Please enter an API key"
                return@setOnClickListener
            }
            binding.tilFinnhubKey.error = null
            binding.btnTestSaveKey.isEnabled = false
            binding.btnTestSaveKey.text = "Testing..."

            lifecycleScope.launch {
                try {
                    val testService = ApiClient.createFinnhubService(enteredKey)
                    val quote = testService.getQuote("AAPL")
                    if (quote.currentPrice > 0 || quote.timestamp > 0) {
                        ApiKeyStore.saveApiKey(this@SettingsActivity, enteredKey)
                        binding.tvApiKeyStatus.text = "Status: Connected & Verified ✓ (AAPL: ₹${quote.currentPrice})"
                        binding.tvApiKeyStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.income_green))
                        Toast.makeText(this@SettingsActivity, "Finnhub API key verified and saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        ApiKeyStore.saveApiKey(this@SettingsActivity, enteredKey)
                        binding.tvApiKeyStatus.text = "Status: Connected & Saved ✓"
                        binding.tvApiKeyStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.income_green))
                        Toast.makeText(this@SettingsActivity, "Finnhub API key saved!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Failed to connect"
                    binding.tvApiKeyStatus.text = "Status: Verification failed ($errorMsg)"
                    binding.tvApiKeyStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.expense_red))
                    Toast.makeText(this@SettingsActivity, "Invalid key or connection error: $errorMsg", Toast.LENGTH_LONG).show()
                } finally {
                    binding.btnTestSaveKey.isEnabled = true
                    binding.btnTestSaveKey.text = "Test & Save Key"
                }
            }
        }
    }

    private fun setupAboutInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0"
            binding.tvAppVersion.text = "Version $versionName"
        } catch (e: Exception) {
            binding.tvAppVersion.text = "Version 1.0"
        }
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            categoryRepository.allCategories.collectLatest { categories ->
                categoryAdapter.submitList(categories)
            }
        }
    }

    private fun showAddCategoryDialog() {
        val dialog = AlertDialog.Builder(this)
        val dialogBinding = DialogAddEditCategoryBinding.inflate(layoutInflater)
        val alertDialog = dialog.setView(dialogBinding.root).create()

        dialogBinding.tvDialogTitle.text = "Add Category"
        dialogBinding.tvTypeLabel.visibility = View.VISIBLE
        dialogBinding.rgCategoryType.visibility = View.VISIBLE

        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etCategoryName.text.toString().trim()
            if (name.isBlank()) {
                dialogBinding.tilCategoryName.error = "Name cannot be empty"
                return@setOnClickListener
            }
            dialogBinding.tilCategoryName.error = null

            val type = if (dialogBinding.rbIncome.isChecked) {
                CategoryType.INCOME.storageValue
            } else {
                CategoryType.EXPENSE.storageValue
            }

            lifecycleScope.launch {
                val existing = categoryRepository.getByName(name)
                if (existing != null) {
                    dialogBinding.tilCategoryName.error = "A category with this name already exists"
                    return@launch
                }

                val newCategory = Category(
                    name = name,
                    type = type,
                    iconRes = if (type == CategoryType.INCOME.storageValue) R.drawable.ic_income else R.drawable.ic_expense,
                    isSystemDefault = false
                )
                categoryRepository.insert(newCategory)
                alertDialog.dismiss()
                Toast.makeText(this@SettingsActivity, "Category \"$name\" created", Toast.LENGTH_SHORT).show()
            }
        }

        alertDialog.show()
    }

    private fun showEditCategoryDialog(category: Category) {
        if (category.isSystemDefault) {
            Toast.makeText(this, "System default categories cannot be modified", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this)
        val dialogBinding = DialogAddEditCategoryBinding.inflate(layoutInflater)
        val alertDialog = dialog.setView(dialogBinding.root).create()

        dialogBinding.tvDialogTitle.text = "Rename Category"
        dialogBinding.etCategoryName.setText(category.name)
        dialogBinding.tvTypeLabel.visibility = View.GONE
        dialogBinding.rgCategoryType.visibility = View.GONE

        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val newName = dialogBinding.etCategoryName.text.toString().trim()
            if (newName.isBlank()) {
                dialogBinding.tilCategoryName.error = "Name cannot be empty"
                return@setOnClickListener
            }
            dialogBinding.tilCategoryName.error = null

            if (newName.equals(category.name, ignoreCase = true)) {
                alertDialog.dismiss()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existing = categoryRepository.getByName(newName)
                if (existing != null && existing.id != category.id) {
                    dialogBinding.tilCategoryName.error = "A category with this name already exists"
                    return@launch
                }

                categoryRepository.update(category.copy(name = newName))
                alertDialog.dismiss()
                Toast.makeText(this@SettingsActivity, "Category renamed to \"$newName\"", Toast.LENGTH_SHORT).show()
            }
        }

        alertDialog.show()
    }

    private fun confirmDeleteCategory(category: Category) {
        if (category.isSystemDefault) {
            Toast.makeText(this, "System default categories cannot be deleted", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete \"${category.name}\"? Transactions already associated with this category will remain.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    categoryRepository.delete(category)
                    Toast.makeText(this@SettingsActivity, "Category \"${category.name}\" deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class CategorySettingsAdapter(
    private val onEditCategory: (Category) -> Unit,
    private val onDeleteCategory: (Category) -> Unit
) : ListAdapter<Category, CategorySettingsAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    class CategoryViewHolder(val binding: ItemCategorySettingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategorySettingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position)
        val context = holder.itemView.context

        holder.binding.tvCategoryName.text = category.name
        holder.binding.tvCategoryType.text = category.type

        val isIncome = category.type.equals("INCOME", ignoreCase = true)
        holder.binding.tvCategoryType.setBackgroundResource(
            if (isIncome) R.drawable.bg_income_badge else R.drawable.bg_expense_badge
        )

        val iconRes = if (category.iconRes != 0) category.iconRes else {
            if (isIncome) R.drawable.ic_income else R.drawable.ic_expense
        }
        holder.binding.ivCategoryIcon.setImageResource(iconRes)

        if (category.isSystemDefault) {
            holder.binding.tvSystemDefaultBadge.visibility = View.VISIBLE
            holder.binding.btnEditCategory.visibility = View.GONE
            holder.binding.btnDeleteCategory.visibility = View.GONE
        } else {
            holder.binding.tvSystemDefaultBadge.visibility = View.GONE
            holder.binding.btnEditCategory.visibility = View.VISIBLE
            holder.binding.btnDeleteCategory.visibility = View.VISIBLE

            holder.binding.btnEditCategory.setOnClickListener {
                onEditCategory(category)
            }
            holder.binding.btnDeleteCategory.setOnClickListener {
                onDeleteCategory(category)
            }
        }
    }
}

class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
    override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean =
        oldItem == newItem
}
