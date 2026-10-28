package dev.budgettracker.android.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.budgettracker.android.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulates v1 -> v2 installation upgrade at the SQLite layer, then exercises
 * Group 4/5 linked transactions and Group 8 backup after migration.
 * An actual signed APK install-over remains an explicit physical-device gate.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeAcceptanceTest {
    private val file="release-upgrade-acceptance-isolated.db"

    @Test fun olderFinanceDataSurvivesUpgradeNewModulesRestoreAndReopen() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(file)
        val expenseId=UUID.randomUUID().toString()
        val legacy=object:SQLiteOpenHelper(context,file,null,1) {
            override fun onCreate(db:SQLiteDatabase) {
                db.execSQL("CREATE TABLE budgets(month TEXT PRIMARY KEY, amount INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE allocations(month TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(month,category))")
                db.execSQL("CREATE TABLE expenses(id TEXT PRIMARY KEY, description TEXT NOT NULL, amount INTEGER NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, source TEXT NOT NULL)")
                db.execSQL("CREATE INDEX expenses_date ON expenses(date)")
            }
            override fun onUpgrade(db:SQLiteDatabase, oldVersion:Int,newVersion:Int) {}
        }
        legacy.writableDatabase.use {sql ->
            sql.insertOrThrow("budgets",null,ContentValues().apply{
                put("month","2025-12");put("amount",200000L)
            })
            sql.insertOrThrow("budgets",null,ContentValues().apply{
                put("month","2026-10");put("amount",360000L)
            })
            sql.insertOrThrow("allocations",null,ContentValues().apply{
                put("month","2025-12");put("category","FOOD");put("amount",25000L)
            })
            sql.insertOrThrow("expenses",null,ContentValues().apply{
                put("id",expenseId);put("description","Legacy groceries")
                put("amount",12950L);put("category","FOOD")
                put("date","2025-12-15");put("source","MANUAL")
            })
        }
        legacy.close()

        FinanceDb(context,file).use { upgraded ->
            val previous=upgraded.load(YearMonth.of(2025,12))
            assertEquals(200000L,previous.budget)
            assertEquals(25000L,previous.allocations[Category.FOOD])
            assertEquals(expenseId,previous.expenses.single().id)

            upgraded.applyTrackerCommand(TrackerCommand.Save(TrackerKind.BILL,JSONObject()
                .put("name","Insurance").put("category","Other").put("amount",42000L)
                .put("frequency","Monthly").put("dueDay",10)
                .put("startMonth","2026-01").put("active",true)))
            val bill=TrackerEngine.rows(upgraded.loadTrackerData(),"recurringBills").single()
            upgraded.applyTrackerCommand(TrackerCommand.Record(
                TrackerKind.BILL,bill.getString("id"),42000L,"2026-10-10","2026-10"))
            upgraded.applyPhilippineCommand(PhilippineCommand.SaveMp2Account(JSONObject()
                .put("name","MP2").put("dividendOption","Annual").put("active",true)))
            val mp2=PhilippineEngine.rows(upgraded.loadTrackerData(),"mp2Accounts").single()
            upgraded.applyPhilippineCommand(PhilippineCommand.AddMp2Deposit(
                mp2.getString("id"),JSONObject().put("amount",15000L)
                    .put("paymentDate","2026-10-15")))

            val backup=DeviceBackup.decode(DeviceBackup.encode(upgraded.loadTrackerData())).data
            assertTrue(WorkspaceAudit.inspect(backup).issues.toString(),
                WorkspaceAudit.inspect(backup).passed)
            upgraded.restoreDeviceSnapshot(backup)
            assertEquals(2,upgraded.loadAllExpenses().size)
            assertEquals(1,TrackerEngine.rows(upgraded.loadTrackerData(),"billPayments").size)
        }

        FinanceDb(context,file).use { reopened ->
            assertEquals(200000L,reopened.load(YearMonth.of(2025,12)).budget)
            assertEquals(expenseId,reopened.load(YearMonth.of(2025,12)).expenses.single().id)
            assertEquals(360000L,reopened.load(YearMonth.of(2026,10)).budget)
            val snapshot=reopened.loadTrackerData()
            assertEquals(1,PhilippineEngine.rows(snapshot,"mp2Deposits").size)
            assertEquals(1,TrackerEngine.rows(snapshot,"billPayments").size)
            assertEquals(1,reopened.loadAllExpenses().count {
                it.source==ExpenseSource.BILL_PAYMENT
            })
        }
    }
}
