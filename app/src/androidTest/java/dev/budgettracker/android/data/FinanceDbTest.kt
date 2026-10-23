package dev.budgettracker.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Expense
import dev.budgettracker.android.domain.FinanceSnapshot
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDbTest {
    @Test fun planAndExpenseEditsSurviveDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "finance.db"
        // Isolated instrumented app profile only. Never run this test against user finance data.
        context.deleteDatabase(dbName)
        val month = YearMonth.of(2026, 10)
        val id = java.util.UUID.randomUUID().toString()
        FinanceDb(context).use { store ->
            store.savePlan(month, 10000L, mapOf(Category.FOOD to 15000L))
            store.addExpense(Expense(id, "Lunch", 1200L, Category.FOOD, LocalDate.of(2026, 10, 5)))
            store.updateManualExpense(Expense(id, "Groceries", 1350L, Category.FOOD, LocalDate.of(2026, 9, 29)))
            assertEquals(0, store.load(month).expenses.size)
            assertEquals(1, store.loadAllExpenses().size)
        }
        FinanceDb(context).use { store ->
            val october = store.load(month)
            assertEquals(10000L, october.budget)
            assertEquals(15000L, october.allocations[Category.FOOD])
            assertTrue(october.expenses.isEmpty())
            val expense = store.loadAllExpenses().single()
            assertEquals("Groceries", expense.description)
            assertEquals(1350L, expense.amount)
            assertEquals(LocalDate.of(2026, 9, 29), expense.date)
            store.deleteExpense(id)
            assertTrue(store.loadAllExpenses().isEmpty())
        }
    }

    @Test fun invalidPlanDoesNotOverwriteExistingBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("finance.db")
        FinanceDb(context).use { store ->
            val month = YearMonth.of(2026, 10)
            store.savePlan(month, 50000L, mapOf(Category.FOOD to 10000L))
            assertThrows(IllegalArgumentException::class.java) {
                store.savePlan(month, -1L, mapOf(Category.FOOD to 10000L))
            }
            assertEquals(50000L, store.load(month).budget)
            assertEquals(10000L, store.load(month).allocations[Category.FOOD])
        }
    }
}
