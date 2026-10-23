package dev.budgettracker.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.*
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject

@Composable
fun TrackerDetailsDialog(
    model: FinanceViewModel, kind: TrackerKind, existing: JSONObject?, onClose: () -> Unit
) {
    fun old(field:String)=existing?.optString(field) ?: ""
    val key=existing?.optString("id") ?: "new"
    var name by remember(key){mutableStateOf(old("name"))}
    var amount by remember(key) {
        val field=when(kind){TrackerKind.BILL->"amount";TrackerKind.DEBT->"originalAmount";TrackerKind.SAVINGS->"targetAmount"}
        mutableStateOf(if(existing?.has(field)==true)
            Money(Currency.AED,existing.getLong(field)).decimal().toPlainString() else "")
    }
    var lender by remember(key){mutableStateOf(old("lender"))}
    var category by remember(key){
        mutableStateOf(Category.entries.firstOrNull{it.label==old("category")} ?: Category.OTHER)
    }
    var categoryOpen by remember{mutableStateOf(false)}
    var frequency by remember(key){mutableStateOf(old("frequency").ifBlank{"Monthly"})}
    var frequencyOpen by remember{mutableStateOf(false)}
    var dueDay by remember(key){mutableStateOf(old("dueDay").ifBlank{"1"})}
    var startMonth by remember(key){mutableStateOf(old("startMonth").ifBlank{YearMonth.now().toString()})}
    var endMonth by remember(key){mutableStateOf(old("endMonth"))}
    var startDate by remember(key){mutableStateOf(old("startDate").ifBlank{LocalDate.now().toString()})}
    var finalDate by remember(key){
        mutableStateOf(old(if(kind==TrackerKind.DEBT)"dueDate" else "targetDate"))
    }
    var monthlyTarget by remember(key) {
        mutableStateOf(if(existing?.has("monthlyTarget")==true)
            Money(Currency.AED,existing.getLong("monthlyTarget")).decimal().toPlainString()
            else "")
    }
    var destination by remember(key){mutableStateOf(old("destination"))}
    var notes by remember(key){mutableStateOf(old("notes"))}
    var active by remember(key){mutableStateOf(existing?.optBoolean("active") ?: true)}
    var error by remember{mutableStateOf<String?>(null)}
    val baseline=remember(key){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){
        if(model.savedMutationSequence>baseline)onClose()
    }
    AlertDialog(
        onDismissRequest={if(!model.loading)onClose()},
        title={Text((if(existing==null)"Add " else "Edit ") + when(kind){
            TrackerKind.BILL->"recurring bill";TrackerKind.DEBT->"debt";TrackerKind.SAVINGS->"savings goal"
        })},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)){
                OutlinedTextField(name,{name=it},label={Text("Name")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(amount,{amount=it},label={Text(when(kind) {
                    TrackerKind.BILL->"Recurring amount AED"
                    TrackerKind.DEBT->"Original balance AED"
                    TrackerKind.SAVINGS->"Savings target AED"
                })},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                if(kind==TrackerKind.DEBT)
                    OutlinedTextField(lender,{lender=it},label={Text("Lender")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                if(kind!=TrackerKind.SAVINGS){
                    Box{
                        OutlinedButton(onClick={categoryOpen=true}){Text("Category: "+category.label+" ▾")}
                        DropdownMenu(expanded=categoryOpen,onDismissRequest={categoryOpen=false}){
                            Category.entries.forEach{option->
                                DropdownMenuItem(text={Text(option.label)},
                                    onClick={category=option;categoryOpen=false})
                            }
                        }
                    }
                }
                if(kind==TrackerKind.BILL){
                    Box{
                        OutlinedButton(onClick={frequencyOpen=true}){Text("Frequency: "+frequency+" ▾")}
                        DropdownMenu(expanded=frequencyOpen,onDismissRequest={frequencyOpen=false}){
                            listOf("Monthly","Quarterly","Yearly").forEach { option->
                                DropdownMenuItem(text={Text(option)},onClick={
                                    frequency=option;frequencyOpen=false
                                })
                            }
                        }
                    }
                    OutlinedTextField(dueDay,{dueDay=it},label={Text("Due day (1–31)")},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(startMonth,{startMonth=it},label={Text("Start month YYYY-MM")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(endMonth,{endMonth=it},label={Text("End month (optional)")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                }else{
                    OutlinedTextField(startDate,{startDate=it},label={Text("Start date YYYY-MM-DD")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(finalDate,{finalDate=it},
                        label={Text(if(kind==TrackerKind.DEBT)"Due date (optional)" else "Target date (optional)")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(monthlyTarget,{monthlyTarget=it},
                        label={Text("Monthly target AED (optional)")},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                if(kind==TrackerKind.SAVINGS)
                    OutlinedTextField(destination,{destination=it},
                        label={Text("Destination / wallet (optional)")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text("Active",modifier=Modifier.padding(top=13.dp))
                    Switch(checked=active,onCheckedChange={active=it})
                }
                OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},
                    modifier=Modifier.fillMaxWidth(),minLines=2)
                if(error!=null)Text(error ?: "",color=MaterialTheme.colorScheme.error)
                if(model.error!=null)Text(model.error ?: "",color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={
            Button(onClick={
                try{
                    val fields=JSONObject().put("name",name.trim()).put("active",active)
                        .put("notes",notes.trim())
                    val value=parseMinor(amount)
                    when(kind){
                        TrackerKind.BILL->{
                            fields.put("amount",value).put("category",category.label)
                                .put("frequency",frequency).put("dueDay",dueDay.toInt())
                                .put("startMonth",startMonth).put("endMonth",endMonth)
                        }
                        TrackerKind.DEBT->{
                            fields.put("originalAmount",value).put("lender",lender.trim())
                                .put("category",category.label).put("startDate",startDate)
                                .put("dueDate",finalDate)
                            if(monthlyTarget.isNotBlank())
                                fields.put("monthlyTarget",parseMinor(monthlyTarget).toString())
                        }
                        TrackerKind.SAVINGS->{
                            fields.put("targetAmount",value).put("startDate",startDate)
                                .put("targetDate",finalDate).put("destination",destination)
                            if(monthlyTarget.isNotBlank())
                                fields.put("monthlyTarget",parseMinor(monthlyTarget).toString())
                        }
                    }
                    // Full validation and database/API mutation happen in one ViewModel write.
                    model.trackerMutation(TrackerCommand.Save(kind,fields,existing?.optString("id")))
                    error=null
                }catch(ex:Exception){error=ex.message ?: "Check the tracker details"}
            },enabled=!model.loading && !model.syncBlocked){Text("Save")}
        },
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}}
    )
}
