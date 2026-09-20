package com.example.finora.ui.watchlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.R
import com.example.finora.databinding.ItemStockSearchResultBinding
import com.example.finora.network.FinnhubSearchResult

/**
 * Adapter for displaying live stock search results from Finnhub API.
 */
class StockSearchAdapter(
    private val onAddClick: (FinnhubSearchResult) -> Unit
) : ListAdapter<FinnhubSearchResult, StockSearchAdapter.ViewHolder>(DiffCallback) {

    private var watchlistedSymbols: Set<String> = emptySet()

    fun setWatchlistedSymbols(symbols: Set<String>) {
        this.watchlistedSymbols = symbols.map { it.uppercase().trim() }.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStockSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemStockSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FinnhubSearchResult) {
            val symbol = item.symbol.uppercase().trim()
            binding.tvSearchSymbol.text = symbol
            binding.tvSearchDescription.text = item.description
            binding.tvSearchType.text = if (item.type.isNotBlank()) item.type else "Stock"

            val isAlreadyAdded = watchlistedSymbols.contains(symbol)
            if (isAlreadyAdded) {
                binding.btnAddWatchlist.text = "Added"
                binding.btnAddWatchlist.isEnabled = false
                binding.btnAddWatchlist.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.text_muted)
                )
                binding.btnAddWatchlist.strokeColor = ContextCompat.getColorStateList(
                    binding.root.context,
                    R.color.divider_color
                )
            } else {
                binding.btnAddWatchlist.text = "+ Add"
                binding.btnAddWatchlist.isEnabled = true
                binding.btnAddWatchlist.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.primary_teal)
                )
                binding.btnAddWatchlist.strokeColor = ContextCompat.getColorStateList(
                    binding.root.context,
                    R.color.primary_teal
                )
                binding.btnAddWatchlist.setOnClickListener {
                    onAddClick(item)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FinnhubSearchResult>() {
        override fun areItemsTheSame(oldItem: FinnhubSearchResult, newItem: FinnhubSearchResult): Boolean =
            oldItem.symbol == newItem.symbol

        override fun areContentsTheSame(oldItem: FinnhubSearchResult, newItem: FinnhubSearchResult): Boolean =
            oldItem == newItem
    }
}
