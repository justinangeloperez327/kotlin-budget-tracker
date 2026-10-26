package dev.budgettracker.android.domain

import org.junit.Assert.*
import org.junit.Test

class BudgetNavigationTest {
    @Test fun knownRoutesOnlyAndSameScreenDoesNotAddBackEntry() {
        val first=arrayListOf("Dashboard")
        val opened=BudgetNavigation.navigate(first,"Reports")
        assertEquals(listOf("Dashboard","Reports"),opened)
        assertEquals(opened,BudgetNavigation.navigate(opened,"Reports"))
        assertEquals(listOf("Dashboard"),first)
        assertThrows(IllegalArgumentException::class.java) {
            BudgetNavigation.navigate(first,"Nonexistent screen")
        }
    }
    @Test fun remindersToTrackerAndBackPreservesPreviousScreen() {
        val one=BudgetNavigation.navigate(listOf("Dashboard"),"Reminders")
        val two=BudgetNavigation.navigate(one,"MP2")
        assertEquals("Reminders",BudgetNavigation.back(two).last())
        assertEquals(listOf("Dashboard"),BudgetNavigation.back(one))
        assertEquals(listOf("Dashboard"),BudgetNavigation.back(listOf("Dashboard")))
    }
    @Test fun navigationHistoryIsBoundedAndPreservesLatestRoutes() {
        var history=arrayListOf("Dashboard")
        repeat(101) { index ->
            history=BudgetNavigation.navigate(history,
                if(index%2==0)"Financial overview" else "Reminders")
        }
        assertEquals(20,history.size)
        assertEquals("Financial overview",history.first())
        assertEquals("Financial overview",history.last())
    }
    @Test fun allDestinationsHaveUniqueNames() {
        assertEquals(BudgetNavigation.destinations.size,
            BudgetNavigation.destinations.toSet().size)
        assertEquals("Dashboard",BudgetNavigation.destinations.first())
        assertTrue("Reports" in BudgetNavigation.destinations)
    }
}
