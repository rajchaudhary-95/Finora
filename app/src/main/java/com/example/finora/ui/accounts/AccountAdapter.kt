package com.example.finora.ui.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finora.R
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.databinding.ItemAccountBinding
import java.util.Locale
import kotlin.math.abs

class AccountAdapter(
    private val onAccountClicked: (Account) -> Unit
) : ListAdapter<Account, AccountAdapter.AccountViewHolder>(AccountDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(
        private val binding: ItemAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(account: Account) {
            val context = binding.root.context
            binding.tvAccountName.text = account.name
            binding.tvAccountType.text = when (account.accountType) {
                AccountType.BANK -> "Bank"
                AccountType.CASH -> "Cash"
                AccountType.CREDIT_CARD -> "Credit Card"
            }

            val iconRes = when (account.accountType) {
                AccountType.BANK -> R.drawable.ic_account_bank
                AccountType.CASH -> R.drawable.ic_account_cash
                AccountType.CREDIT_CARD -> R.drawable.ic_account_card
            }
            binding.ivAccountIcon.setImageResource(iconRes)

            if (account.balance < 0) {
                binding.tvAccountBalance.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
                binding.tvAccountBalance.text = String.format(Locale.getDefault(), "-₹%,.2f", abs(account.balance))
            } else {
                binding.tvAccountBalance.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                binding.tvAccountBalance.text = String.format(Locale.getDefault(), "₹%,.2f", account.balance)
            }

            binding.root.setOnClickListener {
                onAccountClicked(account)
            }
        }
    }

    object AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem == newItem
        }
    }
}
