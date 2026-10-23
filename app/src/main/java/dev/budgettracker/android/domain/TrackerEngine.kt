package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class TrackerKind(val records: String, val movements: String, val foreignKey: String) {
    BILL("recurringBills", "billPayments", "billId"),
    DEBT("debts", "debtPayments", "debtId"),
    SAVINGS("savingsGoals", "savingsDeposits", "goalId")
}

/** Source-shaped commands: one command changes the whole workspace atomically. */
sealed interface TrackerCommand {
    data class Save(val kind: TrackerKind, val fields: JSONObject, val id: String? = null): TrackerCommand
    data class Delete(val kind: TrackerKind, val id: String): TrackerCommand
    data class Record(val kind: TrackerKind, val parentId: String, val amount: Long,
                      val date: String, val period: String? = null,
                      val reference: String = "", val notes: String = ""): TrackerCommand
    data class Undo(val kind: TrackerKind, val id: String): TrackerCommand
}

object TrackerEngine {
    val keys = listOf("recurringBills","billPayments","debts","debtPayments","savingsGoals","savingsDeposits")
    fun blank(): JSONObject = JSONObject().apply {
        keys.forEach { put(it, JSONArray()) }
        put("expenses", JSONArray())
    }
    fun array(data: JSONObject, key: String): JSONArray = data.optJSONArray(key) ?: JSONArray()
    fun rows(data: JSONObject, key: String): List<JSONObject> {
        val array = array(data,key)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun find(data: JSONObject, kind: TrackerKind, id: String): JSONObject =
        rows(data,kind.records).firstOrNull { it.getString("id") == id }
            ?: throw IllegalArgumentException("Record no longer exists. Refresh to continue.")
    private fun cloned(data: JSONObject) = JSONObject(data.toString())
    private fun replace(data: JSONObject, key: String, values: List<JSONObject>) {
        data.put(key, JSONArray().apply { values.forEach { put(it) } })
    }
    private fun name(s: String, max: Int): String {
        val cleaned = s.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= max) { "Name must contain 1–$max characters" }
        return cleaned
    }
    private fun optional(s: String, max: Int): String? {
        val cleaned = s.trim()
        require(cleaned.length <= max) { "Text is too long (maximum $max)" }
        return cleaned.ifBlank { null }
    }
    private fun month(s: String): YearMonth {
        val m = YearMonth.parse(s)
        require(m.year in 1900..2199) { "Month must be between 1900 and 2199" }
        return m
    }
    private fun date(s: String): LocalDate {
        val d = LocalDate.parse(s)
        require(d.year in 1900..2199) { "Date must be between 1900 and 2199" }
        return d
    }
    private fun positive(amount: Long) {
        require(amount in 1..BudgetFeatures.MAX_MINOR) { "Enter a valid positive AED amount" }
    }
    private fun itemData(kind: TrackerKind, fields: JSONObject, id: String): JSONObject {
        val obj = JSONObject().put("id", id)
        obj.put("name", name(fields.getString("name"), if (kind == TrackerKind.BILL) 100 else 120))
        obj.put("active", fields.optBoolean("active",true))
        when(kind) {
            TrackerKind.BILL -> {
                val amount=fields.getLong("amount"); positive(amount)
                val category=fields.getString("category")
                require(Category.entries.any { it.label == category })
                val frequency=fields.getString("frequency")
                require(frequency in listOf("Monthly","Quarterly","Yearly"))
                val day=fields.getInt("dueDay")
                require(day in 1..31)
                val start=month(fields.getString("startMonth"))
                obj.put("amount",amount).put("category",category).put("frequency",frequency)
                    .put("dueDay",day).put("startMonth",start.toString())
                optional(fields.optString("endMonth"),7)?.let {
                    val end=month(it); require(end>=start) { "End month precedes start month" }
                    obj.put("endMonth",end.toString())
                }
            }
            TrackerKind.DEBT -> {
                val original=fields.getLong("originalAmount"); positive(original)
                val lender=name(fields.getString("lender"),120)
                val category=fields.getString("category")
                require(Category.entries.any { it.label==category })
                val start=date(fields.getString("startDate"))
                obj.put("lender",lender).put("originalAmount",original).put("category",category)
                    .put("startDate",start.toString())
                optional(fields.optString("dueDate"),10)?.let {
                    val due=date(it); require(due>=start) { "Due date precedes start date" }
                    obj.put("dueDate",due.toString())
                }
                optional(fields.optString("monthlyTarget"),20)?.let {
                    val target=it.toLong(); positive(target); obj.put("monthlyTarget",target)
                }
            }
            TrackerKind.SAVINGS -> {
                val target=fields.getLong("targetAmount");positive(target)
                val start=date(fields.getString("startDate"))
                obj.put("targetAmount",target).put("startDate",start.toString())
                optional(fields.optString("targetDate"),10)?.let {
                    val due=date(it);require(due>=start) { "Target date precedes start date" }
                    obj.put("targetDate",due.toString())
                }
                optional(fields.optString("monthlyTarget"),20)?.let {
                    val amount=it.toLong(); positive(amount);obj.put("monthlyTarget",amount)
                }
                optional(fields.optString("destination"),120)?.let { obj.put("destination",it) }
            }
        }
        optional(fields.optString("notes"),500)?.let { obj.put("notes",it) }
        return obj
    }
    fun occurs(bill: JSONObject, period: YearMonth): Boolean {
        val start=month(bill.getString("startMonth"))
        val end=bill.optString("endMonth").takeIf { it.isNotBlank() }?.let(::month)
        if (period < start || (end != null && period > end)) return false
        val months=ChronoUnit.MONTHS.between(start,period)
        val frequency=when(bill.getString("frequency")) {
            "Monthly" -> 1
            "Quarterly" -> 3
            else -> 12
        }
        return months % frequency == 0L
    }
    fun dueDate(bill: JSONObject, period: YearMonth): LocalDate =
        period.atDay(minOf(bill.getInt("dueDay"),period.lengthOfMonth()))
    fun paid(data: JSONObject, kind: TrackerKind, parentId: String): Long =
        rows(data,kind.movements).filter { it.getString(kind.foreignKey) == parentId }
            .fold(0L) { total, movement -> Math.addExact(total,movement.getLong("amount")) }
    fun remaining(data: JSONObject, kind: TrackerKind, parentId: String): Long {
        val parent=find(data,kind,parentId)
        val target=when(kind) {
            TrackerKind.DEBT -> parent.getLong("originalAmount")
            TrackerKind.SAVINGS -> parent.getLong("targetAmount")
            TrackerKind.BILL -> parent.getLong("amount")
        }
        return maxOf(0L, target - paid(data,kind,parentId))
    }
    fun status(data: JSONObject, kind: TrackerKind, parent: JSONObject, asOf: LocalDate): String {
        val id=parent.getString("id")
        return when(kind) {
            TrackerKind.DEBT -> when {
                remaining(data,kind,id)==0L -> "Paid off"
                !parent.optBoolean("active") -> "Inactive"
                parent.optString("dueDate").isNotBlank() &&
                    date(parent.getString("dueDate")).isBefore(asOf) -> "Overdue"
                else -> "Active"
            }
            TrackerKind.SAVINGS -> when {
                remaining(data,kind,id)==0L -> "Completed"
                !parent.optBoolean("active") -> "Paused"
                parent.optString("targetDate").isNotBlank() &&
                    date(parent.getString("targetDate")).isBefore(asOf) -> "Past due"
                else -> "Active"
            }
            TrackerKind.BILL -> {
                val period=YearMonth.from(asOf)
                if (!occurs(parent,period)) "Not scheduled"
                else if (rows(data,kind.movements).any {
                    it.getString("billId")==id && it.getString("period")==period.toString()
                }) "Paid"
                else {
                    val due=dueDate(parent,period)
                    when {
                        due.isBefore(asOf) -> "Overdue"
                        ChronoUnit.DAYS.between(asOf,due)<=7L -> "Due soon"
                        else -> "Upcoming"
                    }
                }
            }
        }
    }

    fun apply(input: JSONObject, command: TrackerCommand): JSONObject {
        val data=cloned(input)
        when(command) {
            is TrackerCommand.Save -> {
                val kind=command.kind
                val id=command.id ?: UUID.randomUUID().toString()
                val was=rows(data,kind.records).firstOrNull { it.getString("id")==id }
                if(command.id!=null)require(was!=null){"Cannot edit a missing record"}
                val parent=itemData(kind,command.fields,id)
                if(kind!=TrackerKind.BILL && was!=null) {
                    val target=parent.getLong(if(kind==TrackerKind.DEBT)"originalAmount" else "targetAmount")
                    require(target>=paid(data,kind,id)){"Target is lower than recorded payments"}
                }
                val updated=rows(data,kind.records).filterNot { it.getString("id")==id }+parent
                require(updated.size<=when(kind) {
                    TrackerKind.BILL -> 300; TrackerKind.DEBT -> 500; TrackerKind.SAVINGS -> 500
                }) { "Maximum records reached" }
                replace(data,kind.records,updated)
                if (kind!=TrackerKind.SAVINGS) {
                    val linkedIds=rows(data,kind.movements)
                        .filter { it.getString(kind.foreignKey)==id }
                        .map { it.getString("expenseId") }.toSet()
                    val expenses=rows(data,"expenses")
                    val changed=expenses.map { expense ->
                        if (expense.getString("id") !in linkedIds) expense else {
                            val newExpense=JSONObject(expense.toString())
                            newExpense.put("category",parent.getString("category"))
                            newExpense.put("description",if(kind==TrackerKind.BILL)parent.getString("name")
                                else ("Debt payment · "+parent.getString("name")).take(120))
                        }
                    }
                    replace(data,"expenses",changed)
                }
            }
            is TrackerCommand.Delete -> {
                require(rows(data,command.kind.records).any { it.getString("id")==command.id })
                require(rows(data,command.kind.movements).none {
                    it.getString(command.kind.foreignKey)==command.id
                }) { "This record has history. Deactivate it instead of deleting." }
                replace(data,command.kind.records,rows(data,command.kind.records).filterNot {
                    it.getString("id")==command.id
                })
            }
            is TrackerCommand.Record -> {
                val kind=command.kind
                val parent=find(data,kind,command.parentId)
                require(parent.optBoolean("active")) { "Activate this record first" }
                positive(command.amount)
                val paymentDate=date(command.date)
                val id=UUID.randomUUID().toString()
                val row=JSONObject().put("id",id).put(kind.foreignKey,command.parentId)
                    .put("amount",command.amount)
                optional(command.reference,80)?.let { row.put("referenceNumber",it) }
                optional(command.notes,500)?.let { row.put("notes",it) }
                if(kind==TrackerKind.BILL) {
                    val period=month(requireNotNull(command.period) { "Payment period is required" })
                    require(occurs(parent,period)) { "Bill is not scheduled in this month" }
                    require(rows(data,kind.movements).none {
                        it.getString("billId")==command.parentId &&
                            it.getString("period")==period.toString()
                    }) { "Bill is already marked paid for this month" }
                    row.put("period",period.toString()).put("paymentDate",paymentDate.toString())
                } else if(kind==TrackerKind.DEBT) {
                    require(command.amount<=remaining(data,kind,command.parentId)) {
                        "Repayment exceeds remaining balance"
                    }
                    row.put("paymentDate",paymentDate.toString())
                } else {
                    require(command.amount<=remaining(data,kind,command.parentId)) {
                        "Deposit exceeds remaining target"
                    }
                    row.put("depositDate",paymentDate.toString())
                }
                if(kind!=TrackerKind.SAVINGS) {
                    val expenseId=UUID.randomUUID().toString()
                    row.put("expenseId",expenseId)
                    val label=if(kind==TrackerKind.BILL)parent.getString("name")
                        else ("Debt payment · "+parent.getString("name")).take(120)
                    val expense=JSONObject().put("id",expenseId).put("description",label)
                        .put("amount",command.amount).put("category",parent.getString("category"))
                        .put("date",paymentDate.toString())
                    replace(data,"expenses",rows(data,"expenses")+expense)
                }
                val next=rows(data,kind.movements)+row
                require(next.size<=when(kind) {
                    TrackerKind.BILL -> 5000; TrackerKind.DEBT -> 5000; TrackerKind.SAVINGS -> 10000
                }) { "Maximum payment history reached" }
                replace(data,kind.movements,next)
            }
            is TrackerCommand.Undo -> {
                val kind=command.kind
                val payment=rows(data,kind.movements).firstOrNull { it.getString("id")==command.id }
                    ?: throw IllegalArgumentException("Payment record not found")
                replace(data,kind.movements,rows(data,kind.movements).filterNot {
                    it.getString("id")==command.id
                })
                if(kind!=TrackerKind.SAVINGS) {
                    val expenseId=payment.getString("expenseId")
                    replace(data,"expenses",rows(data,"expenses").filterNot {
                        it.getString("id")==expenseId
                    })
                }
            }
        }
        return data
    }
}
