package com.example.finora.ui.watchlist

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.R
import com.example.finora.data.db.entities.WatchlistStock
import com.example.finora.databinding.ItemWatchlistStockBinding
import java.util.Locale

/**
 * Adapter for displaying watchlisted stocks with price and day change.
 */
class WatchlistStockAdapter(
    private val onStockClick: (WatchlistStock) -> Unit,
    private val onStockLongClick: ((WatchlistStock) -> Unit)? = null
) : ListAdapter<WatchlistStock, WatchlistStockAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWatchlistStockBinding.inflate(
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
        private val binding: ItemWatchlistStockBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(stock: WatchlistStock) {
            val context = binding.root.context
            binding.tvStockSymbol.text = stock.symbol
            binding.tvStockName.text = if (stock.displayName.isNotBlank()) stock.displayName else stock.symbol

            // Price formatting
            if (stock.lastKnownPrice > 0.0) {
                binding.tvStockPrice.text = com.example.finora.util.CurrencyFormatter.format(stock.lastKnownPrice)
            } else {
                binding.tvStockPrice.text = "--"
            }

            // Day change formatting
            val changePercent = stock.dayChangePercent
            val prefix = if (changePercent >= 0.0) "+" else ""
            binding.tvStockChange.text = String.format(Locale.US, "%s%.2f%%", prefix, changePercent)

            if (changePercent >= 0.0) {
                binding.tvStockChange.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                binding.tvStockChange.setBackgroundResource(R.drawable.bg_income_badge)
            } else {
                binding.tvStockChange.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
                binding.tvStockChange.setBackgroundResource(R.drawable.bg_expense_badge)
            }

            // Last updated timestamp
            if (stock.lastFetchedAt > 0L) {
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    stock.lastFetchedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                binding.tvStockLastUpdated.text = "Updated $relativeTime"
            } else {
                binding.tvStockLastUpdated.text = "Not fetched yet"
            }

            binding.root.setOnClickListener {
                onStockClick(stock)
            }

            binding.root.setOnLongClickListener {
                onStockLongClick?.invoke(stock)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<WatchlistStock>() {
        override fun areItemsTheSame(oldItem: WatchlistStock, newItem: WatchlistStock): Boolean =
            oldItem.symbol == newItem.symbol

        override fun areContentsTheSame(oldItem: WatchlistStock, newItem: WatchlistStock): Boolean =
            oldItem == newItem
    }
}
