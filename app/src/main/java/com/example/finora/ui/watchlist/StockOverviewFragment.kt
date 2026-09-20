package com.example.finora.ui.watchlist

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.finora.FinoraApp
import com.example.finora.R
import com.example.finora.data.db.entities.PortfolioHolding
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.databinding.FragmentStockOverviewBinding
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class StockOverviewFragment : Fragment() {

    private var _binding: FragmentStockOverviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WatchlistViewModel by activityViewModels {
        val app = requireActivity().application as FinoraApp
        WatchlistViewModel.Factory(
            app.watchlistRepository,
            app.portfolioRepository
        )
    }

    private var symbol: String = ""

    companion object {
        private const val ARG_SYMBOL = "ARG_SYMBOL"

        fun newInstance(symbol: String): StockOverviewFragment {
            return StockOverviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        symbol = arguments?.getString(ARG_SYMBOL) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDetailSymbol.text = symbol.uppercase()
        binding.tvDetailName.text = symbol.uppercase()

        binding.btnRefreshQuote.setOnClickListener {
            viewModel.fetchQuote(symbol, forceRefresh = true)
        }

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.watchlist.collect { stockList ->
                        val currentStock = stockList.firstOrNull {
                            it.symbol.equals(symbol, ignoreCase = true)
                        }
                        if (currentStock != null) {
                            bindStockData(currentStock)
                        }
                    }
                }

                launch {
                    viewModel.getHoldingForSymbol(symbol).collect { holding ->
                        val currentStock = viewModel.watchlist.value.firstOrNull {
                            it.symbol.equals(symbol, ignoreCase = true)
                        }
                        bindHoldingData(holding, currentStock?.lastKnownPrice ?: 0.0)
                    }
                }
            }
        }
    }

    private fun bindStockData(stock: WatchlistStock) {
        binding.tvDetailSymbol.text = stock.symbol
        binding.tvDetailName.text = if (stock.displayName.isNotBlank()) stock.displayName else stock.symbol

        if (stock.lastKnownPrice > 0.0) {
            binding.tvDetailPrice.text = String.format(Locale.US, "$%,.2f", stock.lastKnownPrice)
        } else {
            binding.tvDetailPrice.text = "--"
        }

        val changePercent = stock.dayChangePercent
        val prefix = if (changePercent >= 0.0) "+" else ""
        binding.tvDetailChange.text = String.format(Locale.US, "%s%.2f%%", prefix, changePercent)

        if (changePercent >= 0.0) {
            binding.tvDetailChange.setTextColor(ContextCompat.getColor(requireContext(), R.color.income_green))
            binding.tvDetailChange.setBackgroundResource(R.drawable.bg_income_badge)
        } else {
            binding.tvDetailChange.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense_red))
            binding.tvDetailChange.setBackgroundResource(R.drawable.bg_expense_badge)
        }

        if (stock.lastFetchedAt > 0L) {
            val formattedDate = DateFormat.format("MMM d, yyyy, h:mm a", Date(stock.lastFetchedAt))
            binding.tvDetailLastUpdated.text = "Last updated: $formattedDate"
        } else {
            binding.tvDetailLastUpdated.text = "Not fetched yet"
        }
    }

    private fun bindHoldingData(holding: PortfolioHolding?, currentPrice: Double) {
        if (holding != null && holding.sharesOwned > 0.0) {
            binding.layoutHoldingDetails.visibility = View.VISIBLE
            binding.layoutNotHeld.visibility = View.GONE

            binding.tvHoldingShares.text = String.format(Locale.US, "%.2f", holding.sharesOwned)
            binding.tvHoldingAvgPrice.text = String.format(Locale.US, "$%,.2f", holding.avgBuyPrice)

            val marketValue = holding.sharesOwned * currentPrice
            val totalCost = holding.sharesOwned * holding.avgBuyPrice
            val totalReturn = marketValue - totalCost
            val returnPercent = if (totalCost > 0.0) (totalReturn / totalCost) * 100.0 else 0.0

            binding.tvHoldingMarketValue.text = String.format(Locale.US, "$%,.2f", marketValue)

            val prefix = if (totalReturn >= 0.0) "+" else ""
            binding.tvHoldingTotalReturn.text = String.format(
                Locale.US,
                "%s$%,.2f (%s%.1f%%)",
                prefix,
                totalReturn,
                prefix,
                returnPercent
            )

            if (totalReturn >= 0.0) {
                binding.tvHoldingTotalReturn.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.income_green)
                )
            } else {
                binding.tvHoldingTotalReturn.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.expense_red)
                )
            }
        } else {
            binding.layoutHoldingDetails.visibility = View.GONE
            binding.layoutNotHeld.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
