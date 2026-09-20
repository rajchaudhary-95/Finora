package com.example.finora.ui.transactions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.finora.FinoraApp
import com.example.finora.R
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.databinding.ActivityTransactionDetailBinding
import com.example.finora.util.ImageUtil
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity displaying read-only details of a single transaction, with edit and delete options.
 * Deleting reverses the transaction's effect on the associated account balance.
 */
class TransactionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
    }

    private lateinit var binding: ActivityTransactionDetailBinding

    private val viewModel: TransactionsViewModel by viewModels {
        val app = application as FinoraApp
        TransactionsViewModel.Factory(
            app.transactionRepository,
            app.accountRepository,
            app.categoryRepository
        )
    }

    private var transactionId: Int = -1
    private var currentTransaction: Transaction? = null
    private var currentAccount: Account? = null
    private var currentCategory: Category? = null

    private val fullDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId == -1) {
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnEditTransaction.setOnClickListener {
            val intent = Intent(this, AddEditTransactionActivity::class.java).apply {
                putExtra(AddEditTransactionActivity.EXTRA_TRANSACTION_ID, transactionId)
            }
            startActivity(intent)
        }

        binding.btnDeleteTransaction.setOnClickListener {
            confirmDelete()
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransactionDetails()
    }

    private fun loadTransactionDetails() {
        lifecycleScope.launch {
            val tx = viewModel.getTransactionById(transactionId)
            if (tx == null) {
                // Could have been deleted from edit screen
                finish()
                return@launch
            }
            currentTransaction = tx
            currentAccount = viewModel.getAccountById(tx.accountId)
            currentCategory = tx.categoryId?.let { viewModel.getCategoryById(it) }

            bindUi(tx, currentAccount, currentCategory)
        }
    }

    private fun bindUi(tx: Transaction, account: Account?, category: Category?) {
        val isIncome = category?.type?.equals("INCOME", ignoreCase = true) == true

        if (isIncome) {
            binding.tvDetailAmount.text = String.format(Locale.US, "+$%,.2f", tx.amount)
            binding.tvDetailAmount.setTextColor(ContextCompat.getColor(this, R.color.income_green))
            binding.tvDetailTypeBadge.text = "INCOME"
            binding.tvDetailTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.income_green))
        } else {
            binding.tvDetailAmount.text = String.format(Locale.US, "-$%,.2f", tx.amount)
            binding.tvDetailAmount.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
            binding.tvDetailTypeBadge.text = "EXPENSE"
            binding.tvDetailTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
        }

        binding.tvDetailMerchant.text = tx.merchant
        binding.tvDetailAccount.text = account?.name ?: "Unknown Account"
        binding.tvDetailCategory.text = category?.name ?: "Uncategorized"
        binding.ivDetailCategoryIcon.setImageResource(CategoryIconHelper.getIconForCategory(category))
        binding.tvDetailDate.text = fullDateFormat.format(Date(tx.date))
        binding.tvDetailNote.text = if (!tx.note.isNullOrBlank()) tx.note else "No notes provided"

        // Receipt photo section: show full-size image (sensibly downscaled) when present, hide entirely when null
        if (!tx.receiptImagePath.isNullOrBlank()) {
            val file = File(tx.receiptImagePath)
            if (file.exists() && file.length() > 0) {
                val receiptBitmap = ImageUtil.decodeSampledBitmapFromFile(tx.receiptImagePath, 1080, 1080)
                if (receiptBitmap != null) {
                    binding.ivDetailReceiptImage.setImageBitmap(receiptBitmap)
                    binding.layoutReceiptDetailSection.visibility = View.VISIBLE
                } else {
                    binding.layoutReceiptDetailSection.visibility = View.GONE
                }
            } else {
                binding.layoutReceiptDetailSection.visibility = View.GONE
            }
        } else {
            binding.layoutReceiptDetailSection.visibility = View.GONE
        }

        // Location section (Phase 7): show address, coordinates, and Open in Maps button when present, hide entirely when null
        if (tx.latitude != null && tx.longitude != null) {
            binding.layoutLocationDetailSection.visibility = View.VISIBLE
            binding.tvDetailCoordinates.text = String.format(Locale.US, "%.5f, %.5f", tx.latitude, tx.longitude)
            if (!tx.address.isNullOrBlank()) {
                binding.tvDetailAddress.text = tx.address
            } else {
                binding.tvDetailAddress.text = "Geotagged Location"
            }

            binding.btnOpenInMaps.setOnClickListener {
                val lat = tx.latitude
                val lng = tx.longitude
                val merchantQuery = Uri.encode(tx.merchant)
                val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($merchantQuery)")
                val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                try {
                    startActivity(mapIntent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@TransactionDetailActivity, "No Maps application installed to view location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.layoutLocationDetailSection.visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        val tx = currentTransaction ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction? This will reverse its effect on your account balance.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteTransaction(tx)
                    Toast.makeText(this@TransactionDetailActivity, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
