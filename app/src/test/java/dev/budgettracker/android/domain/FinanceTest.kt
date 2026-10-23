package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceTest {
    @Test fun reportsMonthlyVarianceWithoutOtherMonths() {
        val month=YearMonth.of(2026,10)
        val data=FinanceSnapshot(month,20000L,mapOf(Category.FOOD to 10000L), listOf(
            Expense(description="Food",amount=12000L,category=Category.FOOD,date=LocalDate.of(2026,10,3)),
            Expense(description="Old",amount=99999L,category=Category.FOOD,date=LocalDate.of(2026,9,30))
        ))
        assertEquals(12000L,BudgetCalculator.summarize(data).spent)
        assertEquals(8000L,BudgetCalculator.summarize(data).variance)
    }
    @Test fun rejectsMixingCurrencies() {
        assertThrows(IllegalArgumentException::class.java){ Money(Currency.AED,100L)+Money(Currency.PHP,100L) }
    }
    @Test fun decimalParsingIsExact() {
        assertEquals(105L,parseMinor("1.05"))
        assertThrows(IllegalArgumentException::class.java){parseMinor("1.005")}
    }
    @Test fun billsClampDueDayToMonthEnd() {
        assertEquals(LocalDate.of(2028,2,29), AccountingRules.billDueDate(YearMonth.of(2028,2),31))
    }
    @Test fun ownAccountRemittanceIsNotSpending() {
        assertEquals(0L,AccountingRules.remittancePrincipalExpense(30000L,false))
    }
}
