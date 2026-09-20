package com.example.finora.ui.watchlist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finora.FinoraApp
import com.example.finora.databinding.FragmentWatchlistBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class WatchlistFragment : Fragment() {

    private var _binding: FragmentWatchlistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WatchlistViewModel by viewModels {
        val app = requireActivity().application as FinoraApp
        WatchlistViewModel.Factory(
            app.watchlistRepository,
            app.portfolioRepository
        )
    }

    private lateinit var adapter: WatchlistStockAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatchlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupActions()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWatchlist(forceRefresh = false)
    }

    private fun setupRecyclerView() {
        adapter = WatchlistStockAdapter(
            onStockClick = { stock ->
                val intent = Intent(requireContext(), StockDetailActivity::class.java).apply {
                    putExtra(StockDetailActivity.EXTRA_SYMBOL, stock.symbol)
                }
                startActivity(intent)
            }
        )

        binding.rvWatchlist.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWatchlist.adapter = adapter
    }

    private fun setupActions() {
        val openWatchlistActivity = {
            val intent = Intent(requireContext(), WatchlistActivity::class.java)
            startActivity(intent)
        }

        binding.btnManageWatchlist.setOnClickListener { openWatchlistActivity() }
        binding.btnSearchStocksEmpty.setOnClickListener { openWatchlistActivity() }

        binding.swipeRefreshWatchlist.setOnRefreshListener {
            viewModel.refreshWatchlist(forceRefresh = true)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.watchlist.collect { stocks ->
                        adapter.submitList(stocks)
                        if (stocks.isEmpty()) {
                            binding.layoutEmptyWatchlist.visibility = View.VISIBLE
                            binding.rvWatchlist.visibility = View.GONE
                        } else {
                            binding.layoutEmptyWatchlist.visibility = View.GONE
                            binding.rvWatchlist.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefreshWatchlist.isRefreshing = refreshing
                    }
                }

                launch {
                    viewModel.userMessage.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
