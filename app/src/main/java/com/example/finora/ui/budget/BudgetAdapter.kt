package com.example.finora.ui.budget

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.R
import com.example.finora.databinding.ItemBudgetBinding
import com.example.finora.ui.transactions.CategoryIconHelper
import com.example.finora.util.BudgetStatus
import java.util.Locale

/**
 * Adapter for rendering budgets with color-coded progress bars and status indicators.
 */
class BudgetAdapter(
    private val onItemClick: (BudgetUiModel) -> Unit = {}
) : ListAdapter<BudgetUiModel, BudgetAdapter.BudgetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val binding = ItemBudgetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BudgetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BudgetViewHolder(
        private val binding: ItemBudgetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BudgetUiModel) {
            val context = binding.root.context

            binding.tvCategoryName.text = item.categoryName
            binding.ivCategoryIcon.setImageResource(CategoryIconHelper.getIconForCategory(item.category))

            binding.tvSpentVsLimit.text = "${com.example.finora.util.CurrencyFormatter.format(item.spentSoFar)} spent of ${com.example.finora.util.CurrencyFormatter.format(item.effectiveLimit)}"

            if (item.rolloverAmount > 0) {
                binding.tvRolloverBadge.visibility = View.VISIBLE
                binding.tvRolloverBadge.text = "+${com.example.finora.util.CurrencyFormatter.format(item.rolloverAmount)} rollover"
            } else if (item.budget.rolloverEnabled) {
                binding.tvRolloverBadge.visibility = View.VISIBLE
                binding.tvRolloverBadge.text = "Rollover ON"
            } else {
                binding.tvRolloverBadge.visibility = View.GONE
            }

            binding.progressBudget.progress = item.progressPercent
            binding.tvStatusPercent.text = String.format(Locale.US, "%d%% used", item.progressPercent)

            when (item.status) {
                BudgetStatus.OK -> {
                    val color = ContextCompat.getColor(context, R.color.income_green)
                    binding.progressBudget.progressTintList = ColorStateList.valueOf(color)
                    binding.tvRemainingAmount.setTextColor(color)
                    binding.tvRemainingAmount.text = "${com.example.finora.util.CurrencyFormatter.format(item.remaining)} left"
                }
                BudgetStatus.AT_RISK -> {
                    val color = ContextCompat.getColor(context, R.color.warning_amber)
                    binding.progressBudget.progressTintList = ColorStateList.valueOf(color)
                    binding.tvRemainingAmount.setTextColor(color)
                    binding.tvRemainingAmount.text = "${com.example.finora.util.CurrencyFormatter.format(item.remaining)} left"
                }
                BudgetStatus.OVER -> {
                    val color = ContextCompat.getColor(context, R.color.expense_red)
                    binding.progressBudget.progressTintList = ColorStateList.valueOf(color)
                    binding.tvRemainingAmount.setTextColor(color)
                    binding.tvRemainingAmount.text = "${com.example.finora.util.CurrencyFormatter.format(kotlin.math.abs(item.remaining))} over"
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BudgetUiModel>() {
            override fun areItemsTheSame(oldItem: BudgetUiModel, newItem: BudgetUiModel): Boolean =
                oldItem.budget.id == newItem.budget.id

            override fun areContentsTheSame(oldItem: BudgetUiModel, newItem: BudgetUiModel): Boolean =
                oldItem == newItem
        }
    }
}
