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
fun Mp2Screen(model:FinanceViewModel) {
    val accounts=PhilippineEngine.rows(model.trackerData,"mp2Accounts")
    val deposits=PhilippineEngine.rows(model.trackerData,"mp2Deposits")
    val now=LocalDate.now()
    val total=deposits.fold(0L){s,r->Math.addExact(s,r.getLong("amount"))}
    val ytd=deposits.filter{it.getString("paymentDate").startsWith(now.year.toString())}
        .fold(0L){s,r->Math.addExact(s,r.getLong("amount"))}
    var editing by remember {mutableStateOf<JSONObject?>(null)}
    var showAccount by remember {mutableStateOf(false)}
    var depositing by remember {mutableStateOf<JSONObject?>(null)}
    var deleting by remember {mutableStateOf<JSONObject?>(null)}
    var year by remember {mutableIntStateOf(now.year)}
    val editable=!model.loading && !model.syncBlocked
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("MP2 Savings · PHP",style=MaterialTheme.typography.titleLarge)
        Text("Track Pag-IBIG MP2 savings, dividends option and five-year maturity. Deposits do not create AED expenses.",
            style=MaterialTheme.typography.bodySmall)
        Button(onClick={editing=null;showAccount=true},enabled=editable){Text("Add MP2 account")}
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("Total recorded MP2 deposits: "+php(total))
                Text("Saved in "+now.year+": "+php(ytd))
                Text("Accounts: "+accounts.size)
            }
        }
        if(accounts.isEmpty())Text("No MP2 accounts yet.")
        accounts.forEach { account ->
            val accountId=account.getString("id")
            val own=deposits.filter{it.getString("accountId")==accountId}
                .sortedByDescending{it.getString("paymentDate")}
            val balance=own.fold(0L){s,r->Math.addExact(s,r.getLong("amount"))}
            val maturity=PhilippineEngine.maturity(account.optString("initialPaymentDate"))
            val currentMonth=YearMonth.now().toString()
            val thisMonth=own.filter{it.getString("paymentDate").startsWith(currentMonth)}
                .fold(0L){s,r->Math.addExact(s,r.getLong("amount"))}
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text(account.getString("name"),style=MaterialTheme.typography.titleMedium)
                    Text(if(account.optBoolean("active"))"Active" else "Inactive")
                    if(account.has("accountNumber"))Text("MP2 number: "+account.getString("accountNumber"))
                    Text("Dividends: "+account.getString("dividendOption"))
                    Text("Recorded savings: "+php(balance))
                    Text("Five-year maturity: "+(maturity ?: "Not started"))
                    if(maturity!=null){
                        Text(if(LocalDate.parse(maturity)<=now)"Matured" else "Maturing",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    if(account.has("monthlyTarget")){
                        val target=account.getLong("monthlyTarget")
                        Text("This month: "+php(thisMonth)+" / target "+php(target))
                        if(target>0L)LinearProgressIndicator(
                            progress={(thisMonth.toDouble()/target).toFloat().coerceIn(0f,1f)},
                            modifier=Modifier.fillMaxWidth()
                        )
                    }
                    Row {
                        TextButton(onClick={editing=account;showAccount=true},enabled=editable){Text("Edit")}
                        TextButton(onClick={depositing=account},enabled=editable){Text("Deposit")}
                    }
                    if(own.isNotEmpty()){
                        HorizontalDivider()
                        Text("Deposit history",style=MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            TextButton(onClick={year--}){Text("‹ Year")}
                            Text(year.toString(),modifier=Modifier.padding(top=13.dp))
                            TextButton(onClick={year++}){Text("Year ›")}
                        }
                        (1..12).forEach{month->
                            val period=String.format(java.util.Locale.US,"%04d-%02d",year,month)
                            val matches=own.filter{it.getString("paymentDate").startsWith(period)}
                            val sum=matches.fold(0L){s,r->Math.addExact(s,r.getLong("amount"))}
                            Text(period+" · "+php(sum)+" · "+matches.size+" deposits",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        own.filter{it.getString("paymentDate").startsWith(year.toString())}
                            .forEach { deposit ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                Column(Modifier.weight(1f)){
                                    Text(php(deposit.getLong("amount")))
                                    Text(deposit.getString("paymentDate"),style=MaterialTheme.typography.bodySmall)
                                    if(deposit.has("referenceNumber"))Text(
                                        "Ref "+deposit.getString("referenceNumber"),style=MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick={deleting=deposit},enabled=editable){Text("Undo")}
                            }
                        }
                    }
                }
            }
        }
    }
    if(showAccount)Mp2AccountDialog(model,editing){showAccount=false;editing=null}
    depositing?.let{account->Mp2DepositDialog(model,account){depositing=null}}
    deleting?.let{deposit->
        val baseline=remember(deposit.getString("id")){model.savedMutationSequence}
        LaunchedEffect(model.savedMutationSequence){
            if(model.savedMutationSequence>baseline)deleting=null
        }
        AlertDialog(onDismissRequest={if(!model.loading)deleting=null},
            title={Text("Remove MP2 deposit?")},
            text={Text("The PHP savings entry will be removed. No AED expense is affected.")},
            confirmButton={Button(onClick={
                model.philippineMutation(PhilippineCommand.RemoveMp2Deposit(deposit.getString("id")))
            },enabled=editable){Text("Remove")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})
    }
}

@Composable
private fun Mp2AccountDialog(model:FinanceViewModel,existing:JSONObject?,onClose:()->Unit){
    val id=existing?.optString("id") ?: "new"
    var name by remember(id){mutableStateOf(existing?.optString("name") ?: "")}
    var number by remember(id){mutableStateOf(existing?.optString("accountNumber") ?: "")}
    var dividend by remember(id){mutableStateOf(existing?.optString("dividendOption") ?: "Compounded")}
    var date by remember(id){mutableStateOf(existing?.optString("initialPaymentDate") ?: "")}
    var target by remember(id){mutableStateOf(if(existing?.has("monthlyTarget")==true)
        inputMoney(existing.getLong("monthlyTarget")) else "")}
    var active by remember(id){mutableStateOf(existing?.optBoolean("active") ?: true)}
    var error by remember{mutableStateOf<String?>(null)}
    val baseline=remember(id){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){if(model.savedMutationSequence>baseline)onClose()}
    AlertDialog(onDismissRequest={if(!model.loading)onClose()},
        title={Text(if(existing==null)"Add MP2 account" else "Edit MP2 account")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            PhField("Account name",name,{name=it})
            PhField("MP2 account number (optional)",number,{number=it})
            PhSelection("Dividend option",dividend,listOf("Compounded","Annual")){dividend=it}
            PhField("Initial payment YYYY-MM-DD (optional)",date,{date=it})
            PhField("Monthly target PHP (optional)",target,{target=it},money=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("Active",modifier=Modifier.padding(top=13.dp))
                Switch(checked=active,onCheckedChange={active=it})
            }
            PhError(error,model.error)
        }},
        confirmButton={Button(onClick={
            try{
                val fields=JSONObject().put("name",name).put("accountNumber",number)
                    .put("dividendOption",dividend).put("initialPaymentDate",date)
                    .put("active",active)
                if(target.isNotBlank())fields.put("monthlyTarget",parseMinor(target))
                model.philippineMutation(PhilippineCommand.SaveMp2Account(fields,existing?.optString("id")))
                error=null
            }catch(e:Exception){error=e.message ?: "Invalid MP2 account"}
        },enabled=!model.loading && !model.syncBlocked){Text("Save")}},
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}})
}

@Composable
private fun Mp2DepositDialog(model:FinanceViewModel,account:JSONObject,onClose:()->Unit){
    val id=account.getString("id")
    var amount by remember(id){mutableStateOf("")}
    var date by remember(id){mutableStateOf(LocalDate.now().toString())}
    var reference by remember(id){mutableStateOf("")}
    var notes by remember(id){mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    val baseline=remember(id){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){if(model.savedMutationSequence>baseline)onClose()}
    AlertDialog(onDismissRequest={if(!model.loading)onClose()},
        title={Text("Record MP2 deposit")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(account.getString("name"),style=MaterialTheme.typography.titleSmall)
            PhField("Deposit PHP",amount,{amount=it},money=true)
            PhField("Payment date YYYY-MM-DD",date,{date=it})
            PhField("Reference (optional)",reference,{reference=it})
            PhField("Notes (optional)",notes,{notes=it},single=false)
            Text("MP2 deposits are savings movements, not AED expenses.",
                style=MaterialTheme.typography.bodySmall)
            PhError(error,model.error)
        }},
        confirmButton={Button(onClick={
            try{
                val row=JSONObject().put("amount",positiveInput(amount))
                    .put("paymentDate",date).put("referenceNumber",reference).put("notes",notes)
                model.philippineMutation(PhilippineCommand.AddMp2Deposit(id,row))
                error=null
            }catch(e:Exception){error=e.message ?: "Invalid deposit"}
        },enabled=!model.loading && !model.syncBlocked){Text("Record")}},
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}})
}
