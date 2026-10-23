package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class BudgetFeaturesTest {
    private fun expense(id: String, amount: Long, category: Category, date: String,
                        description: String = "Expense") = Expense(
        id = id, description = description, amount = amount, category = category,
        date = LocalDate.parse(date)
    )
    private val month = YearMonth.of(2026, 10)

    @Test fun matchesSourceMonthlyReportFixture() {
        val snap = FinanceSnapshot(
            month = month, budget = 30000,
            allocations = mapOf(Category.HOUSING to 15000, Category.FOOD to 5000),
            expenses = listOf(
                expense("rent", 14000, Category.HOUSING, "2026-10-05"),
                expense("meal", 7000, Category.FOOD, "2026-10-06"),
                expense("bus", 1000, Category.TRANSPORT, "2026-10-07"),
                expense("old", 9999, Category.FOOD, "2026-09-15")
            )
        )
        val report = BudgetFeatures.report(snap)
        assertEquals(30000L, report.budget)
        assertEquals(22000L, report.actual)
        assertEquals(8000L, report.variance)
        assertEquals(8000L, report.saved)
        assertEquals(0L, report.overspent)
        assertEquals(20000L, report.allocated)
        assertEquals(10000L, report.unallocated)
        assertEquals(1000L, report.categorySaved)
        assertEquals(3000L, report.categoryOverspent)
        assertEquals(8000.0 / 30000.0 * 100, report.savingsRate, .00001)
    }

    @Test fun sourceAllowsOverAllocationWithoutCrashingReports() {
        val snap = FinanceSnapshot(month, 10000,
            mapOf(Category.FOOD to 12000, Category.HOUSING to 5000))
        val r = BudgetFeatures.report(snap)
        assertEquals(-7000L, r.unallocated)
        assertEquals(17000L, r.allocated)
    }

    @Test fun reportedSavingsAreNotNegativeWhenOverspent() {
        val r = BudgetFeatures.report(FinanceSnapshot(month, 10000,
            expenses = listOf(expense("a", 12000, Category.FOOD, "2026-10-05"))))
        assertEquals(0L, r.saved)
        assertEquals(2000L, r.overspent)
        assertEquals(120.0, r.usedRate, 0.00001)
    }

    @Test fun fullLedgerFiltersMatchSourceCaseInsensitiveSearchAndMonth() {
        val records = listOf(
            expense("a", 200, Category.FOOD, "2026-10-01", "Lunch"),
            expense("b", 300, Category.FOOD, "2026-09-01", "LUNCH"),
            expense("c", 400, Category.HOUSING, "2026-10-15", "Rent")
        )
        val selected = ExpenseFilter(search = "lUn", category = Category.FOOD, month = month).apply(records)
        assertEquals(listOf("a"), selected.map { it.id })
        assertEquals(3, ExpenseFilter().apply(records).size)
        assertEquals(listOf("c", "a"), ExpenseFilter(month = month).apply(records).map { it.id })
    }

    @Test fun csvNeutralizesFormulasQuotesAndUsesCrLf() {
        val rows = listOf(
            expense("a",1250,Category.FOOD,"2026-10-05","=SUM(A1)"),
            expense("b",150,Category.FOOD,"2026-10-05","A \"quoted\", lunch")
        )
        val csv=ExpenseCsv.render(rows)
        assertTrue(csv.startsWith("\uFEFFDate,Description,Category,Amount (AED)\r\n"))
        assertTrue(csv.contains("\"'=SUM(A1)\""))
        assertTrue(csv.contains("\"A \"\"quoted\"\", lunch\""))
        assertTrue(csv.contains(",12.50"))
        assertTrue(csv.contains("\r\n"))
    }

    @Test fun capMatchesSourceServerValidation() {
        assertEquals(9_999_999_900L, parseMinor("99999999.00"))
        assertThrows(IllegalArgumentException::class.java) {
            parseMinor("99999999.01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BudgetFeatures.plan(month, -1, emptyMap())
        }
    }
}
