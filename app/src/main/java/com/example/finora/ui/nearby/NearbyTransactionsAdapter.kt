package com.example.finora.ui.nearby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.R
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.databinding.ItemNearbyTransactionBinding
import com.example.finora.ui.transactions.CategoryIconHelper
import com.example.finora.util.HaversineUtil
import java.util.Locale

data class NearbyTransactionItem(
    val transaction: Transaction,
    val category: Category?,
    val distanceKm: Double
)

class NearbyTransactionsAdapter(
    private val onItemClick: (Transaction) -> Unit
) : ListAdapter<NearbyTransactionItem, NearbyTransactionsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearbyTransactionBinding.inflate(
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
        private val binding: ItemNearbyTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NearbyTransactionItem) {
            val tx = item.transaction
            val context = binding.root.context

            binding.tvNearbyMerchant.text = tx.merchant
            binding.tvNearbyCategory.text = item.category?.name ?: "Uncategorized"
            binding.ivNearbyCategoryIcon.setImageResource(
                CategoryIconHelper.getIconForCategory(item.category)
            )

            val isIncome = item.category?.type?.equals("INCOME", ignoreCase = true) == true
            if (isIncome) {
                binding.tvNearbyAmount.text = "+${com.example.finora.util.CurrencyFormatter.format(tx.amount)}"
                binding.tvNearbyAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
            } else {
                binding.tvNearbyAmount.text = "-${com.example.finora.util.CurrencyFormatter.format(tx.amount)}"
                binding.tvNearbyAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
            }

            binding.tvNearbyDistance.text = HaversineUtil.formatDistance(item.distanceKm)
            binding.tvNearbyAddress.text = tx.address ?: String.format(Locale.US, "%.5f, %.5f", tx.latitude, tx.longitude)

            binding.root.setOnClickListener {
                onItemClick(tx)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<NearbyTransactionItem>() {
        override fun areItemsTheSame(oldItem: NearbyTransactionItem, newItem: NearbyTransactionItem): Boolean {
            return oldItem.transaction.id == newItem.transaction.id
        }

        override fun areContentsTheSame(oldItem: NearbyTransactionItem, newItem: NearbyTransactionItem): Boolean {
            return oldItem == newItem
        }
    }
}
