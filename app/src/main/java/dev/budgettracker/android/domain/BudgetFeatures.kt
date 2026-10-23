package dev.budgettracker.android.domain

import java.time.YearMonth
import java.util.Locale

/** Source-compatible budget metrics: "saved" is UNspent budget, not deposited savings. */
data class CategoryVariance(
    val category: Category,
    val limit: Long,
    val actual: Long,
    val difference: Long
)
data class MonthlyBudgetReport(
    val budget: Long,
    val actual: Long,
    val variance: Long,
    val saved: Long,
    val overspent: Long,
    val allocated: Long,
    val unallocated: Long,
    val categorySaved: Long,
    val categoryOverspent: Long,
    val usedRate: Double,
    val savingsRate: Double,
    val categories: List<CategoryVariance>
)
object BudgetFeatures {
    const val MAX_MINOR = 9_999_999_900L

    fun checkedMinor(value: Long): Long {
        require(value in 0..MAX_MINOR) { "Amount exceeds the permitted AED limit" }
        return value
    }

    fun plan(month: YearMonth, budget: Long, allocations: Map<Category, Long>) {
        checkedMinor(budget)
        allocations.values.forEach(::checkedMinor)
        require(YearMonth.parse(month.toString()) == month)
        // The source permits allocations larger than the overall monthly budget:
        // negative "unallocated" is a meaningful over-allocation warning.
    }

    fun report(snapshot: FinanceSnapshot): MonthlyBudgetReport {
        val actual = snapshot.expenses.filter { YearMonth.from(it.date) == snapshot.month }
            .fold(0L) { sum, expense -> Math.addExact(sum, expense.amount) }
        val categories = Category.entries.map { category ->
            val spent = snapshot.expenses.filter { YearMonth.from(it.date) == snapshot.month && it.category == category }
                .fold(0L) { sum, expense -> Math.addExact(sum, expense.amount) }
            val limit = snapshot.allocations[category] ?: 0
            CategoryVariance(category, limit, spent, Math.subtractExact(limit, spent))
        }
        val allocated = categories.fold(0L) { sum, item -> Math.addExact(sum, item.limit) }
        val variance = Math.subtractExact(snapshot.budget, actual)
        return MonthlyBudgetReport(
            budget = snapshot.budget, actual = actual, variance = variance,
            saved = maxOf(variance, 0), overspent = maxOf(-variance, 0),
            allocated = allocated, unallocated = Math.subtractExact(snapshot.budget, allocated),
            categorySaved = categories.fold(0L) { sum, item -> Math.addExact(sum, maxOf(item.difference, 0)) },
            categoryOverspent = categories.fold(0L) { sum, item -> Math.addExact(sum, maxOf(-item.difference, 0)) },
            usedRate = if (snapshot.budget > 0) actual * 100.0 / snapshot.budget else 0.0,
            savingsRate = if (snapshot.budget > 0) maxOf(variance, 0) * 100.0 / snapshot.budget else 0.0,
            categories = categories
        )
    }
}

/** Search and filters are over the full expense ledger, not just the dashboard month. */
data class ExpenseFilter(
    val search: String = "",
    val category: Category? = null,
    val month: YearMonth? = null
) {
    fun apply(records: List<Expense>): List<Expense> =
        records.filter { expense ->
            expense.description.contains(search.trim(), ignoreCase = true) &&
                (category == null || expense.category == category) &&
                (month == null || YearMonth.from(expense.date) == month)
        }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
}

object ExpenseCsv {
    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    private fun safe(value: String): String {
        // Prevent CSV/Excel formula execution when recipients open exported financial records.
        return quote(if (value.firstOrNull() in listOf('=', '+', '@', '-', '\t', '\r')) "'" + value else value)
    }
    fun render(expenses: List<Expense>): String = buildString {
        append("\uFEFF")
        append("Date,Description,Category,Amount (AED)\r\n")
        append(expenses.joinToString("\r\n") { expense ->
            listOf(
                quote(expense.date.toString()),
                safe(expense.description),
                quote(expense.category.label),
                "%d.%02d".format(Locale.US, expense.amount / 100, expense.amount % 100)
            ).joinToString(",")
        })
    }
}
