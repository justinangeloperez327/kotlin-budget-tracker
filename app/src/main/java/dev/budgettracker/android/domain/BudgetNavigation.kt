package dev.budgettracker.android.domain

/**
 * Navigation is intentionally explicit: account/tracker screens cannot invent destinations.
 * The bounded, immutable history is saveable across Android configuration changes.
 */
object BudgetNavigation {
    val destinations = listOf(
        "Dashboard", "Financial overview", "Reminders", "Budget vs. actual",
        "Expense tracker", "Bills & recurring", "Debt / Utang", "Savings goals",
        "Remittances", "Contributions", "SSS", "PhilHealth", "Pag-IBIG", "MP2", "Reports"
    )
    fun navigate(history: List<String>, target: String): ArrayList<String> {
        require(target in destinations) { "Unknown destination: $target" }
        val current=history.ifEmpty { listOf("Dashboard") }
        if(current.last()==target)return ArrayList(current)
        return ArrayList((current + target).takeLast(20))
    }
    fun back(history: List<String>): ArrayList<String> =
        ArrayList(if(history.size>1)history.dropLast(1) else listOf("Dashboard"))
}
