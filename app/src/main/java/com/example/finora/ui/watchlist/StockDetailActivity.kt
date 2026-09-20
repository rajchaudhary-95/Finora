package com.example.finora.ui.watchlist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.finora.FinoraApp
import com.example.finora.databinding.ActivityStockDetailBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/**
 * Activity presenting detailed stock quote, portfolio position, and price history chart.
 * Contains TabLayout + ViewPager2 with two tabs: Overview and Chart.
 */
class StockDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockDetailBinding

    private val viewModel: WatchlistViewModel by viewModels {
        val app = application as FinoraApp
        WatchlistViewModel.Factory(
            app.watchlistRepository,
            app.portfolioRepository
        )
    }

    private var symbol: String = ""

    companion object {
        const val EXTRA_SYMBOL = "EXTRA_SYMBOL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        symbol = intent.getStringExtra(EXTRA_SYMBOL)?.uppercase()?.trim() ?: ""

        setupToolbar()
        setupViewPager()
        observeData()

        // Fetch quote on open with cache discipline
        if (symbol.isNotBlank()) {
            viewModel.fetchQuote(symbol, forceRefresh = false)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = if (symbol.isNotBlank()) symbol else "Stock Detail"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViewPager() {
        val adapter = StockDetailPagerAdapter(this, symbol)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Overview"
                1 -> "Chart"
                else -> ""
            }
        }.attach()
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userMessage.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private class StockDetailPagerAdapter(
        activity: AppCompatActivity,
        private val symbol: String
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> StockOverviewFragment.newInstance(symbol)
                1 -> StockChartFragment.newInstance(symbol)
                else -> throw IllegalStateException("Invalid tab position: $position")
            }
        }
    }
}
