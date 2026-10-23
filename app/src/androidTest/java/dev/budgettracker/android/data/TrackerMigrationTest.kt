package dev.budgettracker.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.budgettracker.android.domain.TrackerCommand
import dev.budgettracker.android.domain.TrackerEngine
import dev.budgettracker.android.domain.TrackerKind
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerMigrationTest {
    @Test fun versionOneBudgetAndExpensesSurviveVersionTwoMigration() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="group4-v1-migration-test.db"
        context.deleteDatabase(file)
        val legacy=object:SQLiteOpenHelper(context,file,null,1) {
            override fun onCreate(db:SQLiteDatabase) {
                db.execSQL("CREATE TABLE budgets(month TEXT PRIMARY KEY, amount INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE allocations(month TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(month,category))")
                db.execSQL("CREATE TABLE expenses(id TEXT PRIMARY KEY, description TEXT NOT NULL, amount INTEGER NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, source TEXT NOT NULL)")
                db.execSQL("CREATE INDEX expenses_date ON expenses(date)")
            }
            override fun onUpgrade(db:SQLiteDatabase, oldVersion:Int,newVersion:Int) {}
        }
        legacy.writableDatabase.use { sql ->
            sql.insertOrThrow("budgets",null,ContentValues().apply {
                put("month","2026-10");put("amount",550000L)
            })
            sql.insertOrThrow("expenses",null,ContentValues().apply {
                put("id","test-legacy-expense")
                put("description","Legacy groceries")
                put("amount",2950L)
                put("category","FOOD")
                put("date","2026-10-05")
                put("source","MANUAL")
            })
        }
        legacy.close()
        FinanceDb(context,file).use { upgraded ->
            val month=upgraded.load(YearMonth.of(2026,10))
            assertEquals(550000L,month.budget)
            assertEquals(1,month.expenses.size)
            assertEquals("Legacy groceries",month.expenses.single().description)
            assertEquals(0,TrackerEngine.rows(upgraded.loadTrackerData(),"recurringBills").size)
            upgraded.applyTrackerCommand(TrackerCommand.Save(TrackerKind.SAVINGS,JSONObject()
                .put("name","Emergency").put("targetAmount",100000)
                .put("startDate","2026-10-01").put("active",true)))
            assertEquals(550000L,upgraded.load(month.month).budget)
            assertEquals(1,upgraded.loadAllExpenses().size)
        }
    }
}
