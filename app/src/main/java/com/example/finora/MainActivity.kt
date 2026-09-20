package com.example.finora

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.finora.databinding.ActivityMainBinding
import com.example.finora.ui.budget.BudgetFragment
import com.example.finora.ui.dashboard.DashboardFragment
import com.example.finora.ui.transactions.TransactionsFragment
import com.example.finora.ui.watchlist.WatchlistFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var activeFragmentTag: String = TAG_DASHBOARD

    companion object {
        const val TAG_DASHBOARD = "TAG_DASHBOARD"
        const val TAG_TRANSACTIONS = "TAG_TRANSACTIONS"
        const val TAG_BUDGET = "TAG_BUDGET"
        const val TAG_WATCHLIST = "TAG_WATCHLIST"
        private const val KEY_ACTIVE_TAG = "KEY_ACTIVE_TAG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            val dashboard = DashboardFragment()
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container, dashboard, TAG_DASHBOARD)
                .commit()
            activeFragmentTag = TAG_DASHBOARD
        } else {
            activeFragmentTag = savedInstanceState.getString(KEY_ACTIVE_TAG, TAG_DASHBOARD)
            // Ensure only the active fragment is visible after recreation/rotation
            val tx = supportFragmentManager.beginTransaction()
            listOf(TAG_DASHBOARD, TAG_TRANSACTIONS, TAG_BUDGET, TAG_WATCHLIST).forEach { tag ->
                val frag = supportFragmentManager.findFragmentByTag(tag)
                if (frag != null) {
                    if (tag == activeFragmentTag) {
                        tx.show(frag)
                    } else {
                        tx.hide(frag)
                    }
                }
            }
            tx.commit()
            supportFragmentManager.executePendingTransactions()
        }

        val targetMenuId = when (activeFragmentTag) {
            TAG_DASHBOARD -> R.id.nav_dashboard
            TAG_TRANSACTIONS -> R.id.nav_transactions
            TAG_BUDGET -> R.id.nav_budget
            TAG_WATCHLIST -> R.id.nav_watchlist
            else -> R.id.nav_dashboard
        }
        if (binding.bottomNav.selectedItemId != targetMenuId) {
            binding.bottomNav.selectedItemId = targetMenuId
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetTag = when (item.itemId) {
                R.id.nav_dashboard -> TAG_DASHBOARD
                R.id.nav_transactions -> TAG_TRANSACTIONS
                R.id.nav_budget -> TAG_BUDGET
                R.id.nav_watchlist -> TAG_WATCHLIST
                else -> return@setOnItemSelectedListener false
            }
            switchFragment(targetTag)
            true
        }

        setSupportActionBar(binding.topAppBar)

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_settings) {
                startActivity(android.content.Intent(this, com.example.finora.ui.settings.SettingsActivity::class.java))
                true
            } else {
                false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(android.content.Intent(this, com.example.finora.ui.settings.SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Swaps fragments using hide/show to preserve state and scroll positions.
     */
    private fun switchFragment(targetTag: String) {
        if (targetTag == activeFragmentTag) return

        val fm = supportFragmentManager
        val currentFrag = fm.findFragmentByTag(activeFragmentTag)
        val targetFrag = fm.findFragmentByTag(targetTag)

        val tx = fm.beginTransaction().setReorderingAllowed(true)

        if (currentFrag != null && currentFrag.isAdded) {
            tx.hide(currentFrag)
        }

        if (targetFrag == null) {
            val newFrag: Fragment = when (targetTag) {
                TAG_DASHBOARD -> DashboardFragment()
                TAG_TRANSACTIONS -> TransactionsFragment()
                TAG_BUDGET -> BudgetFragment()
                TAG_WATCHLIST -> WatchlistFragment()
                else -> DashboardFragment()
            }
            tx.add(R.id.fragment_container, newFrag, targetTag)
        } else {
            tx.show(targetFrag)
        }

        tx.commit()
        fm.executePendingTransactions()
        activeFragmentTag = targetTag
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAG, activeFragmentTag)
    }
}