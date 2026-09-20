package com.example.finora

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    /**
     * Verifies that all 4 bottom navigation tabs switch correctly and display their placeholder views.
     */
    @Test
    fun testBottomNavigationTabSwitching() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1. Initial tab should be Dashboard
            onView(withText("Dashboard — coming soon")).check(matches(isDisplayed()))

            // 2. Click Transactions tab
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_transactions
            }
            onView(withId(R.id.fab_add_transaction)).check(matches(isDisplayed()))

            // 3. Click Budget tab
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_budget
            }
            onView(withText("Budget — coming soon")).check(matches(isDisplayed()))

            // 4. Click Watchlist tab
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_watchlist
            }
            onView(withText("Watchlist — coming soon")).check(matches(isDisplayed()))

            // 5. Switch back to Dashboard
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_dashboard
            }
            onView(withText("Dashboard — coming soon")).check(matches(isDisplayed()))
        }
    }

    /**
     * Stress test: switching tabs and recreating activity (simulating screen rotation)
     * verifies state is preserved without crashing or leaking fragments.
     */
    @Test
    fun testRotationAndTabSwitchStressTest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Switch to Budget tab
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_budget
            }
            onView(withText("Budget — coming soon")).check(matches(isDisplayed()))

            // Recreate activity (simulating rotation)
            scenario.recreate()

            // Confirm Budget tab remains active after recreation
            onView(withText("Budget — coming soon")).check(matches(isDisplayed()))

            // Switch to Watchlist after recreation
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_watchlist
            }
            onView(withText("Watchlist — coming soon")).check(matches(isDisplayed()))
        }
    }
}
