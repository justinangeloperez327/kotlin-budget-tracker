package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject

/** Amounts are integer minor units. AED and PHP are never summed. */
data class FinancialMonth(
    val month: YearMonth,
    val asOf: LocalDate,
    val budget: Long,
    val spent: Long,
    val variance: Long,
    val categorySpent: Map<Category, Long>,
    val billsPaid: Long,
    val debtRepaid: Long,
    val savingsAdded: Long,
    val remitted: Long,
    val pendingRemittances: Long,
    val remittanceFees: Long,
    val supportRemitted: Long,
    val ownTransfers: Long,
    val governmentPaid: Long,
    val mp2Saved: Long,
    val remittanceReceived: Long
) {
    val unspent: Long get() = maxOf(0L, variance)
    val overspent: Long get() = maxOf(0L, -variance)
    val usedRate: Double get() = if (budget > 0) spent.toDouble() * 100.0 / budget else 0.0
}

data class FinancialYear(val year: Int, val months: List<FinancialMonth>) {
    fun aed(selector: (FinancialMonth)->Long): Long =
        months.fold(0L) { acc, month -> Math.addExact(acc, selector(month)) }
    fun php(selector: (FinancialMonth)->Long): Long =
        months.fold(0L) { acc, month -> Math.addExact(acc, selector(month)) }
    val totalBudget get() = aed { it.budget }
    val totalSpent get() = aed { it.spent }
    val netVariance get() = Math.subtractExact(totalBudget,totalSpent)
    val totalUnspent get() = aed { it.unspent }
    val totalOverspent get() = aed { it.overspent }
}

data class FinancialOverview(
    val month: FinancialMonth,
    val billsOutstanding: Long,
    val overdueBills: Int,
    val debtRemaining: Long,
    val overdueDebts: Int,
    val savingsBalance: Long,
    val savingsRemaining: Long,
    val governmentYtdPaid: Long,
    val mp2TotalSaved: Long,
    val contributionGaps: Int
)

object FinancialAnalytics {
    fun cutoff(month: YearMonth, today: LocalDate): LocalDate =
        minOf(month.atEndOfMonth(), today)

    private fun entries(data: JSONObject, key: String): List<JSONObject> =
        TrackerEngine.rows(data,key)

    private fun sum(rows: List<JSONObject>, field: String): Long =
        rows.fold(0L) { acc, row -> Math.addExact(acc, row.getLong(field)) }

    private fun recorded(rows: List<JSONObject>, key: String, month: YearMonth,
                         until: LocalDate): List<JSONObject> =
        rows.filter {
            val date=LocalDate.parse(it.getString(key))
            date.year == month.year && date.monthValue == month.monthValue && date <= until
        }

    fun monthly(data: JSONObject, month: YearMonth,
                today: LocalDate = LocalDate.now()): FinancialMonth {
        val until=cutoff(month,today)
        val expenses=recorded(entries(data,"expenses"),"date",month,until)
        val category=Category.entries.associateWith { cat ->
            sum(expenses.filter { it.getString("category")==cat.label },"amount")
        }
        val budget=data.optJSONObject("budgets")?.optLong(month.toString(),0L) ?: 0L
        val spent=sum(expenses,"amount")
        val paidBills=recorded(entries(data,"billPayments"),"paymentDate",month,until)
        val debt=recorded(entries(data,"debtPayments"),"paymentDate",month,until)
        val savings=recorded(entries(data,"savingsDeposits"),"depositDate",month,until)
        val remittances=recorded(entries(data,"remittances"),"transferDate",month,until)
        val completed=remittances.filter { it.getString("status")=="Completed" }
        val pending=remittances.filter { it.getString("status")=="Pending" }
        val government=entries(data,"governmentContributions").filter {
            it.getString("period")==month.toString() && it.getString("status")=="Paid" &&
                (!it.has("paymentDate") || LocalDate.parse(it.getString("paymentDate"))<=until)
        }
        val mp2=recorded(entries(data,"mp2Deposits"),"paymentDate",month,until)
        return FinancialMonth(
            month=month,asOf=until,budget=budget,spent=spent,
            variance=Math.subtractExact(budget,spent),categorySpent=category,
            billsPaid=sum(paidBills,"amount"),debtRepaid=sum(debt,"amount"),
            savingsAdded=sum(savings,"amount"),remitted=sum(completed,"sentAmount"),
            pendingRemittances=sum(pending,"sentAmount"),
            remittanceFees=sum(remittances,"feeAmount"),
            supportRemitted=sum(completed.filter { it.optBoolean("principalAsExpense") },"sentAmount"),
            ownTransfers=sum(completed.filter { !it.optBoolean("principalAsExpense") },"sentAmount"),
            governmentPaid=sum(government,"amount"),mp2Saved=sum(mp2,"amount"),
            remittanceReceived=completed.fold(0L){ acc,row ->
                Math.addExact(acc,row.optLong("receivedAmount",0L))
            }
        )
    }

    fun yearly(data: JSONObject, year: Int,
               today: LocalDate = LocalDate.now()): FinancialYear {
        require(year in 1900..2199)
        val end = when {
            year < today.year -> 12
            year == today.year -> today.monthValue
            else -> 0
        }
        return FinancialYear(year,(1..end).map { monthly(data,YearMonth.of(year,it),today) })
    }

    fun availableYears(data: JSONObject, today: LocalDate=LocalDate.now()): List<Int> {
        val result=mutableSetOf(today.year)
        val budgets=data.optJSONObject("budgets")
        if(budgets!=null)budgets.keys().forEach { key ->
            runCatching { YearMonth.parse(key).year }.getOrNull()?.let(result::add)
        }
        val fields=mapOf(
            "expenses" to "date","billPayments" to "paymentDate",
            "debtPayments" to "paymentDate","savingsDeposits" to "depositDate",
            "remittances" to "transferDate","governmentContributions" to "period",
            "mp2Deposits" to "paymentDate"
        )
        fields.forEach { (key, dateField) ->
            entries(data,key).forEach { row ->
                row.optString(dateField).take(4).toIntOrNull()?.let(result::add)
            }
        }
        return result.filter { it in 1900..2199 }.sortedDescending()
    }

    fun overview(data: JSONObject, month: YearMonth,
                 today: LocalDate=LocalDate.now()): FinancialOverview {
        val report=monthly(data,month,today)
        val until=report.asOf
        val bills=entries(data,"recurringBills").filter {
            it.optBoolean("active") && TrackerEngine.occurs(it,month) &&
                // Don't report bills started in a future period as outstanding
                !month.atDay(1).isAfter(until)
        }
        val paid=entries(data,"billPayments").filter {
            LocalDate.parse(it.getString("paymentDate"))<=until &&
                it.getString("period")==month.toString()
        }.map { it.getString("billId") }.toSet()
        val outstanding=bills.filter { it.getString("id") !in paid }
        val debts=entries(data,"debts").filter {
            LocalDate.parse(it.getString("startDate"))<=until
        }
        val debtPayments=entries(data,"debtPayments").filter {
            LocalDate.parse(it.getString("paymentDate"))<=until
        }
        val remaining=debts.associate { debt ->
            val id=debt.getString("id")
            val paidAmount=sum(debtPayments.filter { it.getString("debtId")==id },"amount")
            id to maxOf(0L,Math.subtractExact(debt.getLong("originalAmount"),paidAmount))
        }
        val goals=entries(data,"savingsGoals").filter {
            LocalDate.parse(it.getString("startDate"))<=until
        }
        val deposits=entries(data,"savingsDeposits").filter {
            LocalDate.parse(it.getString("depositDate"))<=until
        }
        val savedByGoal=goals.associate { goal ->
            val id=goal.getString("id")
            id to sum(deposits.filter { it.getString("goalId")==id },"amount")
        }
        val accounts=entries(data,"governmentAccounts")
        val contribs=entries(data,"governmentContributions")
        val expectedGaps=accounts.filter { it.optBoolean("active") }.sumOf { account ->
            PhilippineEngine.expectedPeriods(account,until.year).count { period ->
                period < YearMonth.from(until).toString() && contribs.none {
                    it.getString("accountId")==account.getString("id") &&
                        it.getString("period")==period
                }
            }
        }
        val governmentYtd=sum(contribs.filter {
            it.getString("status")=="Paid" && it.getString("period").startsWith(until.year.toString()) &&
                it.getString("period")<=YearMonth.from(until).toString() &&
                (!it.has("paymentDate")||LocalDate.parse(it.getString("paymentDate"))<=until)
        },"amount")
        val mp2Total=sum(entries(data,"mp2Deposits").filter {
            LocalDate.parse(it.getString("paymentDate"))<=until
        },"amount")
        return FinancialOverview(
            month=report,billsOutstanding=sum(outstanding,"amount"),
            overdueBills=outstanding.count { TrackerEngine.dueDate(it,month)<until },
            debtRemaining=remaining.values.fold(0L,Math::addExact),
            overdueDebts=debts.count { debt ->
                (remaining[debt.getString("id")] ?: 0L)>0 &&
                    debt.optBoolean("active") &&
                    debt.optString("dueDate").takeIf(String::isNotBlank)?.let {
                        LocalDate.parse(it)<until
                    }==true
            },
            savingsBalance=savedByGoal.values.fold(0L,Math::addExact),
            savingsRemaining=goals.fold(0L) { acc,goal ->
                Math.addExact(acc,maxOf(0L,goal.getLong("targetAmount") -
                    (savedByGoal[goal.getString("id")] ?: 0L)))
            },
            governmentYtdPaid=governmentYtd,
            mp2TotalSaved=mp2Total,
            contributionGaps=expectedGaps
        )
    }

    private fun quote(value:String) = "\"" + value.replace("\"","\"\"") + "\""
    private fun money(value:Long):String {
        val prefix=if(value<0)"-" else ""
        val abs=kotlin.math.abs(value)
        return prefix+(abs/100).toString()+"."+(abs%100).toString().padStart(2,'0')
    }
    private val monthlyHeader=listOf(
        "Month","Budget AED","Spent AED","Variance AED","Bills Paid AED",
        "Debt Repaid AED","Savings Added AED","Completed Remittances AED",
        "Pending Remittances AED","Remittance Fees AED","Own Account Transfers AED",
        "Government Contributions PHP","MP2 Saved PHP","Remittance Received PHP"
    )

    fun yearlyCsv(report: FinancialYear):String = buildString {
        append("\uFEFF")
        append(monthlyHeader.joinToString(",") { quote(it) })
        report.months.forEach { row ->
            val amounts=listOf(row.budget,row.spent,row.variance,row.billsPaid,row.debtRepaid,
                row.savingsAdded,row.remitted,row.pendingRemittances,row.remittanceFees,
                row.ownTransfers,row.governmentPaid,row.mp2Saved,row.remittanceReceived)
            append("\r\n")
            append(quote(row.month.toString()))
            amounts.forEach { append(",");append(money(it)) }
        }
    }

    fun monthlyCsv(report: FinancialMonth):String {
        val rows=listOf(
            Triple("Monthly budget","AED",report.budget),
            Triple("Actual spending","AED",report.spent),
            Triple("Budget variance","AED",report.variance),
            Triple("Bills paid","AED",report.billsPaid),
            Triple("Debt repaid","AED",report.debtRepaid),
            Triple("Savings added","AED",report.savingsAdded),
            Triple("Completed remittances","AED",report.remitted),
            Triple("Pending remittances","AED",report.pendingRemittances),
            Triple("Remittance fees","AED",report.remittanceFees),
            Triple("Own-account transfers","AED",report.ownTransfers),
            Triple("Government contributions","PHP",report.governmentPaid),
            Triple("MP2 saved","PHP",report.mp2Saved),
            Triple("Remittance received","PHP",report.remittanceReceived)
        )
        return "\uFEFF\"Metric\",\"Currency\",\"Amount\"\r\n"+
            rows.joinToString("\r\n"){(label,currency,amount)->
                quote(label)+","+quote(currency)+","+money(amount)
            }
    }
}
