package com.example.finora.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.entities.PortfolioHolding
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.repository.PortfolioRepository
import com.example.finora.repository.WatchlistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Watchlist tab, stock search, and portfolio holdings.
 * Manually constructor-injected with WatchlistRepository and PortfolioRepository.
 */
class WatchlistViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    val watchlist: StateFlow<List<WatchlistStock>> = watchlistRepository.allStocks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val holdings: StateFlow<List<PortfolioHolding>> = portfolioRepository.allHoldings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addToWatchlist(symbol: String, displayName: String) {
        viewModelScope.launch {
            watchlistRepository.insert(
                WatchlistStock(
                    symbol = symbol.uppercase().trim(),
                    displayName = displayName
                )
            )
        }
    }

    fun removeFromWatchlist(stock: WatchlistStock) {
        viewModelScope.launch {
            watchlistRepository.delete(stock)
        }
    }

    fun saveHolding(holding: PortfolioHolding) {
        viewModelScope.launch {
            if (holding.id == 0) {
                portfolioRepository.insert(holding)
            } else {
                portfolioRepository.update(holding)
            }
        }
    }

    class Factory(
        private val watchlistRepository: WatchlistRepository,
        private val portfolioRepository: PortfolioRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WatchlistViewModel::class.java)) {
                return WatchlistViewModel(watchlistRepository, portfolioRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
