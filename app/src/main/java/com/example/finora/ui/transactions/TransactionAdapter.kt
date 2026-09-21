package com.example.finora.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.R
import com.example.finora.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ListAdapter for transactions displaying merchant, category, account, date, and colored amounts.
 */
class TransactionAdapter(
    private val onItemClick: (TransactionUiModel) -> Unit
) : ListAdapter<TransactionUiModel, TransactionAdapter.TransactionViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionUiModel) {
            val context = binding.root.context
            binding.tvMerchant.text = item.transaction.merchant
            binding.badgeAutoCategorized.visibility = if (item.transaction.isAutoCategorized) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.tvCategoryAccount.text = "${item.categoryName} • ${item.accountName}"
            binding.tvDate.text = dateFormat.format(Date(item.transaction.date))
            binding.ivCategoryIcon.setImageResource(item.categoryIconRes)

            if (item.isIncome) {
                binding.tvAmount.text = "+${com.example.finora.util.CurrencyFormatter.format(item.transaction.amount)}"
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
            } else {
                binding.tvAmount.text = "-${com.example.finora.util.CurrencyFormatter.format(item.transaction.amount)}"
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
            binding.tvMerchant.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TransactionUiModel>() {
            override fun areItemsTheSame(oldItem: TransactionUiModel, newItem: TransactionUiModel): Boolean =
                oldItem.transaction.id == newItem.transaction.id

            override fun areContentsTheSame(oldItem: TransactionUiModel, newItem: TransactionUiModel): Boolean =
                oldItem == newItem
        }
    }
}
