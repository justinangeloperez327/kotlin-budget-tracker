package dev.budgettracker.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.domain.Currency
import dev.budgettracker.android.domain.Money
import dev.budgettracker.android.domain.parseMinor

internal fun php(minor: Long): String = "PHP " + Money(Currency.PHP,minor).decimal().toPlainString()
internal fun aedMoney(minor: Long): String = "AED " + Money(Currency.AED,minor).decimal().toPlainString()
internal fun inputMoney(minor: Long): String = Money(Currency.PHP,minor).decimal().toPlainString()
internal fun positiveInput(text: String): Long = parseMinor(text).also { require(it>0L) }
internal fun optionalInput(text:String): Long? = text.takeIf { it.isNotBlank() }?.let(::parseMinor)

@Composable
internal fun PhField(
    label: String, value: String, onValue: (String)->Unit,
    money: Boolean = false, single: Boolean = true
) {
    OutlinedTextField(
        value=value,onValueChange=onValue,label={Text(label)},
        singleLine=single,modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(keyboardType=if(money)KeyboardType.Decimal else KeyboardType.Text)
    )
}
@Composable
internal fun PhSelection(label:String,value:String,choices:List<String>,set:(String)->Unit){
    var open by remember {mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){
            Text(label+": "+value+" ▾")
        }
        DropdownMenu(expanded=open,onDismissRequest={open=false}){
            choices.forEach { option->
                DropdownMenuItem(text={Text(option)},onClick={set(option);open=false})
            }
        }
    }
}

@Composable
internal fun PhError(local:String?,remote:String?){
    if(local!=null)Text(local,color=MaterialTheme.colorScheme.error)
    if(remote!=null)Text(remote,color=MaterialTheme.colorScheme.error)
}
