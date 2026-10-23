package dev.budgettracker.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.budgettracker.android.domain.BudgetFeatures
import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Expense
import dev.budgettracker.android.domain.ExpenseSource
import dev.budgettracker.android.domain.FinanceSnapshot
import dev.budgettracker.android.domain.TrackerCommand
import dev.budgettracker.android.domain.TrackerEngine
import dev.budgettracker.android.domain.TrackerKind
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Device-only storage for the first Android milestone. No web account or sync yet. */
class FinanceDb(context: Context, databaseName: String = "finance.db"): SQLiteOpenHelper(context, databaseName, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE budgets(month TEXT PRIMARY KEY, amount INTEGER NOT NULL CHECK(amount>=0))")
        db.execSQL("CREATE TABLE allocations(month TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>=0), PRIMARY KEY(month,category))")
        db.execSQL("CREATE TABLE expenses(id TEXT PRIMARY KEY, description TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), category TEXT NOT NULL, date TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE INDEX expenses_date ON expenses(date)")
        db.execSQL("CREATE TABLE tracker_state(id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("CREATE TABLE tracker_state(id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)")
        } else {
            error("Missing non-destructive financial migration from $oldVersion to $newVersion")
        }
    }
    fun load(month: YearMonth): FinanceSnapshot {
        val db = readableDatabase
        val key = month.toString()
        val budget = db.rawQuery("SELECT amount FROM budgets WHERE month=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        val allocations = mutableMapOf<Category, Long>()
        db.rawQuery("SELECT category,amount FROM allocations WHERE month=?", arrayOf(key)).use {
            while (it.moveToNext()) allocations[Category.valueOf(it.getString(0))] = it.getLong(1)
        }
        val expenses = mutableListOf<Expense>()
        db.rawQuery("SELECT id,description,amount,category,date,source FROM expenses WHERE date>=? AND date<? ORDER BY date DESC,id DESC",
            arrayOf(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString())).use {
            while (it.moveToNext()) expenses.add(Expense(
                id=it.getString(0), description=it.getString(1), amount=it.getLong(2),
                category=Category.valueOf(it.getString(3)), date=LocalDate.parse(it.getString(4)),
                source=ExpenseSource.valueOf(it.getString(5))
            ))
        }
        return FinanceSnapshot(month, budget, allocations, expenses)
    }
    fun setBudget(month: YearMonth, amount: Long) {
        require(amount >= 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val allocated = totalAllocated(db, month)
            require(allocated <= amount) { "Reduce category allocations before lowering the budget" }
            val values = ContentValues().apply { put("month", month.toString()); put("amount", amount) }
            db.insertWithOnConflict("budgets", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun setAllocation(month: YearMonth, category: Category, amount: Long) {
        require(amount >= 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val budget = db.rawQuery("SELECT amount FROM budgets WHERE month=?", arrayOf(month.toString())).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            val old = db.rawQuery("SELECT amount FROM allocations WHERE month=? AND category=?",
                arrayOf(month.toString(),category.name)).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            val usedWithoutCategory = Math.subtractExact(totalAllocated(db, month), old)
            require(Math.addExact(usedWithoutCategory, amount) <= budget) { "Category allocations exceed monthly budget" }
            val values = ContentValues().apply {
                put("month",month.toString());put("category",category.name);put("amount",amount)
            }
            db.insertWithOnConflict("allocations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun totalAllocated(db: SQLiteDatabase, month: YearMonth): Long =
        db.rawQuery("SELECT amount FROM allocations WHERE month=?", arrayOf(month.toString())).use {
            var sum = 0L
            while (it.moveToNext()) sum = Math.addExact(sum, it.getLong(0))
            sum
        }
    fun addExpense(expense: Expense) {
        require(expense.source == ExpenseSource.MANUAL)
        val values = ContentValues().apply {
            put("id",expense.id);put("description",expense.description.trim());put("amount",expense.amount)
            put("category",expense.category.name);put("date",expense.date.toString());put("source",expense.source.name)
        }
        writableDatabase.insertOrThrow("expenses",null,values)
    }
    fun deleteExpense(id: String) {
        require(writableDatabase.delete("expenses","id=? AND source='MANUAL'", arrayOf(id))==1) {
            "Only manually entered expenses can be deleted"
        }
    }

    /** Full ledger for search, all-month filters, and exporting. */
    fun loadAllExpenses(): List<Expense> {
        val records = mutableListOf<Expense>()
        readableDatabase.rawQuery(
            "SELECT id,description,amount,category,date,source FROM expenses ORDER BY date DESC,id DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records.add(Expense(
                    id = cursor.getString(0), description = cursor.getString(1),
                    amount = cursor.getLong(2), category = Category.valueOf(cursor.getString(3)),
                    date = LocalDate.parse(cursor.getString(4)),
                    source = ExpenseSource.valueOf(cursor.getString(5))
                ))
            }
        }
        return records
    }

    /** One atomic local plan update matching the original web budget editor. */
    fun savePlan(month: YearMonth, budget: Long, allocations: Map<Category, Long>) {
        BudgetFeatures.plan(month, budget, allocations)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val budgetValues = ContentValues().apply {
                put("month", month.toString()); put("amount", budget)
            }
            db.insertWithOnConflict("budgets", null, budgetValues, SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("allocations", "month=?", arrayOf(month.toString()))
            allocations.filterValues { it > 0 }.forEach { (category, amount) ->
                val values = ContentValues().apply {
                    put("month", month.toString())
                    put("category", category.name)
                    put("amount", amount)
                }
                db.insertOrThrow("allocations", null, values)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Linked expenses cannot be altered outside the bill/debt/remittance owner. */
    fun updateManualExpense(expense: Expense) {
        require(expense.source == ExpenseSource.MANUAL)
        BudgetFeatures.checkedMinor(expense.amount)
        val values = ContentValues().apply {
            put("description", expense.description.trim())
            put("amount", expense.amount)
            put("category", expense.category.name)
            put("date", expense.date.toString())
        }
        val changed = writableDatabase.update("expenses", values,
            "id=? AND source='MANUAL'", arrayOf(expense.id))
        require(changed == 1) { "Expense not found or managed by another tracker" }
    }

    /**
     * Source-compatible Group 4 module state in a private SQLite row.
     * All bill/debt-linked expenses remain in the existing expense table and are
     * reconciled in the SAME transaction as bill/debt/savings history.
     */
    fun loadTrackerData(): JSONObject {
        val saved = readableDatabase.rawQuery("SELECT data FROM tracker_state WHERE id=1", null).use {
            if (it.moveToFirst()) JSONObject(it.getString(0)) else TrackerEngine.blank()
        }
        val items=org.json.JSONArray()
        loadAllExpenses().forEach { item ->
            items.put(JSONObject().put("id",item.id).put("description",item.description)
                .put("amount",item.amount).put("category",item.category.label)
                .put("date",item.date.toString()))
        }
        saved.put("expenses",items)
        return saved
    }

    fun applyTrackerCommand(command: TrackerCommand) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val before=loadTrackerData()
            val after=TrackerEngine.apply(before,command)
            val oldLinks=mutableMapOf<String,ExpenseSource>()
            val newLinks=mutableMapOf<String,ExpenseSource>()
            fun collect(data: JSONObject, dest: MutableMap<String,ExpenseSource>) {
                listOf(TrackerKind.BILL to ExpenseSource.BILL_PAYMENT,
                    TrackerKind.DEBT to ExpenseSource.DEBT_PAYMENT).forEach { (kind, source) ->
                    TrackerEngine.rows(data,kind.movements).forEach { payment ->
                        val id=payment.getString("expenseId")
                        require(!dest.containsKey(id)) { "Duplicate linked expense reference" }
                        dest[id]=source
                    }
                }
            }
            collect(before,oldLinks)
            collect(after,newLinks)
            val nextExpense=TrackerEngine.rows(after,"expenses").associateBy { it.getString("id") }
            // Removing a payment removes exactly its own managed expense.
            (oldLinks.keys-newLinks.keys).forEach { id ->
                val removed=db.delete("expenses","id=? AND source=?",
                    arrayOf(id, oldLinks.getValue(id).name))
                check(removed==1) { "Linked expense is missing or has incorrect owner" }
            }
            // Creating or editing a managed payment maintains one linked expense.
            newLinks.forEach { (id,source) ->
                val row=requireNotNull(nextExpense[id]) { "Payment is missing linked expense" }
                val values=ContentValues().apply {
                    put("description",row.getString("description"))
                    put("amount",row.getLong("amount"))
                    put("category",Category.entries.first {
                        it.label==row.getString("category")
                    }.name)
                    put("date",row.getString("date"))
                }
                if(id in oldLinks) {
                    require(oldLinks[id]==source) { "Linked expense owner changed" }
                    val updated=db.update("expenses",values,"id=? AND source=?",arrayOf(id,source.name))
                    check(updated==1) { "Expected linked expense row is absent" }
                } else {
                    values.put("id",id)
                    values.put("source",source.name)
                    db.insertOrThrow("expenses",null,values)
                }
            }
            val moduleData=JSONObject()
            TrackerEngine.keys.forEach { key ->
                moduleData.put(key,TrackerEngine.array(after,key))
            }
            val state=ContentValues().apply {
                put("id",1);put("data",moduleData.toString())
            }
            db.insertWithOnConflict("tracker_state",null,state,SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

}
