package dev.budgettracker.android.domain

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceBackupTest {
    private fun fresh(): JSONObject = TrackerEngine.blank().apply {
        PhilippineEngine.keys.forEach{put(it,JSONArray())}
        put("budgets",JSONObject().put("2026-10",220000L))
        put("categoryBudgets",JSONObject().put("2026-10",JSONObject().put("Housing",130000L)))
    }

    private fun populated():JSONObject {
        var next=TrackerEngine.apply(fresh(),TrackerCommand.Save(TrackerKind.BILL,
            JSONObject().put("name","Rent").put("amount",130000L)
                .put("category","Housing").put("frequency","Monthly")
                .put("dueDay",15).put("startMonth","2026-01").put("active",true)))
        val id=TrackerEngine.rows(next,"recurringBills").single().getString("id")
        next=TrackerEngine.apply(next,TrackerCommand.Record(
            TrackerKind.BILL,id,130000L,"2026-10-15","2026-10"))
        next=PhilippineEngine.apply(next,PhilippineCommand.SaveMp2Account(
            JSONObject().put("name","Emergency MP2")
                .put("dividendOption","Compounded").put("active",true)))
        val mp2=PhilippineEngine.rows(next,"mp2Accounts").single().getString("id")
        return PhilippineEngine.apply(next,PhilippineCommand.AddMp2Deposit(mp2,
            JSONObject().put("amount",24000L).put("paymentDate","2026-10-16")))
    }

    @Test fun validRoundtripPreservesAllSourceArraysAndCrossLinkedExpenses() {
        val before=populated()
        assertTrue(WorkspaceAudit.inspect(before).issues.toString(),WorkspaceAudit.inspect(before).passed)
        val encoded=DeviceBackup.encode(before,LocalDate.of(2026,10,26))
        val decoded=DeviceBackup.decode(encoded)
        assertEquals("2026-10-26",decoded.createdAt)
        assertEquals(1,decoded.budgetMonths)
        assertEquals(1,decoded.counts["expenses"])
        assertEquals(1,decoded.counts["billPayments"])
        assertEquals(1,decoded.counts["mp2Deposits"])
        assertEquals(before.getJSONObject("budgets").toString(),decoded.data.getJSONObject("budgets").toString())
        assertEquals(before.getJSONArray("expenses").toString(),decoded.data.getJSONArray("expenses").toString())
        assertEquals(before.getJSONArray("mp2Deposits").toString(),decoded.data.getJSONArray("mp2Deposits").toString())
    }

    @Test fun invalidChecksumAndFormatAreAlwaysRejected() {
        val encoded=DeviceBackup.encode(fresh())
        val wrong=JSONObject(encoded).put("sha256","0".repeat(64)).toString()
        assertThrows(IllegalArgumentException::class.java){DeviceBackup.decode(wrong)}
        val wrongFormat=JSONObject(encoded).put("version",2).toString()
        assertThrows(IllegalArgumentException::class.java){DeviceBackup.decode(wrongFormat)}
        val overlarge="x".repeat(DeviceBackup.MAX_BYTES+1)
        assertThrows(IllegalArgumentException::class.java){DeviceBackup.decode(overlarge)}
    }

    @Test fun recomputingChecksumCannotBypassReferentialIntegrity() {
        val broken=populated()
        val first=broken.getJSONArray("billPayments").getJSONObject(0)
        first.put("expenseId",java.util.UUID.randomUUID().toString())
        val report=WorkspaceAudit.inspect(broken)
        assertFalse(report.passed)
        assertTrue(report.issues.any{it.code=="missing-expense"})
        assertThrows(IllegalArgumentException::class.java) {
            DeviceBackup.encode(broken)
        }
        val valid=JSONObject(DeviceBackup.encode(fresh()))
        val raw=JSONObject(valid.getString("data")).apply {
            getJSONArray("expenses").put(JSONObject().put("id",java.util.UUID.randomUUID().toString())
                .put("description","Invalid").put("amount",1000L)
                .put("date","2026-10-05").put("category","Not a category"))
        }.toString()
        valid.put("data",raw).put("sha256",DeviceBackup.digest(raw))
        assertThrows(IllegalArgumentException::class.java){DeviceBackup.decode(valid.toString())}
    }

    @Test fun detectsDuplicateBillPeriodsAndOverpayment() {
        val before=populated()
        val payment=JSONObject(before.getJSONArray("billPayments").getJSONObject(0).toString())
        payment.put("id",java.util.UUID.randomUUID().toString())
        payment.put("expenseId",java.util.UUID.randomUUID().toString())
        before.getJSONArray("billPayments").put(payment)
        val result=WorkspaceAudit.inspect(before)
        assertFalse(result.passed)
        assertTrue(result.issues.any{it.code=="duplicate-payment"})
    }

    @Test fun boundedInputReaderStopsLargeFileWithoutReadingToEnd() {
        var called=0
        val bytes=ByteArray(8192){'a'.code.toByte()}
        assertThrows(IllegalArgumentException::class.java){
            DeviceBackup.readBounded { dst ->
                called++
                bytes.copyInto(dst)
                8192
            }
        }
        assertTrue(called < 500)
    }
}
