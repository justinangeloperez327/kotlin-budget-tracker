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
fun TrackerMovementDialog(model:FinanceViewModel,kind:TrackerKind,parent:JSONObject,onClose:()->Unit) {
    val id=parent.getString("id")
    var amount by remember(id){mutableStateOf(
        if(kind==TrackerKind.BILL)
            Money(Currency.AED,parent.getLong("amount")).decimal().toPlainString()
        else ""
    )}
    var date by remember(id){mutableStateOf(LocalDate.now().toString())}
    var period by remember(id){mutableStateOf(YearMonth.now().toString())}
    var reference by remember(id){mutableStateOf("")}
    var notes by remember(id){mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    val baseline=remember(id){model.savedMutationSequence}
    LaunchedEffect(model.savedMutationSequence){
        if(model.savedMutationSequence>baseline)onClose()
    }
    AlertDialog(onDismissRequest={if(!model.loading)onClose()},
        title={Text(when(kind) {
            TrackerKind.BILL->"Record bill payment"
            TrackerKind.DEBT->"Record debt repayment"
            TrackerKind.SAVINGS->"Record savings deposit"
        })},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text(parent.getString("name"),style=MaterialTheme.typography.titleSmall)
                OutlinedTextField(amount,{amount=it},label={Text("Amount AED")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                if(kind==TrackerKind.BILL)
                    OutlinedTextField(period,{period=it},label={Text("Bill period YYYY-MM")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(date,{date=it},label={Text("Payment / deposit date YYYY-MM-DD")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(reference,{reference=it},
                    label={Text("Reference (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},
                    modifier=Modifier.fillMaxWidth())
                Text(if(kind==TrackerKind.SAVINGS)"Deposit does not count as an expense."
                    else "Recording payment also creates one linked AED expense.",
                    style=MaterialTheme.typography.bodySmall)
                if(error!=null)Text(error ?: "",color=MaterialTheme.colorScheme.error)
                if(model.error!=null)Text(model.error ?: "",color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={
            Button(onClick={
                try{
                    model.trackerMutation(TrackerCommand.Record(kind,id,parseMinor(amount),
                        date,if(kind==TrackerKind.BILL)period else null,reference,notes))
                    error=null
                }catch(ex:Exception){error=ex.message ?: "Invalid payment"}
            },enabled=!model.loading && !model.syncBlocked){Text("Record")}
        },
        dismissButton={TextButton(onClick=onClose,enabled=!model.loading){Text("Cancel")}}
    )
}
