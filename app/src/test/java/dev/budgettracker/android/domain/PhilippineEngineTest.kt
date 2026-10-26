package dev.budgettracker.android.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhilippineEngineTest {
    private fun blank()=TrackerEngine.blank()
    private fun remittance(
        type:String="Own account", support:Boolean=false, fee:Long=250,
        status:String="Completed"
    ) = JSONObject()
        .put("recipient","Savings account").put("destinationType",type)
        .put("sentAmount",100000L).put("feeAmount",fee).put("status",status)
        .put("transferDate","2026-10-19").put("principalAsExpense",support)
        .put("category","Other").put("receivedAmount",1540000L)
    private fun account(provider:String="SSS")=JSONObject()
        .put("memberType",PhilippineEngine.memberTypes.getValue(provider).first())
        .put("frequency","Quarterly").put("active",true)
    private fun mp2()=JSONObject()
        .put("name","MP2 Savings").put("accountNumber","111222")
        .put("dividendOption","Compounded").put("active",true)

    @Test fun ownAccountPrincipalNeverBecomesExpenseOnlyFeeDoes() {
        val result=PhilippineEngine.apply(blank(),PhilippineCommand.SaveRemittance(remittance()))
        assertEquals(1,PhilippineEngine.rows(result,"remittances").size)
        val expense=PhilippineEngine.rows(result,"expenses").single()
        assertEquals(250L,expense.getLong("amount"))
        assertEquals(1540000L,PhilippineEngine.rows(result,"remittances").single().getLong("receivedAmount"))
        assertEquals(15.4,PhilippineEngine.effectiveRate(PhilippineEngine.rows(result,"remittances").single())!!,0.00001)
    }

    @Test fun zeroFeeOwnTransferProducesNoExpenseAndNoCrossCurrencyAddition() {
        val result=PhilippineEngine.apply(blank(),PhilippineCommand.SaveRemittance(
            remittance(fee=0)))
        assertEquals(0,PhilippineEngine.rows(result,"expenses").size)
        assertEquals(100000L,PhilippineEngine.rows(result,"remittances").single().getLong("sentAmount"))
        assertThrows(IllegalArgumentException::class.java) {
            Money(Currency.AED,100).plus(Money(Currency.PHP,200))
        }
    }

    @Test fun familySupportExpenseIncludesPrincipalAndFeeExactlyOnce() {
        val first=PhilippineEngine.apply(blank(),PhilippineCommand.SaveRemittance(
            remittance("Family / person",true)))
        val row=PhilippineEngine.rows(first,"remittances").single()
        val originalId=row.getString("expenseId")
        assertEquals(100250L,PhilippineEngine.rows(first,"expenses").single().getLong("amount"))
        val modified=PhilippineEngine.apply(first,PhilippineCommand.SaveRemittance(
            remittance("Family / person",true,500),row.getString("id")))
        assertEquals(originalId,PhilippineEngine.rows(modified,"expenses").single().getString("id"))
        assertEquals(100500L,PhilippineEngine.rows(modified,"expenses").single().getLong("amount"))
        val removed=PhilippineEngine.apply(modified,PhilippineCommand.RemoveRemittance(row.getString("id")))
        assertTrue(PhilippineEngine.rows(removed,"expenses").isEmpty())
        assertTrue(PhilippineEngine.rows(removed,"remittances").isEmpty())
    }

    @Test fun completedTransferMustHavePhpReceived() {
        val f=remittance()
        f.remove("receivedAmount")
        assertThrows(IllegalArgumentException::class.java) {
            PhilippineEngine.apply(blank(),PhilippineCommand.SaveRemittance(f))
        }
        val pending=PhilippineEngine.apply(blank(),PhilippineCommand.SaveRemittance(
            remittance(status="Pending").apply{remove("receivedAmount")}))
        assertNull(PhilippineEngine.effectiveRate(PhilippineEngine.rows(pending,"remittances").single()))
    }

    @Test fun onlyExplicitMissedIsMissedNeverMissingRecords() {
        val saved=PhilippineEngine.apply(blank(),PhilippineCommand.SaveGovernmentAccount(
            "SSS",account()))
        val id=PhilippineEngine.rows(saved,"governmentAccounts").single().getString("id")
        assertEquals("No record",PhilippineEngine.contributionStatus(saved,id,"2026-09"))
        assertEquals(listOf("2026-03","2026-06","2026-09","2026-12"),
            PhilippineEngine.expectedPeriods(PhilippineEngine.rows(saved,"governmentAccounts").single(),2026))
        val marked=PhilippineEngine.apply(saved,PhilippineCommand.SaveContribution(id,"2026-09",
            JSONObject().put("status","Missed").put("amount",0L)))
        assertEquals("Missed",PhilippineEngine.contributionStatus(marked,id,"2026-09"))
        val record=PhilippineEngine.rows(marked,"governmentContributions").single()
        val erased=PhilippineEngine.apply(marked,PhilippineCommand.RemoveContribution(record.getString("id")))
        assertEquals("No record",PhilippineEngine.contributionStatus(erased,id,"2026-09"))
    }

    @Test fun contributionUpdatesKeepIdAndRequirePaidDate() {
        val first=PhilippineEngine.apply(blank(),PhilippineCommand.SaveGovernmentAccount(
            "PHILHEALTH",account("PHILHEALTH")))
        val id=PhilippineEngine.rows(first,"governmentAccounts").single().getString("id")
        assertThrows(IllegalArgumentException::class.java){
            PhilippineEngine.apply(first,PhilippineCommand.SaveContribution(id,"2026-10",
                JSONObject().put("status","Paid").put("amount",50000L)))
        }
        val paid=PhilippineEngine.apply(first,PhilippineCommand.SaveContribution(id,"2026-10",
            JSONObject().put("status","Paid").put("amount",50000L).put("paymentDate","2026-10-19")))
        val originalId=PhilippineEngine.rows(paid,"governmentContributions").single().getString("id")
        val updated=PhilippineEngine.apply(paid,PhilippineCommand.SaveContribution(id,"2026-10",
            JSONObject().put("status","Not Required").put("amount",0L)))
        assertEquals(originalId,PhilippineEngine.rows(updated,"governmentContributions").single().getString("id"))
        assertTrue(PhilippineEngine.rows(updated,"expenses").isEmpty())
    }

    @Test fun mp2FirstDepositStartsFiveYearMaturityAndNoExpense() {
        val added=PhilippineEngine.apply(blank(),PhilippineCommand.SaveMp2Account(mp2()))
        val id=PhilippineEngine.rows(added,"mp2Accounts").single().getString("id")
        val paid=PhilippineEngine.apply(added,PhilippineCommand.AddMp2Deposit(id,
            JSONObject().put("amount",125000L).put("paymentDate","2024-02-29")))
        val acct=PhilippineEngine.rows(paid,"mp2Accounts").single()
        assertEquals("2024-02-29",acct.getString("initialPaymentDate"))
        assertEquals("2029-02-28",PhilippineEngine.maturity(acct.getString("initialPaymentDate")))
        assertTrue(PhilippineEngine.rows(paid,"expenses").isEmpty())
        val payment=PhilippineEngine.rows(paid,"mp2Deposits").single()
        val removed=PhilippineEngine.apply(paid,
            PhilippineCommand.RemoveMp2Deposit(payment.getString("id")))
        assertTrue(PhilippineEngine.rows(removed,"mp2Deposits").isEmpty())
    }

    @Test fun duplicateMp2AccountNumbersAreRejected() {
        val first=PhilippineEngine.apply(blank(),PhilippineCommand.SaveMp2Account(mp2()))
        assertThrows(IllegalArgumentException::class.java) {
            PhilippineEngine.apply(first,PhilippineCommand.SaveMp2Account(mp2()))
        }
    }

    @Test fun preservesUnportedModulesInEveryMutation() {
        val first=TrackerEngine.apply(blank(),TrackerCommand.Save(TrackerKind.BILL,
            JSONObject().put("name","Rent").put("amount",50000)
                .put("category","Housing").put("frequency","Monthly")
                .put("dueDay",1).put("startMonth","2026-01").put("active",true)))
        val changed=PhilippineEngine.apply(first,PhilippineCommand.SaveMp2Account(mp2()))
        assertEquals(first.getJSONArray("recurringBills").toString(),
            changed.getJSONArray("recurringBills").toString())
        assertEquals(first.getJSONArray("expenses").toString(),
            changed.getJSONArray("expenses").toString())
    }
}
