package dev.budgettracker.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.*
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject

@Composable
fun RemittanceScreen(model: FinanceViewModel) {
    val entries=PhilippineEngine.rows(model.trackerData,"remittances")
        .sortedByDescending { it.getString("transferDate") }
    val current=YearMonth.now().toString()
    val monthly=entries.filter { it.getString("transferDate").startsWith(current) }
    val sent=monthly.fold(0L) { s,r->Math.addExact(s,r.getLong("sentAmount")) }
    val fees=monthly.fold(0L) { s,r->Math.addExact(s,r.getLong("feeAmount")) }
    val received=monthly.filter{it.getString("status")=="Completed"}
        .fold(0L) {s,r->Math.addExact(s,r.optLong("receivedAmount",0))}
    var editing by remember {mutableStateOf<JSONObject?>(null)}
    var showForm by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<JSONObject?>(null)}
    val editable=!model.loading && !model.syncBlocked
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Remittances · AED → PHP",style=MaterialTheme.typography.titleLarge)
        Text("Family support and own-account transfers; never combine AED and PHP.",
            style=MaterialTheme.typography.bodySmall)
        Button(onClick={editing=null;showForm=true},enabled=editable){Text("Add remittance")}
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp)){
                Text("This month · "+current,style=MaterialTheme.typography.titleMedium)
                Text("Sent "+aedMoney(sent))
                Text("Fees "+aedMoney(fees))
                Text("Received (completed) "+php(received))
                Text(monthly.size.toString()+" transfers")
            }
        }
        if(entries.isEmpty())Text("No remittances recorded.")
        entries.forEach { r ->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(r.getString("recipient"),style=MaterialTheme.typography.titleMedium)
                    Text(r.getString("destinationType")+" · "+r.getString("status"))
                    Text("Sent "+aedMoney(r.getLong("sentAmount"))+" · fee "+aedMoney(r.getLong("feeAmount")))
                    if(r.has("receivedAmount"))Text("Received "+php(r.getLong("receivedAmount")))
                    PhilippineEngine.effectiveRate(r)?.let { rate ->
                        Text("PHP/AED effective rate "+String.format(java.util.Locale.US,"%.4f",rate))
                    }
                    Text(r.getString("transferDate")+" · "+r.getString("category"),
                        style=MaterialTheme.typography.bodySmall)
                    Text(if(r.getBoolean("principalAsExpense"))"Principal + fee counted as AED expense"
                        else "Only fee counted as AED expense",style=MaterialTheme.typography.bodySmall)
                    if(r.has("referenceNumber"))Text("Ref "+r.getString("referenceNumber"),
                        style=MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick={editing=r;showForm=true},enabled=editable){Text("Edit")}
                        TextButton(onClick={deleting=r},enabled=editable){
                            Text("Delete",color=MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if(showForm)RemittanceEditor(model,editing){showForm=false;editing=null}
    deleting?.let { item->
        val initial=remember(item.getString("id")){model.savedMutationSequence}
        LaunchedEffect(model.savedMutationSequence){
            if(model.savedMutationSequence>initial)deleting=null
        }
        AlertDialog(onDismissRequest={if(!model.loading)deleting=null},
            title={Text("Delete remittance?")},
            text={Text("The remittance and its linked AED fee/support expense will be removed.")},
            confirmButton={Button(onClick={
                model.philippineMutation(PhilippineCommand.RemoveRemittance(item.getString("id")))
            },enabled=editable){Text("Delete")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})
    }
}

@Composable
private fun RemittanceEditor(model:FinanceViewModel,existing:JSONObject?,onClose:()->Unit) {
    val id=existing?.optString("id") ?: "new"
    fun prior(k:String)=existing?.optString(k) ?: ""
    var recipient by remember(id){mutableStateOf(prior("recipient"))}
    var destination by remember(id){mutableStateOf(prior("destinationType").ifBlank{"Family / person"})}
    var provider by remember(id){mutableStateOf(prior("provider"))}
    var sent by remember(id){mutableStateOf(if(existing==null)"" else inputMoney(existing.getLong("sentAmount")))}
    var fee by remember(id){mutableStateOf(if(existing==null)"0.00" else inputMoney(existing.getLong("feeAmount")))}
    var received by remember(id){mutableStateOf(if(existing?.has("receivedAmount")==true)
        inputMoney(existing.getLong("receivedAmount")) else "")}
    var date by remember(id){mutableStateOf(prior("transferDate").ifBlank{LocalDate.now().toString()})}
    var status by remember(id){mutableStateOf(prior("status").ifBlank{"Pending"})}
    var principal by remember(id){mutableStateOf(existing?.optBoolean("principalAsExpense") ?: true)}
    var category by remember(id){mutableStateOf(prior("category").ifBlank{"Other"})}
    var reference by remember(id){mutableStateOf(prior("referenceNumber"))}
    var notes by remember(id){mutableStateOf(prior("notes"))}
    var localError by remember {mutableStateOf<String?>(null)}
    val start=remember(id){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){if(model.savedMutationSequence>start)onClose()}
    AlertDialog(onDismissRequest={if(!model.loading)onClose()},
        title={Text(if(existing==null)"Add remittance" else "Edit remittance")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            PhField("Recipient",recipient,{recipient=it})
            PhSelection("Destination",destination,listOf("Family / person","Own account")) {
                destination=it
                principal=(it=="Family / person")
            }
            PhField("Transfer provider",provider,{provider=it})
            PhField("AED sent",sent,{sent=it},money=true)
            PhField("AED fee",fee,{fee=it},money=true)
            PhField("PHP received (completed)",received,{received=it},money=true)
            PhField("Transfer date YYYY-MM-DD",date,{date=it})
            PhSelection("Status",status,listOf("Pending","Completed")){status=it}
            PhSelection("Expense category",category,Category.entries.map{it.label}){category=it}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("Count principal as expense",modifier=Modifier.weight(1f).padding(top=12.dp))
                Switch(checked=principal,onCheckedChange={principal=it})
            }
            Text("Own-account principal should not count as spending. Fees are expenses.",
                style=MaterialTheme.typography.bodySmall)
            PhField("Reference",reference,{reference=it})
            PhField("Notes",notes,{notes=it},single=false)
            PhError(localError,model.error)
        }},
        confirmButton={Button(onClick={
            try {
                val row=JSONObject().put("recipient",recipient).put("destinationType",destination)
                    .put("provider",provider).put("sentAmount",positiveInput(sent))
                    .put("feeAmount",parseMinor(fee)).put("transferDate",date).put("status",status)
                    .put("principalAsExpense",principal).put("category",category)
                    .put("referenceNumber",reference).put("notes",notes)
                if(received.isNotBlank())row.put("receivedAmount",positiveInput(received))
                model.philippineMutation(PhilippineCommand.SaveRemittance(row,existing?.optString("id")))
                localError=null
            }catch(ex:Exception){localError=ex.message ?: "Invalid remittance details"}
        },enabled=!model.loading && !model.syncBlocked){Text("Save")}},
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}})
}
