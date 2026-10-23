package dev.budgettracker.android.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Currency { AED, PHP }

data class Money(val currency: Currency, val minor: Long) {
    init { require(minor >= 0L) }
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot combine AED and PHP" }
        return Money(currency, Math.addExact(minor, other.minor))
    }
    fun decimal(): BigDecimal = BigDecimal.valueOf(minor, 2)
}

fun parseMinor(input: String): Long {
    require(input.trim().matches(Regex("""\d{1,12}(\.\d{1,2})?"""))) { "Enter a valid amount (max 2 decimals)" }
    return BigDecimal(input.trim()).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
}

enum class Category(val label: String) {
    HOUSING("Housing"), FOOD("Food"), TRANSPORT("Transport"), SHOPPING("Shopping"),
    HEALTH("Health"), ENTERTAINMENT("Entertainment"), OTHER("Other")
}
enum class ExpenseSource { MANUAL, BILL_PAYMENT, DEBT_PAYMENT, REMITTANCE }

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val amount: Long,
    val category: Category,
    val date: LocalDate,
    val source: ExpenseSource = ExpenseSource.MANUAL,
) {
    init {
        require(description.isNotBlank() && description.length <= 120)
        require(amount > 0)
    }
}
data class FinanceSnapshot(
    val month: YearMonth,
    val budget: Long = 0L,
    val allocations: Map<Category, Long> = emptyMap(),
    val expenses: List<Expense> = emptyList(),
)
data class Summary(val budget: Long, val spent: Long, val variance: Long, val allocated: Long)
object BudgetCalculator {
    fun summarize(snapshot: FinanceSnapshot): Summary {
        val spent = snapshot.expenses.filter { YearMonth.from(it.date) == snapshot.month }
            .fold(0L) { total, expense -> Math.addExact(total, expense.amount) }
        val allocated = snapshot.allocations.values.fold(0L, Math::addExact)
        require(allocated <= snapshot.budget) { "Allocations exceed budget" }
        return Summary(snapshot.budget, spent, Math.subtractExact(snapshot.budget, spent), allocated)
    }
    fun categorySpent(snapshot: FinanceSnapshot, category: Category): Long =
        snapshot.expenses.filter { YearMonth.from(it.date) == snapshot.month && it.category == category }
            .fold(0L) { total, expense -> Math.addExact(total, expense.amount) }
}

/** Parity policies from next-budget-tracker; persistence of linked payments is a future phase. */
object AccountingRules {
    fun billOccurs(start: YearMonth, end: YearMonth?, period: YearMonth, frequencyMonths: Int): Boolean {
        require(frequencyMonths in setOf(1,3,12))
        if (period < start || (end != null && period > end)) return false
        return ((period.year - start.year) * 12 + period.monthValue - start.monthValue) % frequencyMonths == 0
    }
    fun billDueDate(period: YearMonth, dueDay: Int): LocalDate {
        require(dueDay in 1..31)
        return period.atDay(minOf(dueDay, period.lengthOfMonth()))
    }
    fun remittancePrincipalExpense(amount: Long, principalAsExpense: Boolean) = if (principalAsExpense) amount else 0L
}
