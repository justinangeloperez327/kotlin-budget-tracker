package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject

/** Source-shaped, privacy-preserving cross-record consistency checks. */
data class AuditIssue(val code: String, val detail: String)
data class AuditResult(val issues: List<AuditIssue>,val counts: Map<String,Int>) {
    val passed get() = issues.isEmpty()
}

object WorkspaceAudit {
    private val maxCounts=linkedMapOf(
        "expenses" to 2000,"recurringBills" to 300,"billPayments" to 5000,
        "debts" to 500,"debtPayments" to 5000,"savingsGoals" to 500,
        "savingsDeposits" to 10000,"remittances" to 10000,
        "governmentAccounts" to 12,"governmentContributions" to 1200,
        "mp2Accounts" to 24,"mp2Deposits" to 5000
    )
    val arrays=maxCounts.keys.toList()
    private val uuid=Regex("(?i)^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$")
    private fun date(s:String)=runCatching{LocalDate.parse(s).year in 1900..2199}.getOrDefault(false)
    private fun month(s:String)=runCatching{YearMonth.parse(s).year in 1900..2199}.getOrDefault(false)
    fun inspect(data:JSONObject):AuditResult {
        val issues=mutableListOf<AuditIssue>()
        fun fail(code:String,detail:String) { if(issues.size<80)issues+=AuditIssue(code,detail) }
        val tables=linkedMapOf<String,List<JSONObject>>()
        maxCounts.forEach{ (name,cap) ->
            val arr=data.optJSONArray(name)
            if(arr==null) { fail("missing-module",name+" must be an array");tables[name]=emptyList() }
            else {
                if(arr.length()>cap)fail("limit",name+" exceeds permitted number of rows")
                tables[name]=(0 until minOf(cap+1,arr.length())).mapNotNull { index ->
                    val row=arr.optJSONObject(index)
                    if(row==null)fail("row-type",name+" contains a non-object record")
                    row
                }
            }
        }
        fun rows(key:String)=tables[key].orEmpty()
        fun requiredText(record:JSONObject,key:String,cap:Int){
            val text=record.optString(key)
            if(text.trim().isEmpty() || text.length>cap)
                fail("text",key+" is missing or exceeds the permitted length")
        }
        fun optionalText(record:JSONObject,key:String,cap:Int) {
            if(record.has(key))requiredText(record,key,cap)
        }
        fun active(record:JSONObject){
            if(record.opt("active") !is Boolean)
                fail("active","Account/record active flag is missing or invalid")
        }
        val index=linkedMapOf<String,Map<String,JSONObject>>()
        tables.forEach { (table,all) ->
            val map=linkedMapOf<String,JSONObject>()
            all.forEach { row ->
                val id=row.optString("id")
                if(!uuid.matches(id))fail("uuid",table+" has an invalid record ID")
                if(map.put(id,row)!=null)fail("duplicate-id",table+" contains duplicate record IDs")
            }
            index[table]=map
        }
        fun checkMoney(row:JSONObject,field:String,zero:Boolean=false):Long? {
            val raw=row.opt(field)
            val value=when(raw){is Int->raw.toLong();is Long->raw;else->null}
            if(value==null || value !in (if(zero)0L else 1L)..BudgetFeatures.MAX_MINOR) {
                fail("money",field+" is not a valid minor-unit amount");return null
            }
            return value
        }
        fun optionalTarget(record:JSONObject){
            if(record.has("monthlyTarget"))checkMoney(record,"monthlyTarget",true)
        }
        fun parent(table:String,key:String,row:JSONObject):JSONObject? =
            index[table]?.get(row.optString(key)).also {
                if(it==null)fail("orphan",key+" references a missing "+table+" record")
            }
        val budgets=data.optJSONObject("budgets")
        val allocated=data.optJSONObject("categoryBudgets")
        if(budgets==null||allocated==null)fail("budget-shape","Budget objects are required")
        val periods=mutableSetOf<String>()
        budgets?.keys()?.forEach { key ->
            if(!month(key))fail("month","Invalid budget month")
            periods+=key; checkMoney(budgets,key,true)
        }
        var allocationCount=0
        allocated?.keys()?.forEach { key ->
            if(!month(key))fail("month","Invalid allocation month")
            periods+=key
            val rows=allocated.optJSONObject(key)
            if(rows==null)fail("budget-shape","Category allocations must be an object")
            else rows.keys().forEach { name ->
                allocationCount++
                if(Category.entries.none{it.label==name})fail("category","Unknown category")
                checkMoney(rows,name,true)
            }
        }
        if(periods.size>600||allocationCount>4200)fail("budget-limit","Too many monthly budget entries")
        rows("expenses").forEach { e ->
            if(!date(e.optString("date")))fail("date","Invalid expense date")
            if(Category.entries.none{it.label==e.optString("category")})fail("category","Unknown expense category")
            if(e.optString("description").trim().isEmpty()||e.optString("description").length>120)
                fail("description","Expense description invalid")
            checkMoney(e,"amount")
        }
        val owners=mutableSetOf<String>()
        fun link(expenseId:String,amount:Long?,category:String,date:String) {
            if(!owners.add(expenseId))fail("duplicate-owner","Expense is linked to multiple payments")
            val e=index["expenses"]?.get(expenseId)
            if(e==null)fail("missing-expense","Payment has no linked expense")
            else if(amount==null || e.optLong("amount",Long.MIN_VALUE)!=amount ||
                e.optString("category")!=category || e.optString("date")!=date)
                fail("expense-mismatch","Payment and linked expense amount, category or date differ")
        }
        rows("recurringBills").forEach {
            requiredText(it,"name",100);active(it);optionalText(it,"notes",500)
            if(Category.entries.none { category->category.label==it.optString("category") })
                fail("category","Bill has invalid category")
            checkMoney(it,"amount")
            if(!month(it.optString("startMonth")))fail("month","Invalid bill start month")
            if(it.has("endMonth")&&!month(it.optString("endMonth")))fail("month","Invalid bill end month")
            if(it.has("endMonth")&&month(it.optString("endMonth"))&&
                it.optString("endMonth")<it.optString("startMonth"))
                fail("month","Bill ends before it starts")
            if(it.optString("frequency") !in listOf("Monthly","Quarterly","Yearly") ||
               it.optInt("dueDay") !in 1..31)fail("recurrence","Invalid bill schedule")
        }
        val paidPeriods=mutableSetOf<String>()
        rows("billPayments").forEach { p ->
            val bill=parent("recurringBills","billId",p)
            val period=p.optString("period")
            if(!month(period)||!date(p.optString("paymentDate")))fail("date","Invalid bill payment date/period")
            optionalText(p,"referenceNumber",80);optionalText(p,"notes",500)
            if(!paidPeriods.add(p.optString("billId")+":"+period))
                fail("duplicate-payment","Bill period was paid more than once")
            if(bill!=null) {
                if(month(period)&&!runCatching{TrackerEngine.occurs(bill,YearMonth.parse(period))}.getOrDefault(false))
                    fail("schedule-mismatch","Bill payment falls outside recurrence")
                link(p.optString("expenseId"),checkMoney(p,"amount"),bill.optString("category"),
                    p.optString("paymentDate"))
            }
        }
        val debtPaid=mutableMapOf<String,Long>()
        rows("debts").forEach {
            requiredText(it,"name",120);requiredText(it,"lender",120);active(it)
            optionalText(it,"notes",500);optionalTarget(it)
            checkMoney(it,"originalAmount")
            if(Category.entries.none { category->category.label==it.optString("category") })
                fail("category","Debt has invalid category")
            if(!date(it.optString("startDate")))fail("date","Debt has invalid start date")
            if(it.has("dueDate") && (!date(it.optString("dueDate")) ||
                it.optString("dueDate")<it.optString("startDate")))
                fail("date","Debt due date is invalid")
        }
        rows("debtPayments").forEach { p ->
            val d=parent("debts","debtId",p)
            val amount=checkMoney(p,"amount")
            if(!date(p.optString("paymentDate")))fail("date","Invalid debt payment date")
            optionalText(p,"referenceNumber",80);optionalText(p,"notes",500)
            if(d!=null) {
                link(p.optString("expenseId"),amount,d.optString("category"),p.optString("paymentDate"))
                if(amount!=null)debtPaid[p.optString("debtId")]=
                    Math.addExact(debtPaid[p.optString("debtId")]?:0L,amount)
            }
        }
        debtPaid.forEach { (id,sum) ->
            if(sum>(index["debts"]?.get(id)?.optLong("originalAmount")?:0L))
                fail("overpaid","Debt repayment exceeds original balance")
        }
        val savingsPaid=mutableMapOf<String,Long>()
        rows("savingsGoals").forEach {
            requiredText(it,"name",120);active(it);optionalText(it,"destination",120)
            optionalText(it,"notes",500);optionalTarget(it)
            checkMoney(it,"targetAmount")
            if(!date(it.optString("startDate")))fail("date","Savings goal start date invalid")
            if(it.has("targetDate") && (!date(it.optString("targetDate")) ||
                it.optString("targetDate")<it.optString("startDate")))
                fail("date","Savings goal target date invalid")
        }
        rows("savingsDeposits").forEach { p ->
            parent("savingsGoals","goalId",p)
            val amount=checkMoney(p,"amount")
            if(!date(p.optString("depositDate")))fail("date","Invalid savings date")
            optionalText(p,"referenceNumber",80);optionalText(p,"notes",500)
            if(amount!=null)savingsPaid[p.optString("goalId")]=
                Math.addExact(savingsPaid[p.optString("goalId")]?:0L,amount)
        }
        savingsPaid.forEach { (id,sum) ->
            if(sum>(index["savingsGoals"]?.get(id)?.optLong("targetAmount")?:0L))
                fail("overfilled","Savings deposit exceeds goal")
        }
        val providers=mutableSetOf<String>()
        rows("governmentAccounts").forEach { a ->
            active(a);optionalText(a,"accountIdentifier",40);optionalTarget(a)
            if(a.optString("frequency") !in listOf("Monthly","Quarterly"))
                fail("frequency","Government contribution frequency invalid")
            if(a.optString("memberType") !in PhilippineEngine.memberTypes[a.optString("provider")].orEmpty())
                fail("member-type","Government member type invalid")
            if(a.optString("provider") !in PhilippineEngine.providers ||
                !providers.add(a.optString("provider")))fail("provider","Duplicate/unknown government provider")
        }
        val paidContributionPeriods=mutableSetOf<String>()
        rows("governmentContributions").forEach { p ->
            parent("governmentAccounts","accountId",p)
            if(!paidContributionPeriods.add(p.optString("accountId")+":"+p.optString("period")))
                fail("duplicate-period","Duplicate government period")
            if(!month(p.optString("period")))fail("month","Invalid contribution period")
            if(p.optString("status") !in listOf("Paid","Pending","Missed","Not Required"))
                fail("status","Government contribution status is invalid")
            optionalText(p,"referenceNumber",80);optionalText(p,"notes",500)
            if(p.optString("status")=="Paid"&&!date(p.optString("paymentDate")))
                fail("date","Paid contribution has invalid payment date")
            checkMoney(p,"amount",true)
        }
        val mp2Numbers=mutableSetOf<String>()
        rows("mp2Accounts").forEach { a ->
            requiredText(a,"name",80);active(a);optionalText(a,"accountNumber",40)
            optionalTarget(a)
            if(a.optString("dividendOption") !in listOf("Annual","Compounded"))
                fail("dividend","MP2 dividend selection invalid")
            if(a.has("initialPaymentDate")&&!date(a.optString("initialPaymentDate")))
                fail("date","MP2 initial payment date invalid")
            val number=a.optString("accountNumber")
            if(number.isNotBlank()&&!mp2Numbers.add(number))
                fail("duplicate-mp2","MP2 account number duplicated")
        }
        rows("mp2Deposits").forEach { p ->
            parent("mp2Accounts","accountId",p)
            checkMoney(p,"amount")
            if(!date(p.optString("paymentDate")))fail("date","Invalid MP2 deposit date")
            optionalText(p,"referenceNumber",80);optionalText(p,"notes",500)
        }
        rows("remittances").forEach { r ->
            requiredText(r,"recipient",120)
            optionalText(r,"provider",120);optionalText(r,"referenceNumber",80);optionalText(r,"notes",500)
            if(r.optString("destinationType") !in listOf("Own account","Family / person"))
                fail("destination","Remittance destination invalid")
            if(r.optString("status") !in listOf("Pending","Completed"))
                fail("status","Remittance status invalid")
            if(r.opt("principalAsExpense") !is Boolean)
                fail("principal","Remittance consumption-expense flag invalid")
            if(Category.entries.none { category->category.label==r.optString("category") })
                fail("category","Remittance expense category invalid")
            val sent=checkMoney(r,"sentAmount")
            val fee=checkMoney(r,"feeAmount",true)
            if(!date(r.optString("transferDate")))fail("date","Invalid remittance date")
            if(r.optString("status")=="Completed")checkMoney(r,"receivedAmount")
            if(sent!=null&&fee!=null&&sent+fee>BudgetFeatures.MAX_MINOR)
                fail("money","Remittance principal plus fee exceeds maximum")
            if(r.has("receivedAmount")&&r.optString("status")!="Completed")
                checkMoney(r,"receivedAmount")
            val amount=if(sent!=null&&fee!=null)
                Math.addExact(if(r.optBoolean("principalAsExpense"))sent else 0L,fee)
                else null
            val eid=r.optString("expenseId")
            if(amount==0L&&eid.isNotBlank())fail("unexpected-link","Zero-cost remittance has expense")
            if(amount!=null&&amount>0L)link(eid,amount,r.optString("category"),r.optString("transferDate"))
        }
        return AuditResult(issues.toList(),arrays.keys.associateWith{rows(it).size})
    }
}
