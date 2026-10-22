package dev.budgettracker.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BudgetCalculatorTest {
    private val month = YearMonth.of(2026, 10)
    private fun expense(amount: Long, date: LocalDate = LocalDate.of(2026, 10, 10)) =
        Expense(UUID.randomUUID(), "Transaction", Money(Currency.AED, amount), Category.FOOD, date)

    @Test
    fun monthlyTotalsIgnoreOtherMonths() {
        val result = BudgetCalculator.summarize(
            MonthlyBudget(month, Money(Currency.AED, 20000)),
            listOf(expense(8000), expense(4000), expense(50000, LocalDate.of(2026, 9, 30))),
        )
        assertEquals(12000L, result.spent.minorUnits)
        assertEquals(8000L, result.varianceMinorUnits)
        assertEquals(VarianceStatus.SAVED, result.status)
    }

    @Test
    fun overspendingIsNegativeVariance() {
        val result = BudgetCalculator.summarize(
            MonthlyBudget(month, Money(Currency.AED, 10000)), listOf(expense(12500)),
        )
        assertEquals(-2500L, result.varianceMinorUnits)
        assertEquals(VarianceStatus.OVERSPENT, result.status)
    }

    @Test
    fun allocatedCategoriesCannotExceedBudget() {
        assertFailsWith<IllegalArgumentException> {
            BudgetCalculator.summarize(
                MonthlyBudget(month, Money(Currency.AED, 10000)),
                emptyList(),
                listOf(CategoryAllocation(month, Category.FOOD, Money(Currency.AED, 11000))),
            )
        }
    }

    @Test
    fun mixedCurrenciesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            Money(Currency.AED, 100) + Money(Currency.PHP, 100)
        }
    }

    @Test
    fun moneyFormattingIsDecimalSafe() {
        assertEquals("1.05", Money(Currency.AED, 105).decimal().toPlainString())
    }
}
