package dev.budgettracker.domain

import java.time.YearMonth

enum class VarianceStatus { SAVED, ON_BUDGET, OVERSPENT }

data class CategorySummary(
    val category: Category, val allocated: Money, val actual: Money,
    val varianceMinorUnits: Long, val status: VarianceStatus,
)

data class MonthlySummary(
    val month: YearMonth, val budget: Money, val spent: Money,
    val varianceMinorUnits: Long, val status: VarianceStatus,
    val allocated: Money, val unallocatedMinorUnits: Long,
    val byCategory: List<CategorySummary>,
)

object BudgetCalculator {
    fun summarize(
        budget: MonthlyBudget,
        expenses: List<Expense>,
        allocations: List<CategoryAllocation> = emptyList(),
    ): MonthlySummary {
        val selected = expenses.filter { YearMonth.from(it.date) == budget.month }
        val spent = selected.fold(Money.zero(Currency.AED)) { sum, e -> sum + e.amount }
        val active = allocations.filter { it.month == budget.month }
        require(active.map { it.category }.distinct().size == active.size)
        val totalAllocated = active.fold(Money.zero(Currency.AED)) { sum, entry -> sum + entry.amount }
        require(totalAllocated.minorUnits <= budget.amount.minorUnits) { "Allocations exceed budget" }
        val categories = Category.entries.map { category ->
            val limit = active.firstOrNull { it.category == category }?.amount ?: Money.zero(Currency.AED)
            val actual = selected.filter { it.category == category }
                .fold(Money.zero(Currency.AED)) { sum, e -> sum + e.amount }
            val variance = limit.differenceFrom(actual)
            CategorySummary(category, limit, actual, variance, status(variance))
        }
        val variance = budget.amount.differenceFrom(spent)
        return MonthlySummary(
            budget.month, budget.amount, spent, variance, status(variance),
            totalAllocated, budget.amount.differenceFrom(totalAllocated), categories,
        )
    }

    private fun status(value: Long) = when {
        value > 0L -> VarianceStatus.SAVED
        value < 0L -> VarianceStatus.OVERSPENT
        else -> VarianceStatus.ON_BUDGET
    }
}
