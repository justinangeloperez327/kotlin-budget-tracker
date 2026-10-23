package dev.budgettracker.android.network

import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Expense
import dev.budgettracker.android.domain.FinanceSnapshot
import dev.budgettracker.android.domain.ExpenseSource
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Only HTTPS origins are allowed, even for debug builds. Never accept URL credentials or path suffixes. */
fun validatedServerOrigin(input: String): String {
    val uri = try { URI(input.trim()) } catch (ex: Exception) { throw IllegalArgumentException("Enter an HTTPS server URL") }
    require(uri.scheme?.lowercase() == "https" && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") && uri.port <= 65535
    ) { "Enter an HTTPS server origin, such as https://budget.example.com" }
    return "https://" + uri.rawAuthority.lowercase().removeSuffix("/")
}

/** Opaque source snapshot: all unsupported tracker arrays are preserved on save. */
data class ServerSnapshot(
    val revision: Int,
    val email: String,
    val fullData: JSONObject
) {
    init { require(revision >= 0 && email.isNotBlank()) }

    fun forMonth(month: YearMonth): FinanceSnapshot {
        val key = month.toString()
        val budgetMap = fullData.getJSONObject("budgets")
        val budget = budgetMap.optLong(key, 0L)
        val categories = fullData.optJSONObject("categoryBudgets")?.optJSONObject(key)
        val allocations = mutableMapOf<Category, Long>()
        if (categories != null) for (category in Category.entries)
            if (categories.has(category.label)) allocations[category] = categories.getLong(category.label)
        val expenses = mutableListOf<Expense>()
        val sourceExpenses = fullData.getJSONArray("expenses")
        for (i in 0 until sourceExpenses.length()) {
            val entry = sourceExpenses.getJSONObject(i)
            if (!entry.getString("date").startsWith(key)) continue
            val id = entry.getString("id")
            expenses.add(Expense(
                id = id, description = entry.getString("description"),
                amount = entry.getLong("amount"),
                category = Category.entries.first { it.label == entry.getString("category") },
                date = LocalDate.parse(entry.getString("date")),
                source = expenseSource(id)
            ))
        }
        return FinanceSnapshot(month, budget, allocations, expenses.sortedByDescending { it.date })
    }


    fun allExpenses(): List<Expense> {
        val records = fullData.getJSONArray("expenses")
        return buildList {
            for (i in 0 until records.length()) {
                val e = records.getJSONObject(i)
                val id = e.getString("id")
                add(Expense(
                    id = id, description = e.getString("description"),
                    amount = e.getLong("amount"),
                    category = Category.entries.first { it.label == e.getString("category") },
                    date = LocalDate.parse(e.getString("date")),
                    source = expenseSource(id)
                ))
            }
        }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
    }

    fun updatePlan(month: YearMonth, budget: Long, allocations: Map<Category, Long>): JSONObject {
        dev.budgettracker.android.domain.BudgetFeatures.plan(month, budget, allocations)
        val next = copyData()
        next.getJSONObject("budgets").put(month.toString(), budget)
        val allCategories = next.optJSONObject("categoryBudgets") ?: JSONObject().also {
            next.put("categoryBudgets", it)
        }
        val monthCategories = JSONObject()
        allocations.filterValues { it > 0 }.forEach { (category, limit) ->
            monthCategories.put(category.label, limit)
        }
        allCategories.put(month.toString(), monthCategories)
        return next
    }

    fun updateManualExpense(expense: Expense): JSONObject {
        require(expense.source == ExpenseSource.MANUAL && !isLinkedExpense(expense.id)) {
            "Linked expenses must be edited from their originating tracker"
        }
        require(expense.amount in 1..dev.budgettracker.android.domain.BudgetFeatures.MAX_MINOR)
        val next = copyData()
        val items = next.getJSONArray("expenses")
        var updated = false
        for (i in 0 until items.length()) {
            val entry = items.getJSONObject(i)
            if (entry.getString("id") == expense.id) {
                entry.put("description", expense.description.trim())
                entry.put("amount", expense.amount)
                entry.put("category", expense.category.label)
                entry.put("date", expense.date.toString())
                updated = true
                break
            }
        }
        require(updated) { "Manual expense does not exist" }
        return next
    }

    fun expenseSource(id: String): ExpenseSource = when {
        linkedIn("billPayments", id) -> ExpenseSource.BILL_PAYMENT
        linkedIn("debtPayments", id) -> ExpenseSource.DEBT_PAYMENT
        linkedIn("remittances", id) -> ExpenseSource.REMITTANCE
        else -> ExpenseSource.MANUAL
    }

    private fun linkedIn(arrayName: String, id: String): Boolean {
        val records = fullData.optJSONArray(arrayName) ?: return false
        for (i in 0 until records.length()) {
            if (id == records.getJSONObject(i).optString("expenseId")) return true
        }
        return false
    }
    fun isLinkedExpense(id: String): Boolean =
        linkedIn("billPayments", id) || linkedIn("debtPayments", id) || linkedIn("remittances", id)

    private fun copyData(): JSONObject = JSONObject(fullData.toString())

    fun updateBudget(month: YearMonth, minor: Long): JSONObject {
        require(minor >= 0L)
        val next = copyData()
        next.getJSONObject("budgets").put(month.toString(), minor)
        return next
    }

    fun updateAllocation(month: YearMonth, category: Category, minor: Long): JSONObject {
        require(minor >= 0L)
        val next = copyData()
        val categories = next.getJSONObject("categoryBudgets")
        val key = month.toString()
        val map = categories.optJSONObject(key) ?: JSONObject().also { categories.put(key, it) }
        map.put(category.label, minor)
        // Server workspace validation requires a matching monthly budget row.
        val budgets = next.getJSONObject("budgets")
        if (!budgets.has(key)) budgets.put(key, 0L)
        return next
    }

    fun addManualExpense(description: String, amount: Long, category: Category, date: LocalDate): JSONObject {
        require(description.isNotBlank() && description.length <= 120 && amount in 1..dev.budgettracker.android.domain.BudgetFeatures.MAX_MINOR)
        val next = copyData()
        next.getJSONArray("expenses").put(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("description", description)
            .put("amount", amount)
            .put("category", category.label)
            .put("date", date.toString())
        )
        return next
    }

    fun removeManualExpense(id: String): JSONObject {
        require(!isLinkedExpense(id)) { "Linked expenses must be managed from their source record" }
        val next = copyData()
        val original = next.getJSONArray("expenses")
        val filtered = JSONArray()
        var found = false
        for (i in 0 until original.length()) {
            val row = original.getJSONObject(i)
            if (row.getString("id") == id) found = true else filtered.put(row)
        }
        require(found) { "Expense not found" }
        next.put("expenses", filtered)
        return next
    }
}

fun parseWorkspace(payload: JSONObject): ServerSnapshot {
    val user = payload.getJSONObject("user")
    val revision = payload.getInt("revision")
    require(revision >= 0)
    val data = payload.getJSONObject("data")
    require(data.has("budgets") && data.has("expenses") && data.has("categoryBudgets")) {
        "Server returned an unsupported workspace format"
    }
    return ServerSnapshot(revision, user.getString("email"), data)
}
