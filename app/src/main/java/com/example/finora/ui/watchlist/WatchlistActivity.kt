package com.example.finora.ui.watchlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.databinding.ActivityWatchlistBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Activity for searching, viewing, and managing watchlisted stocks.
 * Implements real-time Finnhub symbol search and watchlist quote tracking.
 */
class WatchlistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchlistBinding

    private val viewModel: WatchlistViewModel by viewModels {
        val app = application as FinoraApp
        WatchlistViewModel.Factory(
            app.watchlistRepository,
            app.portfolioRepository
        )
    }

    private lateinit var searchAdapter: StockSearchAdapter
    private lateinit var watchlistAdapter: WatchlistStockAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSearch()
        setupWatchlistRecyclerView()
        setupSwipeRefresh()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh watchlist if cached data is stale (>60s)
        viewModel.refreshWatchlist(forceRefresh = false)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupSearch() {
        searchAdapter = StockSearchAdapter { searchResult ->
            viewModel.addToWatchlist(searchResult.symbol, searchResult.description) { success ->
                if (success) {
                    Snackbar.make(
                        binding.root,
                        "Added ${searchResult.symbol} to watchlist",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = searchAdapter

        binding.etSearchStock.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                viewModel.searchStocks(query)
            } else {
                viewModel.clearSearch()
            }
        }
    }

    private fun setupWatchlistRecyclerView() {
        watchlistAdapter = WatchlistStockAdapter(
            onStockClick = { stock ->
                val intent = Intent(this, StockDetailActivity::class.java).apply {
                    putExtra(StockDetailActivity.EXTRA_SYMBOL, stock.symbol)
                }
                startActivity(intent)
            },
            onStockLongClick = { stock ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Remove from Watchlist")
                    .setMessage("Do you want to stop tracking ${stock.symbol}?")
                    .setPositiveButton("Remove") { _, _ ->
                        viewModel.removeFromWatchlist(stock)
                        Snackbar.make(
                            binding.root,
                            "Removed ${stock.symbol} from watchlist",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvWatchlist.layoutManager = LinearLayoutManager(this)
        binding.rvWatchlist.adapter = watchlistAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshWatchlist.setOnRefreshListener {
            viewModel.refreshWatchlist(forceRefresh = true)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Watchlist stocks
                launch {
                    viewModel.watchlist.collect { stocks ->
                        watchlistAdapter.submitList(stocks)
                        searchAdapter.setWatchlistedSymbols(stocks.map { it.symbol }.toSet())

                        binding.tvWatchlistCount.text = "${stocks.size} stock${if (stocks.size != 1) "s" else ""}"

                        if (stocks.isEmpty()) {
                            binding.layoutEmptyWatchlist.visibility = View.VISIBLE
                            binding.rvWatchlist.visibility = View.GONE
                        } else {
                            binding.layoutEmptyWatchlist.visibility = View.GONE
                            binding.rvWatchlist.visibility = View.VISIBLE
                        }
                    }
                }

                // Search results
                launch {
                    viewModel.searchResults.collect { results ->
                        searchAdapter.submitList(results)
                        binding.rvSearchResults.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                // Search loading indicator
                launch {
                    viewModel.isSearching.collect { searching ->
                        binding.pbSearchLoading.visibility = if (searching) View.VISIBLE else View.GONE
                    }
                }

                // Swipe refresh indicator
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefreshWatchlist.isRefreshing = refreshing
                    }
                }

                // User messages & error alerts
                launch {
                    viewModel.userMessage.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
