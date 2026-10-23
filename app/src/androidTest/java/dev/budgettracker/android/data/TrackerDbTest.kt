package dev.budgettracker.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.budgettracker.android.domain.*
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDbTest {
    private fun store(): FinanceDb {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "tracker-group4-isolated-tests.db"
        context.deleteDatabase(database)
        return FinanceDb(context,database)
    }
    private fun bill() = JSONObject().put("name","Rent").put("amount",185000)
        .put("category","Housing").put("frequency","Monthly").put("dueDay",5)
        .put("startMonth","2026-01").put("active",true)

    @Test fun paymentAndExpenseCommitTogetherAndUndoTogether() {
        store().use { db ->
            db.addExpense(Expense(description="Groceries",amount=2500,
                category=Category.FOOD,date=LocalDate.of(2026,10,1)))
            db.applyTrackerCommand(TrackerCommand.Save(TrackerKind.BILL,bill()))
            val id=TrackerEngine.rows(db.loadTrackerData(),"recurringBills").single().getString("id")
            db.applyTrackerCommand(TrackerCommand.Record(TrackerKind.BILL,id,185000,
                "2026-10-05","2026-10"))
            val stored=db.loadTrackerData()
            val payment=TrackerEngine.rows(stored,"billPayments").single()
            val entry=db.loadAllExpenses().first { it.id==payment.getString("expenseId") }
            assertEquals(ExpenseSource.BILL_PAYMENT,entry.source)
            assertEquals(185000L,entry.amount)
            assertEquals(2,db.loadAllExpenses().size)
            assertThrows(IllegalArgumentException::class.java) {
                db.applyTrackerCommand(TrackerCommand.Record(TrackerKind.BILL,id,185000,
                    "2026-10-06","2026-10"))
            }
            assertEquals(2,db.loadAllExpenses().size)
            db.applyTrackerCommand(TrackerCommand.Undo(TrackerKind.BILL,payment.getString("id")))
            assertEquals(1,db.loadAllExpenses().size)
            assertEquals(0,TrackerEngine.rows(db.loadTrackerData(),"billPayments").size)
        }
    }

    @Test fun savingsDepositDoesNotCreateAnExpense() {
        store().use { db ->
            val goal=JSONObject().put("name","Emergency")
                .put("targetAmount",100000).put("startDate","2026-01-01")
                .put("active",true)
            db.applyTrackerCommand(TrackerCommand.Save(TrackerKind.SAVINGS,goal))
            val id=TrackerEngine.rows(db.loadTrackerData(),"savingsGoals").single().getString("id")
            db.applyTrackerCommand(TrackerCommand.Record(TrackerKind.SAVINGS,id,35000,"2026-10-03"))
            assertEquals(1,TrackerEngine.rows(db.loadTrackerData(),"savingsDeposits").size)
            assertTrue(db.loadAllExpenses().isEmpty())
        }
    }
}
