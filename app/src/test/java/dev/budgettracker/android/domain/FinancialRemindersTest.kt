package dev.budgettracker.android.domain

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinancialRemindersTest {
    private val today=LocalDate.of(2026,10,26)
    private fun doc()=TrackerEngine.blank()

    @Test fun overdueBillAndDebtRemindersSortedBeforePendingTransfers() {
        val data=doc()
        data.put("recurringBills",JSONArray().put(JSONObject()
            .put("id","bill1").put("name","Rent").put("amount",185000L)
            .put("category","Housing").put("frequency","Monthly").put("dueDay",5)
            .put("startMonth","2026-01").put("active",true)))
        data.put("debts",JSONArray().put(JSONObject()
            .put("id","debt1").put("name","Loan").put("lender","Bank").put("originalAmount",50000L)
            .put("category","Other").put("startDate","2025-01-01")
            .put("dueDate","2026-10-25").put("active",true)))
        data.put("remittances",JSONArray().put(JSONObject()
            .put("id","rem1").put("recipient","Family").put("status","Pending")
            .put("sentAmount",12000L).put("transferDate","2026-10-19")))
        val reminders=FinancialReminders.generate(data,today)
        assertEquals(3,reminders.size)
        assertEquals("overdue",reminders[0].severity)
        assertEquals("overdue",reminders[1].severity)
        assertEquals("pending",reminders[2].severity)
        assertEquals("Remittances",reminders[2].destination)
        assertEquals(2,FinancialReminders.counts(reminders).overdue)
    }
    @Test fun billIsNotRemindedAfterRecordedPayment() {
        val data=doc()
        data.put("recurringBills",JSONArray().put(JSONObject()
            .put("id","bill").put("name","Rent").put("amount",185000L)
            .put("category","Housing").put("frequency","Monthly").put("dueDay",26)
            .put("startMonth","2026-01").put("active",true)))
        assertEquals("due-soon",FinancialReminders.generate(data,today).single().severity)
        data.put("billPayments",JSONArray().put(JSONObject()
            .put("id","payment").put("billId","bill").put("period","2026-10")
            .put("amount",185000L).put("paymentDate","2026-10-25")))
        assertTrue(FinancialReminders.generate(data,today).isEmpty())
    }
    @Test fun contributionGapIsNotAutomaticallyAnOverdueMissedContribution() {
        val data=doc()
        data.put("governmentAccounts",JSONArray().put(JSONObject()
            .put("id","a").put("provider","SSS").put("memberType","OFW")
            .put("frequency","Quarterly").put("active",true)))
        val reminders=FinancialReminders.generate(data,today)
        val gaps=reminders.first {it.id=="contribution:SSS:gaps"}
        assertEquals("pending",gaps.severity)
        assertTrue(gaps.detail.contains("not automatically missed"))
        assertFalse(reminders.any{it.severity=="overdue"})
        data.put("governmentContributions",JSONArray().put(JSONObject()
            .put("id","marked").put("accountId","a").put("period","2026-09")
            .put("amount",0).put("status","Missed")))
        val withMissed=FinancialReminders.generate(data,today)
        assertTrue(withMissed.any{it.severity=="overdue" && it.title.contains("missed")})
    }
    @Test fun reminderAmountsNeverMixCurrencies() {
        val data=doc()
        data.put("savingsGoals",JSONArray().put(JSONObject()
            .put("id","savings").put("name","Emergency").put("startDate","2026-01-01")
            .put("targetDate","2026-10-27").put("targetAmount",50000L)
            .put("monthlyTarget",10000L).put("active",true)))
        data.put("mp2Accounts",JSONArray().put(JSONObject()
            .put("id","mp2").put("name","MP2").put("initialPaymentDate","2021-10-30")
            .put("monthlyTarget",20000L).put("active",true)))
        val reminders=FinancialReminders.generate(data,today)
        assertTrue(reminders.any{it.source=="Savings" && it.currency==Currency.AED})
        assertTrue(reminders.any{it.source=="MP2" && it.currency==Currency.PHP})
        assertTrue(reminders.any{it.source=="MP2" && it.id.endsWith("maturity")})
    }
    @Test fun futureTransactionsDoNotSatisfyCurrentMonthlyTargets() {
        val data=doc()
        data.put("mp2Accounts",JSONArray().put(JSONObject()
            .put("id","mp2").put("name","MP2").put("monthlyTarget",20000L).put("active",true)))
        data.put("mp2Deposits",JSONArray().put(JSONObject()
            .put("id","future").put("accountId","mp2").put("amount",50000L)
            .put("paymentDate","2026-10-31")))
        val reminders=FinancialReminders.generate(data,today)
        assertTrue(reminders.any{it.id=="mp2:mp2:monthly:2026-10"})
    }
}
