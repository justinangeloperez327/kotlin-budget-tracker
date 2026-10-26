package dev.budgettracker.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.budgettracker.android.domain.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialReportsDbTest {
    private fun isolated(): FinanceDb {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="group6-financial-history-isolated.db"
        context.deleteDatabase(name)
        return FinanceDb(context,name)
    }

    @Test fun fullLedgerAndPriorBudgetsFeedHistoryWithoutChangingStoredAmounts() {
        isolated().use { db ->
            db.savePlan(YearMonth.of(2026,9),100000L,mapOf(Category.FOOD to 25000L))
            db.savePlan(YearMonth.of(2026,10),200000L,mapOf(Category.HOUSING to 100000L))
            db.addExpense(Expense(description="September meal",amount=10000L,
                category=Category.FOOD,date=LocalDate.of(2026,9,10)))
            db.addExpense(Expense(description="October rent",amount=55000L,
                category=Category.HOUSING,date=LocalDate.of(2026,10,1)))
            val document=db.loadTrackerData()
            assertEquals(100000L,document.getJSONObject("budgets").getLong("2026-09"))
            assertEquals(200000L,document.getJSONObject("budgets").getLong("2026-10"))
            assertEquals(25000L,document.getJSONObject("categoryBudgets")
                .getJSONObject("2026-09").getLong("Food"))
            val now=LocalDate.of(2026,10,26)
            val report=FinancialAnalytics.yearly(document,2026,now)
            assertEquals(300000L,report.totalBudget)
            assertEquals(65000L,report.totalSpent)
            assertEquals(235000L,report.netVariance)
            assertEquals(2,document.getJSONArray("expenses").length())
            // Reports never write or change the budget/expense tables.
            assertEquals(100000L,db.load(YearMonth.of(2026,9)).budget)
            assertEquals(55000L,db.load(YearMonth.of(2026,10)).expenses.single().amount)
        }
    }

    @Test fun monthReportAfterGroupFiveDepositKeepsPhpAndAedSeparate() {
        isolated().use { db ->
            db.savePlan(YearMonth.of(2026,10),100000L,emptyMap())
            db.applyPhilippineCommand(PhilippineCommand.SaveMp2Account(
                org.json.JSONObject().put("name","MP2")
                    .put("dividendOption","Compounded").put("active",true)))
            val account=PhilippineEngine.rows(db.loadTrackerData(),"mp2Accounts").single()
            db.applyPhilippineCommand(PhilippineCommand.AddMp2Deposit(account.getString("id"),
                org.json.JSONObject().put("paymentDate","2026-10-05").put("amount",30000L)))
            val summary=FinancialAnalytics.monthly(db.loadTrackerData(),
                YearMonth.of(2026,10),LocalDate.of(2026,10,26))
            assertEquals(100000L,summary.budget)
            assertEquals(0L,summary.spent)
            assertEquals(30000L,summary.mp2Saved)
            assertTrue(db.loadAllExpenses().isEmpty())
        }
    }
}
