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
import org.json.JSONObject

internal fun governmentTitle(provider:String) = when(provider) {
    "SSS" -> "Social Security System (SSS)"
    "PHILHEALTH" -> "PhilHealth"
    else -> "Pag-IBIG Membership Savings"
}

@Composable
fun ContributionsOverview(model:FinanceViewModel) {
    val all=PhilippineEngine.rows(model.trackerData,"governmentAccounts")
    val payments=PhilippineEngine.rows(model.trackerData,"governmentContributions")
    val year=LocalDate.now().year.toString()
    val sum=payments.filter { it.getString("status")=="Paid" && it.getString("period").startsWith(year) }
        .fold(0L){s,c->Math.addExact(s,c.getLong("amount"))}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Philippine contributions",style=MaterialTheme.typography.titleLarge)
        Text("SSS, PhilHealth, Pag-IBIG and MP2 maintain separate PHP histories; none are AED expenses.",
            style=MaterialTheme.typography.bodySmall)
        Text("Government accounts configured: "+all.size+" / 3")
        Text("Paid contributions in "+year+": "+php(sum))
        PhilippineEngine.providers.forEach { provider ->
            val account=all.firstOrNull{it.getString("provider")==provider}
            val paid=payments.filter {
                it.getString("accountId")==account?.optString("id") &&
                    it.getString("status")=="Paid" && it.getString("period").startsWith(year)
            }.fold(0L){s,p->Math.addExact(s,p.getLong("amount"))}
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(governmentTitle(provider),style=MaterialTheme.typography.titleMedium)
                    Text(if(account==null)"Not configured"
                        else (if(account.optBoolean("active"))"Active" else "Inactive")+
                            " · "+account.getString("frequency"))
                    Text("Paid YTD: "+php(paid))
                }
            }
        }
        Text("A blank period is 'No record'; it is not automatically 'Missed'.",
            style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun GovernmentScreen(model: FinanceViewModel, provider:String) {
    val data=model.trackerData
    val account=PhilippineEngine.rows(data,"governmentAccounts")
        .firstOrNull{it.getString("provider")==provider}
    val entries=PhilippineEngine.rows(data,"governmentContributions")
        .filter{it.getString("accountId")==account?.optString("id")}
    var year by remember {mutableIntStateOf(LocalDate.now().year)}
    var configOpen by remember {mutableStateOf(false)}
    var editingPeriod by remember {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<JSONObject?>(null)}
    val editable=!model.loading && !model.syncBlocked
    val paid=entries.filter{it.getString("period").startsWith(year.toString())&&it.getString("status")=="Paid"}
    val paidAmount=paid.fold(0L){sum,r->Math.addExact(sum,r.getLong("amount"))}
    val due=if(account==null)emptyList() else PhilippineEngine.expectedPeriods(account,year)
    val completedExpected=due.count{period->entries.any{
        it.getString("period")==period&&it.getString("status")=="Paid"
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(governmentTitle(provider),style=MaterialTheme.typography.titleLarge)
        Text("PHP contribution history. Unrecorded months are unknown, not missed.",
            style=MaterialTheme.typography.bodySmall)
        Button(onClick={configOpen=true},enabled=editable){
            Text(if(account==null)"Configure account" else "Edit account")
        }
        if(account!=null){
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("Member type: "+account.getString("memberType"))
                    Text("Frequency: "+account.getString("frequency"))
                    Text("Status: "+if(account.optBoolean("active"))"Active" else "Inactive")
                    if(account.has("monthlyTarget"))Text("Target: "+php(account.getLong("monthlyTarget")))
                    if(account.has("accountIdentifier"))Text("Account number: "+account.getString("accountIdentifier"))
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick={year--}){Text("‹ Year")}
            Text(year.toString(),style=MaterialTheme.typography.titleMedium,
                modifier=Modifier.padding(top=11.dp))
            TextButton(onClick={year++}){Text("Year ›")}
        }
        Text("Paid "+php(paidAmount)+" · "+paid.size+" paid records",
            style=MaterialTheme.typography.titleMedium)
        Text("Recorded paid expected periods: "+completedExpected+" / "+due.size,
            style=MaterialTheme.typography.bodySmall)
        (1..12).forEach { month->
            val period=String.format(java.util.Locale.US,"%04d-%02d",year,month)
            val entry=entries.firstOrNull{it.getString("period")==period}
            Card(Modifier.fillMaxWidth()){
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){
                        Text(period,style=MaterialTheme.typography.titleSmall)
                        Text(entry?.getString("status") ?: "No record",
                            style=MaterialTheme.typography.bodySmall,
                            color=if(entry?.optString("status")=="Missed")
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if(entry!=null){
                            Text(php(entry.getLong("amount")),style=MaterialTheme.typography.bodySmall)
                            if(entry.has("paymentDate"))
                                Text("Paid on "+entry.getString("paymentDate"),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick={editingPeriod=period},enabled=editable && account!=null){
                        Text(if(entry==null)"Record" else "Edit")
                    }
                    if(entry!=null)TextButton(onClick={deleting=entry},enabled=editable){
                        Text("Remove",color=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if(configOpen)GovernmentAccountDialog(model,provider,account){configOpen=false}
    editingPeriod?.let{period->
        if(account!=null)GovernmentContributionDialog(model,account,period,
            entries.firstOrNull{it.getString("period")==period}){editingPeriod=null}
    }
    deleting?.let {entry->
        val baseline=remember(entry.getString("id")){model.savedMutationSequence}
        LaunchedEffect(model.savedMutationSequence){
            if(model.savedMutationSequence>baseline)deleting=null
        }
        AlertDialog(onDismissRequest={if(!model.loading)deleting=null},
            title={Text("Remove contribution record?")},
            text={Text("This removes only the recorded PHP contribution, not an AED expense.")},
            confirmButton={Button(onClick={
                model.philippineMutation(PhilippineCommand.RemoveContribution(entry.getString("id")))
            },enabled=editable){Text("Remove")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})
    }
}

@Composable
private fun GovernmentAccountDialog(model:FinanceViewModel,provider:String,
                                    existing:JSONObject?,onClose:()->Unit){
    val types=requireNotNull(PhilippineEngine.memberTypes[provider])
    val key=existing?.optString("id") ?: provider
    var member by remember(key){mutableStateOf(existing?.optString("memberType") ?: types.first())}
    var frequency by remember(key){mutableStateOf(existing?.optString("frequency") ?: "Monthly")}
    var identifier by remember(key){mutableStateOf(existing?.optString("accountIdentifier") ?: "")}
    var target by remember(key){mutableStateOf(if(existing?.has("monthlyTarget")==true)
        inputMoney(existing.getLong("monthlyTarget")) else "")}
    var active by remember(key){mutableStateOf(existing?.optBoolean("active") ?: true)}
    var failure by remember{mutableStateOf<String?>(null)}
    val baseline=remember(key){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){if(model.savedMutationSequence>baseline)onClose()}
    AlertDialog(onDismissRequest={if(!model.loading)onClose()},
        title={Text("Configure "+governmentTitle(provider))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            PhSelection("Member type",member,types){member=it}
            PhSelection("Frequency",frequency,listOf("Monthly","Quarterly")){frequency=it}
            PhField("Member/account number (optional)",identifier,{identifier=it})
            PhField("Monthly target PHP (optional)",target,{target=it},money=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("Active",modifier=Modifier.padding(top=12.dp))
                Switch(checked=active,onCheckedChange={active=it})
            }
            PhError(failure,model.error)
        }},
        confirmButton={Button(onClick={
            try {
                val f=JSONObject().put("memberType",member).put("frequency",frequency)
                    .put("accountIdentifier",identifier).put("active",active)
                if(target.isNotBlank())f.put("monthlyTarget",parseMinor(target))
                model.philippineMutation(PhilippineCommand.SaveGovernmentAccount(provider,f))
                failure=null
            }catch(e:Exception){failure=e.message ?: "Invalid account"}
        },enabled=!model.loading && !model.syncBlocked){Text("Save")}},
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}})
}

@Composable
private fun GovernmentContributionDialog(model:FinanceViewModel,account:JSONObject,
                                         period:String,existing:JSONObject?,onClose:()->Unit){
    val key=account.getString("id")+":"+period
    var status by remember(key){mutableStateOf(existing?.optString("status") ?: "Pending")}
    var amount by remember(key){mutableStateOf(if(existing==null)"0.00" else inputMoney(existing.getLong("amount")))}
    var date by remember(key){mutableStateOf(existing?.optString("paymentDate") ?: LocalDate.now().toString())}
    var reference by remember(key){mutableStateOf(existing?.optString("referenceNumber") ?: "")}
    var notes by remember(key){mutableStateOf(existing?.optString("notes") ?: "")}
    var failure by remember{mutableStateOf<String?>(null)}
    val baseline=remember(key){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){if(model.savedMutationSequence>baseline)onClose()}
    AlertDialog(onDismissRequest={if(!model.loading)onClose()},
        title={Text("Contribution "+period)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            PhSelection("Status",status,listOf("Paid","Pending","Missed","Not Required")){status=it}
            PhField("Amount PHP",amount,{amount=it},money=true)
            if(status=="Paid")PhField("Payment date YYYY-MM-DD",date,{date=it})
            PhField("Reference (optional)",reference,{reference=it})
            PhField("Notes (optional)",notes,{notes=it},single=false)
            Text("Only explicitly choosing Missed marks this month as missed.",
                style=MaterialTheme.typography.bodySmall)
            PhError(failure,model.error)
        }},
        confirmButton={Button(onClick={
            try {
                val f=JSONObject().put("status",status).put("amount",parseMinor(amount))
                    .put("referenceNumber",reference).put("notes",notes)
                if(status=="Paid")f.put("paymentDate",date)
                model.philippineMutation(PhilippineCommand.SaveContribution(
                    account.getString("id"),period,f))
                failure=null
            }catch(e:Exception){failure=e.message ?: "Invalid contribution"}
        },enabled=!model.loading && !model.syncBlocked){Text("Save")}},
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}})
}
