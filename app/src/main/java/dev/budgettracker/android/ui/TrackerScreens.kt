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

internal fun trackerAmount(amount: Long): String =
    "AED " + Money(Currency.AED, amount).decimal().toPlainString()

@Composable
fun TrackerScreen(model: FinanceViewModel, kind: TrackerKind) {
    val data=model.trackerData
    val parents=TrackerEngine.rows(data,kind.records)
    val movements=TrackerEngine.rows(data,kind.movements)
    val editable=!model.loading && !model.syncBlocked
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<JSONObject?>(null) }
    var confirming by remember { mutableStateOf<TrackerCommand?>(null) }
    val total=parents.fold(0L){ sum,parent->
        Math.addExact(sum,parent.getLong(when(kind){
            TrackerKind.BILL->"amount";TrackerKind.DEBT->"originalAmount";TrackerKind.SAVINGS->"targetAmount"
        }))
    }
    val recorded=movements.fold(0L){sum,item->Math.addExact(sum,item.getLong("amount"))}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(when(kind){
            TrackerKind.BILL->"Bills & recurring"
            TrackerKind.DEBT->"Debts / Utang"
            TrackerKind.SAVINGS->"Savings goals"
        },style=MaterialTheme.typography.titleLarge)
        Text(when(kind) {
            TrackerKind.BILL->"Scheduled bills do not count as expenses until payment."
            TrackerKind.DEBT->"Repayments create expenses; borrowed principal does not."
            TrackerKind.SAVINGS->"Deposits are transfers to savings, not expenses."
        },style=MaterialTheme.typography.bodySmall)
        Button(onClick={selected=null;showEditor=true},enabled=editable){Text("Add new")}
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)){
                Text("Total planned · "+trackerAmount(total))
                Text("Recorded movements · "+trackerAmount(recorded))
            }
        }
        if(parents.isEmpty())Text("No records yet. Add your first item.")
        parents.forEach { parent->
            val id=parent.getString("id")
            val entries=movements.filter{it.getString(kind.foreignKey)==id}
                .sortedByDescending{it.getString(if(kind==TrackerKind.SAVINGS)"depositDate" else "paymentDate")}
            val status=TrackerEngine.status(data,kind,parent,LocalDate.now())
            val remaining=if(kind==TrackerKind.BILL)0L else TrackerEngine.remaining(data,kind,id)
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(parent.getString("name"),style=MaterialTheme.typography.titleMedium)
                    Text(status,color=if(status=="Overdue"||status=="Past due")
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if(kind==TrackerKind.BILL){
                        Text("Scheduled "+trackerAmount(parent.getLong("amount"))+" · "+parent.getString("frequency"))
                        if(TrackerEngine.occurs(parent,YearMonth.now()))
                            Text("Due "+TrackerEngine.dueDate(parent,YearMonth.now()).toString())
                    }else{
                        val target=parent.getLong(if(kind==TrackerKind.DEBT)"originalAmount" else "targetAmount")
                        val paid=TrackerEngine.paid(data,kind,id)
                        Text("Recorded "+trackerAmount(paid)+" of "+trackerAmount(target))
                        LinearProgressIndicator(progress={ (paid.toDouble()/target).toFloat().coerceIn(0f,1f) },
                            modifier=Modifier.fillMaxWidth())
                        Text("Remaining "+trackerAmount(remaining))
                        if(parent.has("monthlyTarget"))
                            Text("Monthly target "+trackerAmount(parent.getLong("monthlyTarget")))
                    }
                    Row{
                        TextButton(onClick={selected=parent;showEditor=true},enabled=editable){Text("Edit")}
                        TextButton(onClick={paying=parent},
                            enabled=editable && parent.optBoolean("active") &&
                                (kind==TrackerKind.BILL||remaining>0L)){
                            Text(if(kind==TrackerKind.SAVINGS)"Deposit" else "Pay")
                        }
                        if(entries.isEmpty())TextButton(
                            onClick={confirming=TrackerCommand.Delete(kind,id)},enabled=editable){
                            Text("Delete",color=MaterialTheme.colorScheme.error)
                        }
                    }
                    if(entries.isNotEmpty()){
                        HorizontalDivider()
                        Text("Payment / deposit history",style=MaterialTheme.typography.titleSmall)
                        entries.forEach{ row->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                Column(Modifier.weight(1f)){
                                    Text(trackerAmount(row.getLong("amount")))
                                    Text(row.getString(if(kind==TrackerKind.SAVINGS)"depositDate" else "paymentDate")+
                                        if(kind==TrackerKind.BILL)" · period "+row.getString("period") else "",
                                        style=MaterialTheme.typography.bodySmall)
                                    if(row.has("referenceNumber"))Text("Ref "+row.getString("referenceNumber"),
                                        style=MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick={
                                    confirming=TrackerCommand.Undo(kind,row.getString("id"))
                                },enabled=editable){Text("Undo")}
                            }
                        }
                    }
                }
            }
        }
    }
    if(showEditor)TrackerDetailsDialog(model,kind,selected){showEditor=false;selected=null}
    paying?.let { parent->TrackerMovementDialog(model,kind,parent){paying=null} }
    confirming?.let { action->
        val started=remember(action){model.savedMutationSequence}
        LaunchedEffect(model.savedMutationSequence){
            if(model.savedMutationSequence>started)confirming=null
        }
        AlertDialog(onDismissRequest={if(!model.loading)confirming=null},
            title={Text(if(action is TrackerCommand.Undo)"Remove movement?" else "Delete tracker record?")},
            text={Text(if(action is TrackerCommand.Undo && kind!=TrackerKind.SAVINGS)
                "The payment and its linked expense will both be removed."
                else if(action is TrackerCommand.Undo)"This removes the savings deposit."
                else "Records with payment history cannot be deleted; deactivate them instead.")},
            confirmButton={
                Button(onClick={model.trackerMutation(action)},enabled=editable){Text("Confirm")}
            },
            dismissButton={TextButton(onClick={confirming=null},enabled=!model.loading){Text("Cancel")}}
        )
    }
}
