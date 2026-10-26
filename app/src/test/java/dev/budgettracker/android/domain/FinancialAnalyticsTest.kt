package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinancialAnalyticsTest {
    private val today=LocalDate.of(2026,10,26)
    private val month=YearMonth.of(2026,10)
    private fun data():JSONObject=TrackerEngine.blank().apply {
        put("budgets",JSONObject().put("2026-09",200000).put("2026-10",300000)
            .put("2026-11",400000))
        put("categoryBudgets",JSONObject())
        put("expenses",JSONArray()
            .put(JSONObject().put("id","manual").put("amount",130000)
                .put("category","Housing").put("description","Rent").put("date","2026-10-05"))
            .put(JSONObject().put("id","future").put("amount",50000)
                .put("category","Food").put("description","Future").put("date","2026-10-30"))
            .put(JSONObject().put("id","old").put("amount",20000)
                .put("category","Food").put("description","Old").put("date","2026-09-15")))
    }
    private fun record(id:String,amount:Long,date:String,extra:(JSONObject)->Unit={}):JSONObject =
        JSONObject().put("id",id).put("amount",amount).put("paymentDate",date).also(extra)

    @Test fun reportingCutoffUsesTodayOrMonthEndWhicheverEarlier() {
        assertEquals("2026-09-30",FinancialAnalytics.cutoff(YearMonth.of(2026,9),today).toString())
        assertEquals("2026-10-26",FinancialAnalytics.cutoff(month,today).toString())
        assertEquals("2026-10-26",FinancialAnalytics.cutoff(YearMonth.of(2026,11),today).toString())
        val r=FinancialAnalytics.monthly(data(),month,today)
        assertEquals(300000,r.budget)
        assertEquals(130000,r.spent)
        assertEquals(170000,r.variance)
        assertEquals(130000L,r.categorySpent[Category.HOUSING])
        assertEquals(0L,r.categorySpent[Category.FOOD])
        assertEquals(130000.0/300000*100,r.usedRate,0.00001)
        assertEquals(0L,FinancialAnalytics.monthly(data(),YearMonth.of(2026,11),today).spent)
    }
    @Test fun linkedBillAndDebtExpenseAlreadyIncludedAndNotDoubleCounted() {
        val json=data()
        json.getJSONArray("expenses")
            .put(JSONObject().put("id","linked-bill").put("amount",10000)
                .put("category","Housing").put("date","2026-10-06")
                .put("description","Bill"))
            .put(JSONObject().put("id","linked-debt").put("amount",4000)
                .put("category","Other").put("date","2026-10-06")
                .put("description","Debt"))
        json.put("billPayments",JSONArray().put(record("bp",10000,"2026-10-06") {
            it.put("billId","b1").put("expenseId","linked-bill").put("period","2026-10")
        }))
        json.put("debtPayments",JSONArray().put(record("dp",4000,"2026-10-06") {
            it.put("debtId","d1").put("expenseId","linked-debt")
        }))
        val r=FinancialAnalytics.monthly(json,month,today)
        assertEquals(144000,r.spent)
        assertEquals(10000,r.billsPaid)
        assertEquals(4000,r.debtRepaid)
        assertEquals(156000,r.variance)
    }
    @Test fun remittanceOwnerTransferDoesNotDoubleCountAsSpendingOrMixPhp() {
        val json=data()
        json.put("remittances",JSONArray()
            .put(JSONObject().put("id","transfer").put("recipient","Own account")
                .put("sentAmount",100000).put("feeAmount",150).put("receivedAmount",1600000)
                .put("transferDate","2026-10-08").put("destinationType","Own account")
                .put("status","Completed").put("principalAsExpense",false).put("category","Other"))
            .put(JSONObject().put("id","pending").put("recipient","Family")
                .put("sentAmount",20000).put("feeAmount",100).put("transferDate","2026-10-13")
                .put("destinationType","Family / person")
                .put("status","Pending").put("principalAsExpense",true).put("category","Other")))
        val r=FinancialAnalytics.monthly(json,month,today)
        assertEquals(100000,r.remitted)
        assertEquals(20000,r.pendingRemittances)
        assertEquals(250,r.remittanceFees)
        assertEquals(100000,r.ownTransfers)
        assertEquals(0,r.supportRemitted)
        assertEquals(1600000,r.remittanceReceived)
        assertEquals(130000,r.spent) // only recorded linked expenses affect spending
    }
    @Test fun phpTotalsAreSeparateAndBasedOnStatusAndCutoff() {
        val json=data()
        json.put("governmentContributions",JSONArray()
            .put(JSONObject().put("id","a").put("accountId","g").put("period","2026-10")
                .put("status","Paid").put("amount",20000).put("paymentDate","2026-10-20"))
            .put(JSONObject().put("id","b").put("accountId","g").put("period","2026-10")
                .put("status","Pending").put("amount",30000))
            .put(JSONObject().put("id","c").put("accountId","g").put("period","2026-10")
                .put("status","Paid").put("amount",50000).put("paymentDate","2026-10-31")))
        json.put("mp2Deposits",JSONArray()
            .put(record("mp2a",12000,"2026-10-21") {it.put("accountId","mp2")})
            .put(record("mp2future",8000,"2026-10-27") {it.put("accountId","mp2")}))
        val r=FinancialAnalytics.monthly(json,month,today)
        assertEquals(20000,r.governmentPaid)
        assertEquals(12000,r.mp2Saved)
        assertEquals(130000,r.spent)
    }
    @Test fun yearlyStopsAtCurrentMonthAndDoesNotCountFutureMonths() {
        val report=FinancialAnalytics.yearly(data(),2026,today)
        assertEquals(10,report.months.size)
        assertEquals(500000,report.totalBudget) // months Jan-Sept Oct, not Nov
        assertEquals(150000,report.totalSpent)
        assertEquals(350000,report.netVariance)
        assertEquals(0,FinancialAnalytics.yearly(data(),2027,today).months.size)
        assertEquals(12,FinancialAnalytics.yearly(data(),2025,today).months.size)
        assertEquals(listOf(2026),FinancialAnalytics.availableYears(data(),today).filter{it==2026})
    }
    @Test fun exportsLabelCurrencySeparatelyAndUseTwoDecimals() {
        val monthReport=FinancialAnalytics.monthly(data(),month,today)
        val monthCsv=FinancialAnalytics.monthlyCsv(monthReport)
        assertTrue(monthCsv.startsWith("\uFEFF"))
        assertTrue(monthCsv.contains("\"Monthly budget\",\"AED\",3000.00"))
        assertTrue(monthCsv.contains("\"Actual spending\",\"AED\",1300.00"))
        assertTrue(monthCsv.contains("\"Government contributions\",\"PHP\",0.00"))
        val yearly=FinancialAnalytics.yearlyCsv(FinancialAnalytics.yearly(data(),2026,today))
        assertTrue(yearly.contains("\"Government Contributions PHP\""))
        assertTrue(yearly.contains("\"MP2 Saved PHP\""))
        assertTrue(yearly.contains("\"2026-10\",3000.00,1300.00,1700.00"))
        assertFalse(yearly.contains("2026-11"))
    }
    @Test fun overviewDoesNotUseFuturePaymentsOrContributions() {
        val json=data()
        json.put("recurringBills",JSONArray().put(JSONObject().put("id","bill")
            .put("name","Subscription").put("amount",40000).put("frequency","Monthly")
            .put("dueDay",15).put("startMonth","2026-01").put("active",true)))
        json.put("billPayments",JSONArray().put(record("futureBill",40000,"2026-10-30") {
            it.put("billId","bill").put("period","2026-10").put("expenseId","x")
        }))
        val r=FinancialAnalytics.overview(json,month,today)
        assertEquals(40000,r.billsOutstanding)
        assertEquals(1,r.overdueBills)
    }

    @Test fun historicalCategoryPerformanceTracksActiveUnderAndOverMonths() {
        val json=data()
        json.put("categoryBudgets",JSONObject()
            .put("2026-09",JSONObject().put("Food",25000L))
            .put("2026-10",JSONObject().put("Housing",100000L)
                .put("Food",15000L)))
        json.getJSONArray("expenses").put(JSONObject()
            .put("id","category-october-food").put("description","Groceries")
            .put("amount",30000L).put("date","2026-10-15").put("category","Food"))
        val history=FinancialAnalytics.categoryHistory(json,
            FinancialAnalytics.yearly(json,2026,today))
        val food=history.single { it.category==Category.FOOD }
        assertEquals(2,food.activeMonths)
        assertEquals(25000L+15000L,food.budget)
        assertEquals(50000L,food.actual)
        assertEquals(1,food.underBudgetMonths)
        assertEquals(1,food.overBudgetMonths)
        val housing=history.single{it.category==Category.HOUSING}
        assertEquals(1,housing.activeMonths)
        assertEquals(100000L,housing.budget)
        assertEquals(130000L,housing.actual)
        assertEquals(1,housing.overBudgetMonths)
        assertEquals(-30000L,housing.variance)
    }

}
