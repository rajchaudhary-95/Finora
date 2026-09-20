package com.example.finora.ui.transactions

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.finora.FinoraApp
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.databinding.ActivityAddEditTransactionBinding
import com.example.finora.util.CategorizationRuleEngine
import com.example.finora.util.ImageUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Activity for adding or editing a transaction.
 * Supports date picker, account selection, category selection, receipt photo capture,
 * location tagging via FusedLocationProviderClient + Geocoder, and explicit Intent ID passing.
 *
 * Testing note: Testing location convincingly on an Android emulator requires setting custom
 * coordinates via Extended Controls (...) > Location, as the default emulator location may be static or unset.
 */
class AddEditTransactionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        private const val KEY_RECEIPT_IMAGE_PATH = "KEY_RECEIPT_IMAGE_PATH"
        private const val KEY_PENDING_PHOTO_PATH = "KEY_PENDING_PHOTO_PATH"
        private const val KEY_LATITUDE = "KEY_LATITUDE"
        private const val KEY_LONGITUDE = "KEY_LONGITUDE"
        private const val KEY_ADDRESS = "KEY_ADDRESS"
        private const val KEY_IS_AUTO_CATEGORIZED = "KEY_IS_AUTO_CATEGORIZED"
        private const val KEY_USER_MANUALLY_CHANGED_CATEGORY = "KEY_USER_MANUALLY_CHANGED_CATEGORY"
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

    // Location tagging state (Phase 7)
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentAddress: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCancellationTokenSource: CancellationTokenSource? = null

    // Auto-categorization state (Phase 8)
    private var isAutoCategorized: Boolean = false
    private var userManuallyChangedCategory: Boolean = false
    private var isProgrammaticSpinnerSelection: Boolean = false
    private var userTouchedCategorySpinner: Boolean = false
    private var categorizationRuleEngine: CategorizationRuleEngine? = null
    private var merchantDebounceJob: Job? = null

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

    /**
     * Location permission contract (Phase 7).
     * Requests ACCESS_FINE_LOCATION, falls back to checking ACCESS_COARSE_LOCATION.
     * If denied entirely, toggles switch off and shows an informative Snackbar.
     */
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchCurrentLocation()
        } else {
            // Check if coarse location was granted
            val coarseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (coarseGranted) {
                fetchCurrentLocation()
            } else {
                binding.switchTagLocation.isChecked = false
                Snackbar.make(
                    binding.root,
                    "Location permission is needed to tag spending location. You can still save without it.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        currentTransactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        val isEditMode = currentTransactionId != -1

        savedInstanceState?.let { bundle ->
            currentReceiptImagePath = bundle.getString(KEY_RECEIPT_IMAGE_PATH)
            bundle.getString(KEY_PENDING_PHOTO_PATH)?.let { pendingPhotoFile = File(it) }
            if (bundle.containsKey(KEY_LATITUDE) && bundle.containsKey(KEY_LONGITUDE)) {
                currentLatitude = bundle.getDouble(KEY_LATITUDE)
                currentLongitude = bundle.getDouble(KEY_LONGITUDE)
                currentAddress = bundle.getString(KEY_ADDRESS)
            }
            isAutoCategorized = bundle.getBoolean(KEY_IS_AUTO_CATEGORIZED, false)
            userManuallyChangedCategory = bundle.getBoolean(KEY_USER_MANUALLY_CHANGED_CATEGORY, false)
        }

        setupToolbar(isEditMode)
        setupDatePicker()
        setupCategorySpinnerListeners()
        setupMerchantAutoCategorization()
        setupReceiptCapture()
        setupLocationTagging()
        setupSaveButton(isEditMode)
        observeFormDependencies(isEditMode)

        // Restore receipt thumbnail if state had one
        currentReceiptImagePath?.let { displayReceiptThumbnail(it) }

        // Restore location if state had one
        if (currentLatitude != null && currentLongitude != null) {
            binding.switchTagLocation.isChecked = true
            displayLocationInfo(currentLatitude!!, currentLongitude!!, currentAddress)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_RECEIPT_IMAGE_PATH, currentReceiptImagePath)
        pendingPhotoFile?.let { outState.putString(KEY_PENDING_PHOTO_PATH, it.absolutePath) }
        currentLatitude?.let { outState.putDouble(KEY_LATITUDE, it) }
        currentLongitude?.let { outState.putDouble(KEY_LONGITUDE, it) }
        currentAddress?.let { outState.putString(KEY_ADDRESS, it) }
        outState.putBoolean(KEY_IS_AUTO_CATEGORIZED, isAutoCategorized)
        outState.putBoolean(KEY_USER_MANUALLY_CHANGED_CATEGORY, userManuallyChangedCategory)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCancellationTokenSource?.cancel()
        merchantDebounceJob?.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCategorySpinnerListeners() {
        binding.spinnerCategory.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN) {
                userTouchedCategorySpinner = true
            }
            false
        }

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isProgrammaticSpinnerSelection) {
                    return
                }
                if (userTouchedCategorySpinner) {
                    userManuallyChangedCategory = true
                    isAutoCategorized = false
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupMerchantAutoCategorization() {
        binding.etMerchant.doAfterTextChanged { text ->
            merchantDebounceJob?.cancel()
            merchantDebounceJob = lifecycleScope.launch {
                delay(400L)
                triggerAutoCategorization(text?.toString().orEmpty())
            }
        }
    }

    private fun triggerAutoCategorization(merchantText: String) {
        if (userManuallyChangedCategory) return
        val engine = categorizationRuleEngine ?: return
        val suggested = engine.suggestCategory(merchantText) ?: return

        val targetIndex = categoriesList.indexOfFirst { it.id == suggested.id }
        if (targetIndex != -1) {
            isProgrammaticSpinnerSelection = true
            binding.spinnerCategory.setSelection(targetIndex)
            isAutoCategorized = true
            binding.spinnerCategory.post {
                isProgrammaticSpinnerSelection = false
            }
        }
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
                        categorizationRuleEngine = CategorizationRuleEngine(categories)
                        val categoryNames = categories.map { "${it.name} (${it.type})" }
                        val adapter = ArrayAdapter(this@AddEditTransactionActivity, android.R.layout.simple_spinner_item, categoryNames).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        isProgrammaticSpinnerSelection = true
                        binding.spinnerCategory.adapter = adapter

                        existingTransaction?.let { tx ->
                            val index = categoriesList.indexOfFirst { it.id == tx.categoryId }
                            if (index != -1) binding.spinnerCategory.setSelection(index)
                        } ?: run {
                            if (!userManuallyChangedCategory && !binding.etMerchant.text.isNullOrBlank()) {
                                triggerAutoCategorization(binding.etMerchant.text.toString())
                            }
                        }
                        binding.spinnerCategory.post {
                            isProgrammaticSpinnerSelection = false
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

    private fun setupLocationTagging() {
        binding.switchTagLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (currentLatitude != null && currentLongitude != null) {
                    displayLocationInfo(currentLatitude!!, currentLongitude!!, currentAddress)
                } else {
                    checkLocationPermissionAndFetch()
                }
            } else {
                clearLocationInfo()
            }
        }
    }

    private fun checkLocationPermissionAndFetch() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            fetchCurrentLocation()
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        binding.layoutLocationLoading.visibility = View.VISIBLE
        binding.cardLocationInfo.visibility = View.GONE

        lifecycleScope.launch {
            locationCancellationTokenSource?.cancel()
            val cts = CancellationTokenSource()
            locationCancellationTokenSource = cts

            val location = withTimeoutOrNull(10_000L) {
                try {
                    fusedLocationClient.awaitCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    )
                } catch (e: Exception) {
                    null
                }
            }

            binding.layoutLocationLoading.visibility = View.GONE

            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude

                // Reverse geocode on Dispatchers.IO
                val resolvedAddress = withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(this@AddEditTransactionActivity, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            addresses[0].getAddressLine(0)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                currentAddress = resolvedAddress
                displayLocationInfo(location.latitude, location.longitude, currentAddress)
            } else {
                if (currentLatitude == null) {
                    binding.switchTagLocation.isChecked = false
                    Snackbar.make(
                        binding.root,
                        "Location request timed out or unavailable. You can still save without location.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun displayLocationInfo(lat: Double, lng: Double, address: String?) {
        binding.cardLocationInfo.visibility = View.VISIBLE
        binding.tvLocationCoordinates.text = String.format(Locale.US, "%.5f, %.5f", lat, lng)
        if (!address.isNullOrBlank()) {
            binding.tvLocationAddress.text = address
        } else {
            binding.tvLocationAddress.text = "Coordinates tagged (address unavailable)"
        }
    }

    private fun clearLocationInfo() {
        currentLatitude = null
        currentLongitude = null
        currentAddress = null
        binding.cardLocationInfo.visibility = View.GONE
        binding.layoutLocationLoading.visibility = View.GONE
    }

    @SuppressLint("MissingPermission")
    private suspend fun FusedLocationProviderClient.awaitCurrentLocation(
        priority: Int,
        token: com.google.android.gms.tasks.CancellationToken
    ): android.location.Location? = suspendCancellableCoroutine { cont ->
        getCurrentLocation(priority, token)
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener { ex ->
                if (cont.isActive) cont.resumeWithException(ex)
            }
            .addOnCanceledListener {
                if (cont.isActive) cont.cancel()
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

        // Set auto-categorization session state
        isAutoCategorized = tx.isAutoCategorized
        if (!tx.isAutoCategorized) {
            userManuallyChangedCategory = true
        }

        // Select category in spinner
        val categoryIndex = categoriesList.indexOfFirst { it.id == tx.categoryId }
        if (categoryIndex != -1) {
            isProgrammaticSpinnerSelection = true
            binding.spinnerCategory.setSelection(categoryIndex)
            binding.spinnerCategory.post {
                isProgrammaticSpinnerSelection = false
            }
        }

        // Display existing receipt thumbnail if present
        tx.receiptImagePath?.let { path ->
            if (File(path).exists()) {
                currentReceiptImagePath = path
                displayReceiptThumbnail(path)
            }
        }

        // Display existing location if present
        if (tx.latitude != null && tx.longitude != null) {
            currentLatitude = tx.latitude
            currentLongitude = tx.longitude
            currentAddress = tx.address
            binding.switchTagLocation.isChecked = true
            displayLocationInfo(tx.latitude, tx.longitude, tx.address)
        } else {
            binding.switchTagLocation.isChecked = false
            clearLocationInfo()
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
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        address = currentAddress,
                        isAutoCategorized = isAutoCategorized,
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
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        address = currentAddress,
                        isAutoCategorized = isAutoCategorized,
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
