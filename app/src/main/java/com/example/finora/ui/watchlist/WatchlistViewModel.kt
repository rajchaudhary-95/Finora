package com.example.finora.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finora.data.db.entities.PortfolioHolding
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.network.FinnhubSearchResult
import com.example.finora.repository.PortfolioRepository
import com.example.finora.repository.WatchlistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Watchlist tab, stock search, and stock detail screens.
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

    private val _searchResults = MutableStateFlow<List<FinnhubSearchResult>>(emptyList())
    val searchResults: StateFlow<List<FinnhubSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(replay = 0)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var searchJob: Job? = null

    /**
     * Searches for symbols matching [query] with debouncing.
     */
    fun searchStocks(query: String, debounceMs: Long = 350L) {
        searchJob?.cancel()
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            _isSearching.value = true
            val result = watchlistRepository.searchSymbols(cleanQuery)
            result.onSuccess { results ->
                _searchResults.value = results
            }.onFailure { error ->
                _searchResults.value = emptyList()
                _userMessage.emit(error.message ?: "Failed to search stocks")
            }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    /**
     * Adds a stock to the watchlist and immediately fetches its latest market quote.
     */
    fun addToWatchlist(
        symbol: String,
        displayName: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val cleanSymbol = symbol.uppercase().trim()
            val existing = watchlistRepository.getBySymbol(cleanSymbol)
            if (existing == null) {
                try {
                    watchlistRepository.insert(
                        WatchlistStock(
                            symbol = cleanSymbol,
                            displayName = if (displayName.isNotBlank()) displayName else cleanSymbol
                        )
                    )
                } catch (e: Exception) {
                    _userMessage.emit("Stock $cleanSymbol already exists in watchlist")
                }
            }

            // Immediately fetch quote for the newly added stock
            val quoteResult = watchlistRepository.fetchQuote(cleanSymbol, forceRefresh = true)
            quoteResult.onSuccess {
                onComplete?.invoke(true)
            }.onFailure { error ->
                _userMessage.emit(error.message ?: "Failed to fetch live quote for $cleanSymbol")
                onComplete?.invoke(false)
            }
        }
    }

    fun removeFromWatchlist(stock: WatchlistStock) {
        viewModelScope.launch {
            watchlistRepository.delete(stock)
        }
    }

    /**
     * Refreshes quotes for all watchlisted stocks.
     */
    fun refreshWatchlist(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val results = watchlistRepository.refreshAllWatchlist(forceRefresh)
            val failure = results.firstOrNull { it.isFailure }
            if (failure != null) {
                _userMessage.emit(failure.exceptionOrNull()?.message ?: "Some quotes could not be updated.")
            }
            _isRefreshing.value = false
        }
    }

    /**
     * Fetches a single quote for [symbol] (used by StockDetailActivity).
     */
    fun fetchQuote(symbol: String, forceRefresh: Boolean = false, onComplete: ((WatchlistStock?) -> Unit)? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = watchlistRepository.fetchQuote(symbol, forceRefresh)
            result.onSuccess { stock ->
                onComplete?.invoke(stock)
            }.onFailure { error ->
                _userMessage.emit(error.message ?: "Failed to update quote for $symbol")
                onComplete?.invoke(null)
            }
            _isRefreshing.value = false
        }
    }

    fun getHoldingForSymbol(symbol: String) = holdings.map { holdingList ->
        holdingList.firstOrNull { it.symbol.equals(symbol.trim(), ignoreCase = true) }
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

    fun deleteHolding(holding: PortfolioHolding) {
        viewModelScope.launch {
            portfolioRepository.delete(holding)
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
