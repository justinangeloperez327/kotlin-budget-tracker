package dev.budgettracker.android.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Group 5 operates on the original Next.js workspace-shaped JSON.
 * AED/PHP values remain independent integer minor units; only remittance
 * fee/support are consumption expenses.
 */
sealed interface PhilippineCommand {
    data class SaveRemittance(val fields: JSONObject, val existingId: String? = null): PhilippineCommand
    data class RemoveRemittance(val id: String): PhilippineCommand
    data class SaveGovernmentAccount(val provider: String, val fields: JSONObject): PhilippineCommand
    data class SaveContribution(val accountId: String, val period: String, val fields: JSONObject): PhilippineCommand
    data class RemoveContribution(val id: String): PhilippineCommand
    data class SaveMp2Account(val fields: JSONObject, val existingId: String? = null): PhilippineCommand
    data class AddMp2Deposit(val accountId: String, val fields: JSONObject): PhilippineCommand
    data class RemoveMp2Deposit(val id: String): PhilippineCommand
}

object PhilippineEngine {
    val keys = listOf("remittances","governmentAccounts","governmentContributions","mp2Accounts","mp2Deposits")
    val providers = listOf("SSS","PHILHEALTH","PAGIBIG")
    val memberTypes: Map<String,List<String>> = mapOf(
        "SSS" to listOf("Employee","Self-employed","Voluntary","OFW"),
        "PHILHEALTH" to listOf("Direct Contributor","Employed - Private","Employed - Government",
            "Informal / Self-Earning","OFW / Migrant Worker","Lifetime Member","Kasambahay",
            "Other Direct Contributor","Indirect Contributor"),
        "PAGIBIG" to listOf("Mandatory - Employed","Mandatory - Self-Employed","Mandatory - OFW",
            "Mandatory - Employee of Foreign Government","Mandatory - Barangay Official / Employee",
            "Voluntary - Employed","Voluntary - Individual Payor","Voluntary - Member of Cooperative",
            "Voluntary - Member of Trade Union","Voluntary - Non-Working Spouse",
            "Voluntary - Member of Religious Group","Voluntary - Overseas Filipino Immigrant",
            "Voluntary - Pensioner / Investor / Lessor","Voluntary - Other")
    )
    fun rows(data: JSONObject, key: String): List<JSONObject> {
        val array = data.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun change(data: JSONObject, key: String, values: List<JSONObject>) {
        data.put(key,JSONArray().apply { values.forEach { put(it) } })
    }
    private fun text(value: String, max: Int, optional: Boolean = false): String {
        val clean=value.trim()
        require(clean.length <= max && (optional || clean.isNotBlank())) { "Invalid text (maximum $max characters)" }
        return clean
    }
    private fun amount(value: Long, allowZero: Boolean = false): Long {
        require(value >= (if (allowZero) 0 else 1) && value <= BudgetFeatures.MAX_MINOR) {
            "Invalid monetary amount"
        }
        return value
    }
    private fun day(value: String): String {
        val date=LocalDate.parse(value)
        require(date.year in 1900..2199) { "Date must be between 1900 and 2199" }
        return date.toString()
    }
    private fun month(value: String): String {
        val date=YearMonth.parse(value)
        require(date.year in 1900..2199) { "Period must be between 1900 and 2199" }
        return date.toString()
    }
    private fun optionalField(obj: JSONObject, field: String, value: String, max: Int) {
        val safe=text(value,max,true)
        if(safe.isNotEmpty())obj.put(field,safe)
    }
    private fun assertNotOverCap(data: JSONObject) {
        require(rows(data,"remittances").size<=10000 &&
            rows(data,"governmentAccounts").size<=12 &&
            rows(data,"governmentContributions").size<=1200 &&
            rows(data,"mp2Accounts").size<=24 &&
            rows(data,"mp2Deposits").size<=5000 &&
            rows(data,"expenses").size<=2000) { "Workspace limit reached" }
    }

    fun expenseAmount(remittance: JSONObject): Long = Math.addExact(
        if(remittance.getBoolean("principalAsExpense")) remittance.getLong("sentAmount") else 0L,
        remittance.getLong("feeAmount")
    )
    fun effectiveRate(remittance: JSONObject): Double? =
        if(remittance.getString("status")=="Completed" && remittance.has("receivedAmount"))
            remittance.getLong("receivedAmount").toDouble()/remittance.getLong("sentAmount")
        else null

    fun maturity(initialDate: String?): String? =
        initialDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(day(it)).plusYears(5).toString() }

    fun expectedPeriods(account: JSONObject, year: Int): List<String> {
        if(!account.optBoolean("active"))return emptyList()
        val months=if(account.getString("frequency")=="Quarterly")listOf(3,6,9,12) else (1..12).toList()
        return months.map { "%04d-%02d".format(java.util.Locale.US,year,it) }
    }
    /** Missing contribution is an unknown record, never automatically labelled "Missed". */
    fun contributionStatus(data:JSONObject, accountId:String, period:String):String =
        rows(data,"governmentContributions").firstOrNull {
            it.getString("accountId")==accountId && it.getString("period")==period
        }?.getString("status") ?: "No record"

    fun apply(input:JSONObject, command:PhilippineCommand): JSONObject {
        val data=JSONObject(input.toString())
        when(command){
            is PhilippineCommand.SaveRemittance -> {
                val f=command.fields
                val existing=rows(data,"remittances").firstOrNull { it.getString("id")==command.existingId }
                require(command.existingId==null || existing!=null) { "Remittance not found" }
                val id=existing?.getString("id") ?: UUID.randomUUID().toString()
                val recipient=text(f.getString("recipient"),120)
                val destination=f.getString("destinationType")
                require(destination in listOf("Family / person","Own account"))
                val sent=amount(f.getLong("sentAmount"))
                val fee=amount(f.getLong("feeAmount"),true)
                require(Math.addExact(sent,fee)<=BudgetFeatures.MAX_MINOR)
                val status=f.getString("status")
                require(status in listOf("Pending","Completed"))
                val category=f.getString("category")
                require(Category.entries.any { it.label==category })
                val row=JSONObject().put("id",id).put("recipient",recipient)
                    .put("destinationType",destination).put("sentAmount",sent).put("feeAmount",fee)
                    .put("transferDate",day(f.getString("transferDate"))).put("status",status)
                    .put("principalAsExpense",f.getBoolean("principalAsExpense"))
                    .put("category",category)
                if(f.has("receivedAmount")){
                    row.put("receivedAmount",amount(f.getLong("receivedAmount")))
                }
                require(status!="Completed" || row.has("receivedAmount")) {
                    "Completed remittances require received PHP amount"
                }
                optionalField(row,"provider",f.optString("provider"),120)
                optionalField(row,"referenceNumber",f.optString("referenceNumber"),80)
                optionalField(row,"notes",f.optString("notes"),500)
                val expenseSum=expenseAmount(row)
                val oldExpenseId=existing?.optString("expenseId")?.takeIf { it.isNotBlank() }
                val nextExpenses=rows(data,"expenses").filterNot { it.getString("id")==oldExpenseId }.toMutableList()
                if(expenseSum>0) {
                    val expenseId=oldExpenseId ?: UUID.randomUUID().toString()
                    row.put("expenseId",expenseId)
                    nextExpenses.add(JSONObject().put("id",expenseId)
                        .put("description",("Remittance · "+recipient).take(120))
                        .put("amount",expenseSum).put("category",category)
                        .put("date",row.getString("transferDate")))
                }
                change(data,"expenses",nextExpenses)
                change(data,"remittances",rows(data,"remittances").filterNot { it.getString("id")==id }+row)
            }
            is PhilippineCommand.RemoveRemittance -> {
                val existing=rows(data,"remittances").firstOrNull{it.getString("id")==command.id}
                    ?: throw IllegalArgumentException("Remittance not found")
                val expenseId=existing.optString("expenseId")
                change(data,"remittances",rows(data,"remittances").filterNot{it.getString("id")==command.id})
                if(expenseId.isNotBlank())
                    change(data,"expenses",rows(data,"expenses").filterNot{it.getString("id")==expenseId})
            }
            is PhilippineCommand.SaveGovernmentAccount -> {
                val f=command.fields
                val provider=command.provider
                require(provider in providers)
                val memberType=f.getString("memberType")
                require(memberType in requireNotNull(memberTypes[provider]))
                val frequency=f.getString("frequency")
                require(frequency=="Monthly"||frequency=="Quarterly")
                val existing=rows(data,"governmentAccounts").firstOrNull{it.getString("provider")==provider}
                val row=JSONObject().put("id",existing?.getString("id")?:UUID.randomUUID().toString())
                    .put("provider",provider).put("memberType",memberType).put("frequency",frequency)
                    .put("active",f.getBoolean("active"))
                optionalField(row,"accountIdentifier",f.optString("accountIdentifier"),40)
                if(f.has("monthlyTarget"))row.put("monthlyTarget",amount(f.getLong("monthlyTarget"),true))
                change(data,"governmentAccounts",rows(data,"governmentAccounts")
                    .filterNot{it.getString("provider")==provider}+row)
            }
            is PhilippineCommand.SaveContribution -> {
                val account=rows(data,"governmentAccounts").firstOrNull{
                    it.getString("id")==command.accountId
                } ?: throw IllegalArgumentException("Government account not configured")
                val period=month(command.period)
                val f=command.fields
                val status=f.getString("status")
                require(status in listOf("Paid","Pending","Missed","Not Required"))
                val paid=if(status=="Paid")amount(f.getLong("amount"),true) else amount(f.optLong("amount",0),true)
                val paymentDate=f.optString("paymentDate")
                require(status!="Paid" || paymentDate.isNotBlank()) { "Paid contributions require a payment date" }
                val found=rows(data,"governmentContributions").firstOrNull {
                    it.getString("accountId")==account.getString("id") && it.getString("period")==period
                }
                val row=JSONObject().put("id",found?.getString("id")?:UUID.randomUUID().toString())
                    .put("accountId",account.getString("id")).put("period",period)
                    .put("amount",paid).put("status",status)
                if(paymentDate.isNotBlank())row.put("paymentDate",day(paymentDate))
                optionalField(row,"referenceNumber",f.optString("referenceNumber"),80)
                optionalField(row,"notes",f.optString("notes"),500)
                change(data,"governmentContributions",rows(data,"governmentContributions").filterNot{
                    it.getString("accountId")==command.accountId && it.getString("period")==period
                }+row)
            }
            is PhilippineCommand.RemoveContribution -> {
                require(rows(data,"governmentContributions").any{it.getString("id")==command.id})
                change(data,"governmentContributions",rows(data,"governmentContributions").filterNot{
                    it.getString("id")==command.id
                })
            }
            is PhilippineCommand.SaveMp2Account -> {
                val f=command.fields
                val old=rows(data,"mp2Accounts").firstOrNull{it.getString("id")==command.existingId}
                require(command.existingId==null || old!=null)
                val number=text(f.optString("accountNumber"),40,true)
                require(number.isBlank() || rows(data,"mp2Accounts").none{
                    it.getString("id")!=command.existingId && it.optString("accountNumber")==number
                }) {"MP2 account number already exists"}
                val option=f.getString("dividendOption")
                require(option in listOf("Compounded","Annual"))
                val row=JSONObject().put("id",old?.getString("id")?:UUID.randomUUID().toString())
                    .put("name",text(f.getString("name"),80))
                    .put("dividendOption",option).put("active",f.getBoolean("active"))
                if(number.isNotBlank())row.put("accountNumber",number)
                optionalField(row,"initialPaymentDate",f.optString("initialPaymentDate"),10)
                if(row.has("initialPaymentDate"))day(row.getString("initialPaymentDate"))
                if(f.has("monthlyTarget"))row.put("monthlyTarget",amount(f.getLong("monthlyTarget"),true))
                change(data,"mp2Accounts",rows(data,"mp2Accounts")
                    .filterNot{it.getString("id")==row.getString("id")}+row)
            }
            is PhilippineCommand.AddMp2Deposit -> {
                val account=rows(data,"mp2Accounts").firstOrNull{it.getString("id")==command.accountId}
                    ?: throw IllegalArgumentException("MP2 account not found")
                val f=command.fields
                val date=day(f.getString("paymentDate"))
                val row=JSONObject().put("id",UUID.randomUUID().toString())
                    .put("accountId",command.accountId).put("paymentDate",date)
                    .put("amount",amount(f.getLong("amount")))
                optionalField(row,"referenceNumber",f.optString("referenceNumber"),80)
                optionalField(row,"notes",f.optString("notes"),500)
                change(data,"mp2Deposits",rows(data,"mp2Deposits")+row)
                if(!account.has("initialPaymentDate")) {
                    val copy=JSONObject(account.toString()).put("initialPaymentDate",date)
                    change(data,"mp2Accounts",rows(data,"mp2Accounts")
                        .map{if(it.getString("id")==command.accountId)copy else it})
                }
            }
            is PhilippineCommand.RemoveMp2Deposit -> {
                require(rows(data,"mp2Deposits").any{it.getString("id")==command.id})
                change(data,"mp2Deposits",rows(data,"mp2Deposits").filterNot{it.getString("id")==command.id})
            }
        }
        assertNotOverCap(data)
        return data
    }
}
