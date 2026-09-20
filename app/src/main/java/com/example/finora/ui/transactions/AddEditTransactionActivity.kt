package com.example.finora.ui.transactions

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.finora.FinoraApp
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.databinding.ActivityAddEditTransactionBinding
import com.example.finora.util.ImageUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for adding or editing a transaction.
 * Supports date picker, account selection, category selection, receipt photo capture, and explicit Intent ID passing.
 */
class AddEditTransactionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        private const val KEY_RECEIPT_IMAGE_PATH = "KEY_RECEIPT_IMAGE_PATH"
        private const val KEY_PENDING_PHOTO_PATH = "KEY_PENDING_PHOTO_PATH"
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

    private var currentReceiptImagePath: String? = null
    private var pendingPhotoFile: File? = null

    /**
     * Camera permission contract.
     * If denied, shows a Snackbar informing the user and allows saving without receipt.
     */
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Snackbar.make(
                binding.root,
                "Camera permission is needed to scan receipts. You can still save this transaction without one.",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    /**
     * TakePicture contract.
     * Takes destination content URI and saves full-res photo directly to the designated file.
     */
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val photoFile = pendingPhotoFile
        if (success && photoFile != null && photoFile.exists() && photoFile.length() > 0) {
            currentReceiptImagePath = photoFile.absolutePath
            displayReceiptThumbnail(photoFile.absolutePath)
        } else {
            // Clean up 0-byte file if user cancelled camera
            if (photoFile != null && photoFile.exists() && photoFile.length() == 0L) {
                photoFile.delete()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentTransactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        val isEditMode = currentTransactionId != -1

        savedInstanceState?.let { bundle ->
            currentReceiptImagePath = bundle.getString(KEY_RECEIPT_IMAGE_PATH)
            bundle.getString(KEY_PENDING_PHOTO_PATH)?.let { pendingPhotoFile = File(it) }
        }

        setupToolbar(isEditMode)
        setupDatePicker()
        setupReceiptCapture()
        setupSaveButton(isEditMode)
        observeFormDependencies(isEditMode)

        // Restore receipt thumbnail if state had one
        currentReceiptImagePath?.let { displayReceiptThumbnail(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_RECEIPT_IMAGE_PATH, currentReceiptImagePath)
        pendingPhotoFile?.let { outState.putString(KEY_PENDING_PHOTO_PATH, it.absolutePath) }
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

    private fun setupReceiptCapture() {
        binding.btnScanReceipt.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.btnReplaceReceipt.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.btnRemoveReceipt.setOnClickListener {
            clearReceiptPreview()
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        try {
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs()
            }
            val photoFile = File(storageDir, "receipt_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayReceiptThumbnail(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            clearReceiptPreview()
            return
        }

        // Downscale bitmap to avoid memory pressure (approx 200x200 px for thumbnail)
        val thumbnailBitmap = ImageUtil.decodeSampledBitmapFromFile(filePath, 200, 200)
        if (thumbnailBitmap != null) {
            binding.ivReceiptThumbnail.setImageBitmap(thumbnailBitmap)
            binding.tvReceiptFilename.text = file.name
            binding.layoutReceiptPreview.visibility = View.VISIBLE
            binding.btnScanReceipt.visibility = View.GONE
        } else {
            clearReceiptPreview()
        }
    }

    private fun clearReceiptPreview() {
        currentReceiptImagePath = null
        binding.ivReceiptThumbnail.setImageDrawable(null)
        binding.layoutReceiptPreview.visibility = View.GONE
        binding.btnScanReceipt.visibility = View.VISIBLE
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

        // Display existing receipt thumbnail if present
        tx.receiptImagePath?.let { path ->
            if (File(path).exists()) {
                currentReceiptImagePath = path
                displayReceiptThumbnail(path)
            }
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
                        receiptImagePath = currentReceiptImagePath,
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
                        receiptImagePath = currentReceiptImagePath,
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
