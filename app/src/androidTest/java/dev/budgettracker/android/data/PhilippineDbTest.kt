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
class PhilippineDbTest {
    private fun isolated(): FinanceDb {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="group5-isolated-test.db"
        context.deleteDatabase(file)
        return FinanceDb(context,file)
    }
    private fun bill()=JSONObject().put("name","Rent").put("amount",190000L)
        .put("category","Housing").put("frequency","Monthly")
        .put("dueDay",3).put("startMonth","2026-01").put("active",true)

    @Test fun savingsMp2AndGovernmentDoNotCreateAedExpensesAndPreserveGroup4() {
        isolated().use {db->
            db.applyTrackerCommand(TrackerCommand.Save(TrackerKind.BILL,bill()))
            db.applyPhilippineCommand(PhilippineCommand.SaveMp2Account(
                JSONObject().put("name","MP2").put("dividendOption","Annual").put("active",true)))
            val id=PhilippineEngine.rows(db.loadTrackerData(),"mp2Accounts").single().getString("id")
            db.applyPhilippineCommand(PhilippineCommand.AddMp2Deposit(id,
                JSONObject().put("paymentDate","2026-10-20").put("amount",120000L)))
            db.applyPhilippineCommand(PhilippineCommand.SaveGovernmentAccount("SSS",
                JSONObject().put("memberType","OFW").put("frequency","Monthly").put("active",true)))
            db.applyTrackerCommand(TrackerCommand.Save(TrackerKind.BILL,bill()))
            val data=db.loadTrackerData()
            assertEquals(1,PhilippineEngine.rows(data,"mp2Accounts").size)
            assertEquals(1,PhilippineEngine.rows(data,"mp2Deposits").size)
            assertEquals(1,PhilippineEngine.rows(data,"governmentAccounts").size)
            assertTrue(db.loadAllExpenses().isEmpty())
        }
    }
    @Test fun remittanceExpenseInsertAndUndoAreAtomic() {
        isolated().use {db->
            val row=JSONObject().put("recipient","Own account").put("destinationType","Own account")
                .put("sentAmount",50000L).put("feeAmount",325L).put("transferDate","2026-10-19")
                .put("status","Completed").put("receivedAmount",777000L)
                .put("principalAsExpense",false).put("category","Other")
            db.applyPhilippineCommand(PhilippineCommand.SaveRemittance(row))
            val remittance=PhilippineEngine.rows(db.loadTrackerData(),"remittances").single()
            val expense=db.loadAllExpenses().single()
            assertEquals(325L,expense.amount)
            assertEquals(ExpenseSource.REMITTANCE,expense.source)
            assertEquals(remittance.getString("expenseId"),expense.id)
            val id=remittance.getString("id")
            db.applyPhilippineCommand(PhilippineCommand.RemoveRemittance(id))
            assertTrue(db.loadAllExpenses().isEmpty())
            assertTrue(PhilippineEngine.rows(db.loadTrackerData(),"remittances").isEmpty())
        }
    }
    @Test fun rejectedContributionLeavesPreviousDataUntouched() {
        isolated().use {db->
            db.applyPhilippineCommand(PhilippineCommand.SaveGovernmentAccount("PAGIBIG",
                JSONObject().put("memberType","Mandatory - OFW")
                    .put("frequency","Quarterly").put("active",true)))
            val id=PhilippineEngine.rows(db.loadTrackerData(),"governmentAccounts").single().getString("id")
            assertThrows(IllegalArgumentException::class.java) {
                db.applyPhilippineCommand(PhilippineCommand.SaveContribution(id,"2026-10",
                    JSONObject().put("status","Paid").put("amount",10000L)))
            }
            assertTrue(PhilippineEngine.rows(db.loadTrackerData(),"governmentContributions").isEmpty())
        }
    }
}
