package dev.budgettracker.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.budgettracker.android.domain.*
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceBackupDbTest {
    private fun fresh():FinanceDb {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="group8-device-backup-isolated.db"
        context.deleteDatabase(file)
        return FinanceDb(context,file)
    }
    @Test fun allModuleRoundtripRestoresBudgetsLinksAndPhpAccounts() {
        fresh().use { db ->
            db.savePlan(YearMonth.of(2026,10),240000L,mapOf(Category.HOUSING to 130000L))
            db.addExpense(Expense(description="Groceries",amount=1850L,category=Category.FOOD,
                date=LocalDate.of(2026,10,10)))
            db.applyTrackerCommand(TrackerCommand.Save(TrackerKind.BILL,
                JSONObject().put("name","Rent").put("amount",130000L)
                    .put("category","Housing").put("frequency","Monthly").put("dueDay",15)
                    .put("startMonth","2026-01").put("active",true)))
            val bill=TrackerEngine.rows(db.loadTrackerData(),"recurringBills").single()
            db.applyTrackerCommand(TrackerCommand.Record(TrackerKind.BILL,
                bill.getString("id"),130000L,"2026-10-15","2026-10"))
            db.applyPhilippineCommand(PhilippineCommand.SaveGovernmentAccount("SSS",
                JSONObject().put("memberType","OFW").put("frequency","Monthly").put("active",true)))
            db.applyPhilippineCommand(PhilippineCommand.SaveMp2Account(
                JSONObject().put("name","MP2").put("dividendOption","Compounded").put("active",true)))
            val mp2=PhilippineEngine.rows(db.loadTrackerData(),"mp2Accounts").single()
            db.applyPhilippineCommand(PhilippineCommand.AddMp2Deposit(mp2.getString("id"),
                JSONObject().put("amount",25000L).put("paymentDate","2026-10-14")))
            val before=db.loadTrackerData()
            val backedUp=DeviceBackup.encode(before)
            val restored=DeviceBackup.decode(backedUp).data

            // Simulate later local mutations, then explicitly restore the earlier snapshot.
            db.savePlan(YearMonth.of(2026,10),300000L,emptyMap())
            db.addExpense(Expense(description="Later edit",amount=900L,category=Category.OTHER,
                date=LocalDate.of(2026,10,20)))
            db.restoreDeviceSnapshot(restored)

            val after=db.loadTrackerData()
            assertTrue(WorkspaceAudit.inspect(after).issues.toString(),WorkspaceAudit.inspect(after).passed)
            assertEquals(240000L,db.load(YearMonth.of(2026,10)).budget)
            assertEquals(2,db.loadAllExpenses().size)
            assertEquals(1,TrackerEngine.rows(after,"billPayments").size)
            assertEquals(1,PhilippineEngine.rows(after,"mp2Deposits").size)
            assertEquals(1,PhilippineEngine.rows(after,"governmentAccounts").size)
            assertEquals(130000L,db.loadAllExpenses().first {
                it.source==ExpenseSource.BILL_PAYMENT
            }.amount)
        }
    }

    @Test fun invalidImportCannotDeleteExistingRecords() {
        fresh().use { db ->
            db.savePlan(YearMonth.of(2026,10),165000L,emptyMap())
            db.addExpense(Expense(description="Keep me",amount=1000L,category=Category.FOOD,
                date=LocalDate.of(2026,10,10)))
            val bad=JSONObject(db.loadTrackerData().toString())
            val first=bad.getJSONArray("expenses").getJSONObject(0)
            first.put("category","Unknown")
            assertThrows(IllegalArgumentException::class.java) {
                db.restoreDeviceSnapshot(bad)
            }
            assertEquals(165000L,db.load(YearMonth.of(2026,10)).budget)
            assertEquals("Keep me",db.loadAllExpenses().single().description)
        }
    }
}
