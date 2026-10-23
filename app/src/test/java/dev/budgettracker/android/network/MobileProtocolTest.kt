package dev.budgettracker.android.network

import dev.budgettracker.android.domain.Category
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileProtocolTest {
    @Test fun enforcesHttpsOriginWithoutCredentialsOrPaths() {
        assertEquals("https://budget.example.com", validatedServerOrigin("https://budget.example.com/"))
        for (unsafe in listOf("http://budget.example.com", "https://person:pass@budget.example.com",
                "https://budget.example.com/admin", "https://budget.example.com/?token=abc",
                "file:///tmp/test", "javascript:alert(1)")) {
            assertThrows(IllegalArgumentException::class.java) { validatedServerOrigin(unsafe) }
        }
    }

    private fun sample(): ServerSnapshot {
        val full = JSONObject("""
            {
              "budgets": {"2026-10": 50000},
              "categoryBudgets": {"2026-10":{"Food":10000}},
              "expenses": [
                {"id":"manual-1","description":"Lunch","amount":1250,"category":"Food","date":"2026-10-23"},
                {"id":"linked-1","description":"Rent","amount":20000,"category":"Housing","date":"2026-10-20"}
              ],
              "billPayments":[{"id":"payment","expenseId":"linked-1"}],
              "governmentAccounts":[{"id":"sss","provider":"SSS","active":true}],
              "mp2Deposits":[{"id":"mp2","amount":100000}],
              "remittances":[]
            }
        """.trimIndent())
        return ServerSnapshot(8, "owner@example.com", full)
    }

    @Test fun preservesUnportedModulesAndOriginalSnapshot() {
        val snapshot = sample()
        val next = snapshot.updateBudget(YearMonth.of(2026, 10), 60000L)
        assertEquals(50000L, snapshot.fullData.getJSONObject("budgets").getLong("2026-10"))
        assertEquals(60000L, next.getJSONObject("budgets").getLong("2026-10"))
        assertEquals(snapshot.fullData.getJSONArray("mp2Deposits").toString(),
            next.getJSONArray("mp2Deposits").toString())
        assertEquals(snapshot.fullData.getJSONArray("governmentAccounts").toString(),
            next.getJSONArray("governmentAccounts").toString())
    }

    @Test fun projectsCorrectMonthAndRecognizesLinkedExpenses() {
        val snapshot = sample()
        val local = snapshot.forMonth(YearMonth.of(2026, 10))
        assertEquals(2, local.expenses.size)
        assertEquals(50000L, local.budget)
        assertEquals(10000L, local.allocations[Category.FOOD])
        assertTrue(snapshot.isLinkedExpense("linked-1"))
        assertFalse(snapshot.isLinkedExpense("manual-1"))
    }

    @Test fun cannotDeleteExpenseLinkedToBillPayment() {
        assertThrows(IllegalArgumentException::class.java) { sample().removeManualExpense("linked-1") }
        val next = sample().removeManualExpense("manual-1")
        assertEquals(1, next.getJSONArray("expenses").length())
        assertEquals("linked-1", next.getJSONArray("expenses").getJSONObject(0).getString("id"))
    }

    @Test fun editsAreLimitedToManualExpensesAndKeepServerPayload() {
        val s = sample()
        val next = s.addManualExpense("Breakfast", 500L, Category.FOOD, LocalDate.of(2026,10,22))
        assertEquals(3, next.getJSONArray("expenses").length())
        assertEquals(2, s.fullData.getJSONArray("expenses").length())
        assertEquals("SSS", next.getJSONArray("governmentAccounts").getJSONObject(0).getString("provider"))
    }

    @Test fun planUpdateIsAtomicAndPreservesLinkedTrackerRecords() {
        val original = sample()
        val next = original.updatePlan(
            YearMonth.of(2026, 10), 10000,
            mapOf(Category.FOOD to 15000, Category.HEALTH to 1000)
        )
        assertEquals(10000L, next.getJSONObject("budgets").getLong("2026-10"))
        assertEquals(15000L, next.getJSONObject("categoryBudgets")
            .getJSONObject("2026-10").getLong("Food"))
        assertEquals(1, next.getJSONArray("mp2Deposits").length())
        assertEquals(1, next.getJSONArray("governmentAccounts").length())
        assertEquals(50000L, original.fullData.getJSONObject("budgets").getLong("2026-10"))
    }

    @Test fun editPreservesExpenseIdentityAndPreventsLinkedMutations() {
        val server = sample()
        val changed = server.updateManualExpense(dev.budgettracker.android.domain.Expense(
            id = "manual-1", description = "Dinner", amount = 1550,
            category = Category.HEALTH, date = LocalDate.of(2026, 9, 30)
        ))
        assertEquals("manual-1", changed.getJSONArray("expenses").getJSONObject(0).getString("id"))
        assertEquals("Dinner", changed.getJSONArray("expenses").getJSONObject(0).getString("description"))
        assertEquals("Lunch", server.fullData.getJSONArray("expenses").getJSONObject(0).getString("description"))
        assertThrows(IllegalArgumentException::class.java) {
            server.updateManualExpense(dev.budgettracker.android.domain.Expense(
                id = "linked-1", description = "Unsafe edit", amount = 100,
                category = Category.FOOD, date = LocalDate.of(2026, 10, 10)
            ))
        }
        assertEquals(2, server.allExpenses().size)
        assertEquals(dev.budgettracker.android.domain.ExpenseSource.BILL_PAYMENT,
            server.allExpenses().first { it.id == "linked-1" }.source)
    }

}
