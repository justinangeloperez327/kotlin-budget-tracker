package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Informational in-app reminders; no notifications or automatic payment instructions. */
data class FinancialReminder(
    val id: String,
    val source: String,
    val title: String,
    val detail: String,
    val destination: String,
    val severity: String,
    val dueDate: LocalDate? = null,
    val amount: Long? = null,
    val currency: Currency? = null
)
data class ReminderCounts(val total:Int,val overdue:Int,val dueSoon:Int,val pending:Int,val info:Int)

object FinancialReminders {
    private fun rows(data:JSONObject,key:String)=TrackerEngine.rows(data,key)
    private fun paid(rows:List<JSONObject>,foreign:String,id:String,dateKey:String,today:LocalDate):Long =
        rows.filter{
            it.getString(foreign)==id && LocalDate.parse(it.getString(dateKey))<=today
        }.fold(0L){sum,row->Math.addExact(sum,row.getLong("amount"))}

    fun counts(reminders:List<FinancialReminder>)=ReminderCounts(
        total=reminders.size,
        overdue=reminders.count{it.severity=="overdue"},
        dueSoon=reminders.count{it.severity=="due-soon"},
        pending=reminders.count{it.severity=="pending"},
        info=reminders.count{it.severity=="info"}
    )

    fun generate(data:JSONObject,today:LocalDate=LocalDate.now()):List<FinancialReminder>{
        val results=mutableListOf<FinancialReminder>()
        val month=YearMonth.from(today)
        val period=month.toString()
        for(bill in rows(data,"recurringBills")){
            if(!bill.optBoolean("active") || !TrackerEngine.occurs(bill,month))continue
            val hasPayment=rows(data,"billPayments").any {
                it.getString("billId")==bill.getString("id") &&
                    it.getString("period")==period &&
                    LocalDate.parse(it.getString("paymentDate"))<=today
            }
            if(hasPayment)continue
            val due=TrackerEngine.dueDate(bill,month)
            val days=ChronoUnit.DAYS.between(today,due)
            if(days<0 || days<=7L){
                val overdue=days<0
                results+=FinancialReminder(
                    id="bill:"+bill.getString("id")+":"+period,source="Bill",
                    title=bill.getString("name")+(if(overdue)" is overdue" else " is due soon"),
                    detail="Due "+due,destination="Bills & recurring",
                    severity=if(overdue)"overdue" else "due-soon",
                    dueDate=due,amount=bill.getLong("amount"),currency=Currency.AED
                )
            }
        }
        val debtPayments=rows(data,"debtPayments")
        for(debt in rows(data,"debts")){
            if(!debt.optBoolean("active") || debt.optString("dueDate").isBlank() ||
                LocalDate.parse(debt.getString("startDate"))>today)continue
            val remaining=maxOf(0L,debt.getLong("originalAmount")-
                paid(debtPayments,"debtId",debt.getString("id"),"paymentDate",today))
            if(remaining==0L)continue
            val due=LocalDate.parse(debt.getString("dueDate"))
            val days=ChronoUnit.DAYS.between(today,due)
            if(days<0 || days<=30L){
                results+=FinancialReminder(
                    id="debt:"+debt.getString("id"),source="Debt",
                    title=debt.getString("name")+(if(days<0)" is overdue" else " is due soon"),
                    detail=if(days<0)"Due date was "+due else "Due "+due+" · "+days+" days remaining",
                    destination="Debt / Utang",severity=if(days<0)"overdue" else "due-soon",
                    dueDate=due,amount=remaining,currency=Currency.AED
                )
            }
        }

        val contributions=rows(data,"governmentContributions")
        for(account in rows(data,"governmentAccounts")){
            if(!account.optBoolean("active"))continue
            val id=account.getString("id")
            val provider=account.getString("provider")
            val label=when(provider) {
                "PHILHEALTH"->"PhilHealth"
                "PAGIBIG"->"Pag-IBIG"
                else->"SSS"
            }
            val destination=label
            val relevant=contributions.filter{it.getString("accountId")==id}
            val expected=PhilippineEngine.expectedPeriods(account,today.year)
            val current=expected.contains(period)
            val gaps=expected.filter { prior -> prior<period && relevant.none {
                it.getString("period")==prior
            } }
            if(gaps.isNotEmpty())results+=FinancialReminder(
                "contribution:"+provider+":gaps","Contribution",
                label+" contribution gaps",
                gaps.size.toString()+" prior expected periods have no record; not automatically missed",
                destination,"pending",LocalDate.parse(gaps.last()+"-01"),currency=Currency.PHP
            )
            for(row in relevant){
                val p=row.getString("period")
                if(p>period || !p.startsWith(today.year.toString()))continue
                when(row.getString("status")){
                    "Missed"->results+=FinancialReminder(
                        "contribution:"+provider+":missed:"+p,"Contribution",
                        label+" contribution marked missed",p+" needs review",
                        destination,"overdue",LocalDate.parse(p+"-01"),currency=Currency.PHP)
                    "Pending"->results+=FinancialReminder(
                        "contribution:"+provider+":pending:"+p,"Contribution",
                        label+" contribution is pending",p+" awaits completion",
                        destination,"pending",LocalDate.parse(p+"-01"),currency=Currency.PHP)
                }
            }
            if(current && relevant.none{it.getString("period")==period}){
                results+=FinancialReminder(
                    "contribution:"+provider+":current:"+period,"Contribution",
                    label+" current period has no record",period+" expected; no entry recorded",
                    destination,"info",currency=Currency.PHP)
            }
        }

        val savingsDeposits=rows(data,"savingsDeposits")
        for(goal in rows(data,"savingsGoals")){
            if(!goal.optBoolean("active") || LocalDate.parse(goal.getString("startDate"))>today)continue
            val id=goal.getString("id")
            val remaining=maxOf(0L,goal.getLong("targetAmount")-
                paid(savingsDeposits,"goalId",id,"depositDate",today))
            if(remaining==0L)continue
            val dueText=goal.optString("targetDate")
            if(dueText.isNotBlank()){
                val due=LocalDate.parse(dueText)
                val days=ChronoUnit.DAYS.between(today,due)
                if(days<0 || days<=30L) results+=FinancialReminder(
                    "savings:"+id+":deadline","Savings",
                    goal.getString("name")+(if(days<0)" target date passed" else " target date approaching"),
                    "Target "+due,"Savings goals",
                    if(days<0)"info" else "due-soon",due,remaining,Currency.AED)
            }
            val target=goal.optLong("monthlyTarget")
            if(today.dayOfMonth>=20 && target>0) {
                val saved=savingsDeposits.filter {
                    it.getString("goalId")==id &&
                        it.getString("depositDate").startsWith(period) &&
                        LocalDate.parse(it.getString("depositDate"))<=today
                }.fold(0L){s,item->Math.addExact(s,item.getLong("amount"))}
                if(saved<target)results+=FinancialReminder(
                    "savings:"+id+":monthly:"+period,"Savings",
                    goal.getString("name")+" monthly target not complete",
                    "Additional planned saving this month","Savings goals","info",
                    amount=target-saved,currency=Currency.AED)
            }
        }
        val mp2Deposits=rows(data,"mp2Deposits")
        for(account in rows(data,"mp2Accounts")){
            if(!account.optBoolean("active"))continue
            val id=account.getString("id")
            val target=account.optLong("monthlyTarget")
            if(today.dayOfMonth>=20 && target>0) {
                val saved=mp2Deposits.filter {
                    it.getString("accountId")==id &&
                        it.getString("paymentDate").startsWith(period) &&
                        LocalDate.parse(it.getString("paymentDate"))<=today
                }.fold(0L){s,row->Math.addExact(s,row.getLong("amount"))}
                if(saved<target) results+=FinancialReminder(
                    "mp2:"+id+":monthly:"+period,"MP2",account.getString("name")+
                        " monthly target not complete",
                    "Additional planned MP2 deposit","MP2","info",amount=target-saved,currency=Currency.PHP)
            }
            val maturity=PhilippineEngine.maturity(account.optString("initialPaymentDate"))
            if(maturity!=null){
                val due=LocalDate.parse(maturity)
                val days=ChronoUnit.DAYS.between(today,due)
                if(days<0 || days<=60L) results+=FinancialReminder(
                    "mp2:"+id+":maturity","MP2",
                    account.getString("name")+
                        (if(days<0)" has reached maturity" else " maturity approaching"),
                    "Estimated maturity "+due,"MP2",
                    if(days<0)"info" else "due-soon",due,currency=Currency.PHP)
            }
        }
        for(remittance in rows(data,"remittances")){
            if(remittance.getString("status")!="Pending")continue
            results+=FinancialReminder(
                "remittance:"+remittance.getString("id"),"Remittance",
                "Remittance to "+remittance.getString("recipient")+" is pending",
                "Started "+remittance.getString("transferDate"),"Remittances","pending",
                LocalDate.parse(remittance.getString("transferDate")),
                remittance.getLong("sentAmount"),Currency.AED)
        }
        fun priority(value:String)=when(value) {
            "overdue"->0;"due-soon"->1;"pending"->2;else->3
        }
        return results.sortedWith(compareBy<FinancialReminder>(
            {priority(it.severity)},{it.dueDate ?: LocalDate.MAX},{it.title}
        ))
    }
}
