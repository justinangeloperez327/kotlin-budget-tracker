package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrackerEngineTest {
    private fun newBill(active:Boolean=true) = JSONObject()
        .put("name","Rent").put("amount",185000L).put("category","Housing")
        .put("frequency","Monthly").put("dueDay",31).put("startMonth","2026-01")
        .put("active",active)
    private fun newDebt() = JSONObject().put("name","Personal loan").put("lender","Bank")
        .put("originalAmount",100000L).put("category","Other")
        .put("startDate","2026-01-02").put("monthlyTarget","20000")
        .put("active",true)
    private fun newGoal() = JSONObject().put("name","Emergency fund")
        .put("targetAmount",100000L).put("startDate","2026-01-01")
        .put("destination","UAE savings bank").put("active",true)
    private fun saved(kind:TrackerKind,details:JSONObject) =
        TrackerEngine.apply(TrackerEngine.blank(),TrackerCommand.Save(kind,details))

    @Test fun billDueDateAndSchedulesMatchSource() {
        val data=saved(TrackerKind.BILL,newBill())
        val bill=TrackerEngine.rows(data,"recurringBills").single()
        assertTrue(TrackerEngine.occurs(bill,YearMonth.of(2028,2)))
        assertEquals(LocalDate.of(2028,2,29),TrackerEngine.dueDate(bill,YearMonth.of(2028,2)))
        val quarterly=JSONObject(newBill().toString()).put("frequency","Quarterly")
        val d=saved(TrackerKind.BILL,quarterly)
        val q=TrackerEngine.rows(d,"recurringBills").single()
        assertTrue(TrackerEngine.occurs(q,YearMonth.of(2026,4)))
        assertFalse(TrackerEngine.occurs(q,YearMonth.of(2026,5)))
    }

    @Test fun billPaymentLinksExactlyOneExpenseAndUndoIsAtomic() {
        val initial=saved(TrackerKind.BILL,newBill())
        val id=TrackerEngine.rows(initial,"recurringBills").single().getString("id")
        assertEquals(0,TrackerEngine.rows(initial,"expenses").size)
        val paid=TrackerEngine.apply(initial,TrackerCommand.Record(
            TrackerKind.BILL,id,185000L,"2026-10-05","2026-10","RENT-001"))
        val payment=TrackerEngine.rows(paid,"billPayments").single()
        val expense=TrackerEngine.rows(paid,"expenses").single()
        assertEquals(payment.getString("expenseId"),expense.getString("id"))
        assertEquals(185000L,expense.getLong("amount"))
        assertEquals("Housing",expense.getString("category"))
        assertThrows(IllegalArgumentException::class.java){
            TrackerEngine.apply(paid,TrackerCommand.Record(
                TrackerKind.BILL,id,185000L,"2026-10-06","2026-10"))
        }
        assertThrows(IllegalArgumentException::class.java){
            TrackerEngine.apply(paid,TrackerCommand.Delete(TrackerKind.BILL,id))
        }
        val removed=TrackerEngine.apply(paid,TrackerCommand.Undo(TrackerKind.BILL,payment.getString("id")))
        assertEquals(0,TrackerEngine.rows(removed,"expenses").size)
        assertEquals(0,TrackerEngine.rows(removed,"billPayments").size)
    }

    @Test fun editingBillUpdatesLinkedExpenseMetadataWithoutDuplicatingIt() {
        val first=saved(TrackerKind.BILL,newBill())
        val billId=TrackerEngine.rows(first,"recurringBills").single().getString("id")
        val paid=TrackerEngine.apply(first,TrackerCommand.Record(
            TrackerKind.BILL,billId,185000L,"2026-10-05","2026-10"))
        val changed=TrackerEngine.apply(paid,TrackerCommand.Save(
            TrackerKind.BILL,newBill().put("name","Apartment Rent").put("category","Other"),billId))
        val expense=TrackerEngine.rows(changed,"expenses").single()
        assertEquals("Apartment Rent",expense.getString("description"))
        assertEquals("Other",expense.getString("category"))
        assertEquals(185000L,expense.getLong("amount"))
        assertEquals(1,TrackerEngine.rows(changed,"billPayments").size)
    }

    @Test fun debtRepaymentCannotExceedRemainingAndCanBeUndone() {
        val first=saved(TrackerKind.DEBT,newDebt())
        val id=TrackerEngine.rows(first,"debts").single().getString("id")
        assertEquals(100000L,TrackerEngine.remaining(first,TrackerKind.DEBT,id))
        val next=TrackerEngine.apply(first,TrackerCommand.Record(
            TrackerKind.DEBT,id,30000L,"2026-10-05"))
        assertEquals(70000L,TrackerEngine.remaining(next,TrackerKind.DEBT,id))
        assertEquals("Debt payment · Personal loan",
            TrackerEngine.rows(next,"expenses").single().getString("description"))
        assertThrows(IllegalArgumentException::class.java){
            TrackerEngine.apply(next,TrackerCommand.Record(
                TrackerKind.DEBT,id,70001L,"2026-10-06"))
        }
        val payment=TrackerEngine.rows(next,"debtPayments").single()
        val undone=TrackerEngine.apply(next,TrackerCommand.Undo(
            TrackerKind.DEBT,payment.getString("id")))
        assertTrue(TrackerEngine.rows(undone,"expenses").isEmpty())
        assertEquals(100000L,TrackerEngine.remaining(undone,TrackerKind.DEBT,id))
    }

    @Test fun savingsDepositNeverCreatesExpenseOrReducesSpendingBudget() {
        val initial=saved(TrackerKind.SAVINGS,newGoal())
        val goal=TrackerEngine.rows(initial,"savingsGoals").single()
        val id=goal.getString("id")
        val after=TrackerEngine.apply(initial,TrackerCommand.Record(
            TrackerKind.SAVINGS,id,35000L,"2026-10-05"))
        assertEquals(35000L,TrackerEngine.paid(after,TrackerKind.SAVINGS,id))
        assertEquals(65000L,TrackerEngine.remaining(after,TrackerKind.SAVINGS,id))
        assertTrue(TrackerEngine.rows(after,"expenses").isEmpty())
        assertEquals("Active",TrackerEngine.status(after,TrackerKind.SAVINGS,goal,LocalDate.of(2026,10,24)))
        assertThrows(IllegalArgumentException::class.java){
            TrackerEngine.apply(after,TrackerCommand.Delete(TrackerKind.SAVINGS,id))
        }
    }

    @Test fun preservesUnportedModulesWhenEditingBill() {
        val source=TrackerEngine.blank()
        source.put("remittances",org.json.JSONArray().put(JSONObject().put("id","nonfinancial-fixture")))
        val changed=TrackerEngine.apply(source,TrackerCommand.Save(TrackerKind.BILL,newBill()))
        assertEquals(source.getJSONArray("remittances").toString(),
            changed.getJSONArray("remittances").toString())
        assertEquals(0,TrackerEngine.rows(source,"recurringBills").size)
        assertEquals(1,TrackerEngine.rows(changed,"recurringBills").size)
    }

    @Test fun rejectsMissingOrWronglyScheduledBillPayments() {
        val initial=saved(TrackerKind.BILL,newBill().put("frequency","Yearly"))
        val id=TrackerEngine.rows(initial,"recurringBills").single().getString("id")
        assertThrows(IllegalArgumentException::class.java){
            TrackerEngine.apply(initial,TrackerCommand.Record(
                TrackerKind.BILL,id,100L,"2026-10-05","2026-10"))
        }
    }

    @Test fun validatesTargetsDatesNamesAndActiveState() {
        assertThrows(IllegalArgumentException::class.java){
            saved(TrackerKind.BILL,newBill().put("dueDay",32))
        }
        assertThrows(IllegalArgumentException::class.java){
            saved(TrackerKind.DEBT,newDebt().put("lender"," "))
        }
        val first=saved(TrackerKind.SAVINGS,newGoal())
        val id=TrackerEngine.rows(first,"savingsGoals").single().getString("id")
        val paused=TrackerEngine.apply(first,TrackerCommand.Save(
            TrackerKind.SAVINGS,newGoal().put("active",false),id))
        assertEquals("Paused",TrackerEngine.status(paused,TrackerKind.SAVINGS,
            TrackerEngine.rows(paused,"savingsGoals").single(),LocalDate.of(2026,10,24)))
        assertThrows(IllegalArgumentException::class.java){
            TrackerEngine.apply(paused,TrackerCommand.Record(
                TrackerKind.SAVINGS,id,100L,"2026-10-05"))
        }
    }

    @Test fun cannotInvalidateHistoricalBillPeriodsWithFrequencyEdits() {
        val first=saved(TrackerKind.BILL,newBill())
        val bill=TrackerEngine.rows(first,"recurringBills").single()
        val id=bill.getString("id")
        val afterPayment=TrackerEngine.apply(first,TrackerCommand.Record(
            TrackerKind.BILL,id,185000,"2026-10-05","2026-10"))
        assertThrows(IllegalArgumentException::class.java) {
            TrackerEngine.apply(afterPayment,TrackerCommand.Save(
                TrackerKind.BILL,newBill().put("frequency","Yearly"),id))
        }
        assertEquals("Monthly",
            TrackerEngine.rows(afterPayment,"recurringBills").single().getString("frequency"))
    }

}
