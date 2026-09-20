package com.example.finora.ui.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.databinding.ActivityNearbySpendingBinding
import com.example.finora.ui.transactions.TransactionDetailActivity
import com.example.finora.util.HaversineUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Activity listing geotagged transactions sorted ascending by distance from the user's current location.
 *
 * Testing note: Emulators support mock/injected location via Extended Controls (...) > Location.
 * Setting custom coordinates in the emulator allows testing distance sorting convincingly.
 */
class NearbySpendingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbySpendingBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCancellationTokenSource: CancellationTokenSource? = null

    private lateinit var adapter: NearbyTransactionsAdapter

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLocationAndLoadTransactions()
        } else {
            val coarseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (coarseGranted) {
                fetchLocationAndLoadTransactions()
            } else {
                showPermissionDeniedState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbySpendingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupRecyclerView()

        binding.btnRetryLocation.setOnClickListener {
            checkPermissionAndLoad()
        }

        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // Refresh list if we already have location fix
        if (currentLatitude != null && currentLongitude != null) {
            loadGeotaggedTransactions(currentLatitude!!, currentLongitude!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCancellationTokenSource?.cancel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = NearbyTransactionsAdapter { tx ->
            val intent = Intent(this, TransactionDetailActivity::class.java).apply {
                putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, tx.id)
            }
            startActivity(intent)
        }
        binding.rvNearbyTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvNearbyTransactions.adapter = adapter
    }

    private fun checkPermissionAndLoad() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            fetchLocationAndLoadTransactions()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndLoadTransactions() {
        binding.progressNearbyLoading.visibility = View.VISIBLE
        binding.tvCurrentFixStatus.text = "Acquiring current GPS location..."
        binding.layoutNearbyEmpty.visibility = View.GONE
        binding.rvNearbyTransactions.visibility = View.GONE

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

            binding.progressNearbyLoading.visibility = View.GONE

            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                binding.tvCurrentFixStatus.text = String.format(
                    Locale.US,
                    "Sorted relative to: %.4f, %.4f",
                    location.latitude,
                    location.longitude
                )
                loadGeotaggedTransactions(location.latitude, location.longitude)
            } else {
                showLocationUnavailableState()
            }
        }
    }

    private fun loadGeotaggedTransactions(currentLat: Double, currentLng: Double) {
        val app = application as FinoraApp

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.transactionRepository.getGeotaggedTransactions().collect { transactions ->
                    val categories = app.categoryRepository.allCategories.first()
                    val categoryMap = categories.associateBy { it.id }

                    if (transactions.isEmpty()) {
                        binding.layoutNearbyEmpty.visibility = View.VISIBLE
                        binding.rvNearbyTransactions.visibility = View.GONE
                        binding.tvEmptyTitle.text = "No Geotagged Spending"
                        binding.tvEmptySubtitle.text = "Tag locations when creating transactions to see your spending sorted by distance."
                        binding.btnRetryLocation.visibility = View.GONE
                    } else {
                        binding.layoutNearbyEmpty.visibility = View.GONE
                        binding.rvNearbyTransactions.visibility = View.VISIBLE

                        // Map to NearbyTransactionItem and sort ascending by Haversine distance
                        val items = transactions.mapNotNull { tx ->
                            val lat = tx.latitude
                            val lng = tx.longitude
                            if (lat != null && lng != null) {
                                val distance = HaversineUtil.calculateDistanceKm(currentLat, currentLng, lat, lng)
                                NearbyTransactionItem(
                                    transaction = tx,
                                    category = tx.categoryId?.let { categoryMap[it] },
                                    distanceKm = distance
                                )
                            } else {
                                null
                            }
                        }.sortedBy { it.distanceKm }

                        adapter.submitList(items)
                    }
                }
            }
        }
    }

    private fun showPermissionDeniedState() {
        binding.progressNearbyLoading.visibility = View.GONE
        binding.tvCurrentFixStatus.text = "Location permission unavailable"
        binding.rvNearbyTransactions.visibility = View.GONE
        binding.layoutNearbyEmpty.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = "Location Permission Required"
        binding.tvEmptySubtitle.text = "Grant location permission to calculate distances and sort transactions nearest-first."
        binding.btnRetryLocation.visibility = View.VISIBLE
    }

    private fun showLocationUnavailableState() {
        binding.progressNearbyLoading.visibility = View.GONE
        binding.tvCurrentFixStatus.text = "Unable to acquire location fix"
        binding.rvNearbyTransactions.visibility = View.GONE
        binding.layoutNearbyEmpty.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = "Location Fix Timed Out"
        binding.tvEmptySubtitle.text = "Make sure location services are enabled on your device, or set coordinates via emulator Extended Controls."
        binding.btnRetryLocation.visibility = View.VISIBLE
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
}
