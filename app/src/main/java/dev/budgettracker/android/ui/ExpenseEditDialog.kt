package dev.budgettracker.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import dev.budgettracker.android.domain.parseMinor
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Currency
import dev.budgettracker.android.domain.Expense
import dev.budgettracker.android.domain.Money
import java.time.LocalDate

@Composable
fun ExpenseEditDialog(model: FinanceViewModel, expense: Expense?, onClose: () -> Unit) {
    var description by rememberSaveable(expense?.id) { mutableStateOf(expense?.description ?: "") }
    var amount by rememberSaveable(expense?.id) {
        mutableStateOf(expense?.let { Money(Currency.AED, it.amount).decimal().toPlainString() } ?: "")
    }
    var date by rememberSaveable(expense?.id) { mutableStateOf(expense?.date?.toString() ?: LocalDate.now().toString()) }
    var category by rememberSaveable(expense?.id) { mutableStateOf(expense?.category ?: Category.FOOD) }
    var expanded by remember { mutableStateOf(false) }
    val amountValid=runCatching { parseMinor(amount) }.getOrNull()?.let { it>0L } == true
    val dateValid=runCatching { LocalDate.parse(date.trim()) }.getOrNull()?.let {
        it.year in 1900..2199
    } == true
    val descriptionValid=description.trim().isNotEmpty() && description.trim().length<=120
    val starting = remember(expense?.id) { model.savedExpenseSequence }
    LaunchedEffect(model.savedExpenseSequence) {
        if (model.savedExpenseSequence > starting) onClose()
    }
    AlertDialog(
        onDismissRequest = { if (!model.loading) onClose() },
        title = { Text(if (expense == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Record an expense in AED.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(description, { description = it },
                    label = { Text("Description") }, singleLine = true,
                    isError=description.isNotEmpty() && !descriptionValid,
                    supportingText={Text(description.length.toString()+" / 120 characters")},
                    modifier = Modifier.fillMaxWidth().testTag("expense:description"))
                OutlinedTextField(amount, { amount = it },
                    label = { Text("Amount (AED)") },
                    isError=amount.isNotBlank() && !amountValid,
                    supportingText={if(amount.isNotBlank() && !amountValid)
                        Text("Enter a positive AED amount with at most two decimals")},
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("expense:amount"))
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.label + " ▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Category.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) },
                                onClick = { category = option; expanded = false })
                        }
                    }
                }
                OutlinedTextField(date, { date = it },
                    label = { Text("Date YYYY-MM-DD") }, singleLine = true,
                    isError=!dateValid,
                    supportingText={if(!dateValid)Text("Use a valid date: YYYY-MM-DD")},
                    modifier = Modifier.fillMaxWidth().testTag("expense:date"))
                if (model.error != null) Text(model.error ?: "",
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (expense == null) model.addExpense(description, amount, category, date)
                else model.editExpense(expense.id, description, amount, category, date)
            }, enabled = !model.loading && !model.syncBlocked &&
                descriptionValid && amountValid && dateValid,
                modifier=Modifier.testTag("expense:save")) { Text("Save expense") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !model.loading) { Text("Cancel") }
        }
    )
}
