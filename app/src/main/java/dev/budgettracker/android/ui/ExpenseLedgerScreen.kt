package dev.budgettracker.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.*
import java.time.YearMonth

private fun formatAed(value: Long): String =
    "AED " + Money(Currency.AED, value).decimal().toPlainString()

@Composable
fun ExpenseLedgerScreen(model: FinanceViewModel) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<Category?>(null) }
    var monthText by remember { mutableStateOf("") }
    var filterOpen by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Expense?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val month = monthText.takeIf { it.isNotBlank() }?.let {
        runCatching { YearMonth.parse(it.trim()) }.getOrNull()
    }
    val validMonth = monthText.isBlank() || month != null
    val filtered = ExpenseFilter(search, categoryFilter, month).apply(model.allExpenses)
    val total = filtered.fold(0L) { sum, item -> Math.addExact(sum, item.amount) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) {
                    "The selected file cannot be written"
                }
                stream.use { it.write(ExpenseCsv.render(filtered).toByteArray(Charsets.UTF_8)) }
                exportError = null
            } catch (e: Exception) { exportError = e.message ?: "Export failed" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Expense tracker", style = MaterialTheme.typography.titleLarge)
        Text("Search your complete AED transaction history.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editor = null; showEditor = true },
                enabled = !model.loading && !model.syncBlocked, modifier = Modifier.weight(1f)) {
                Text("Add expense")
            }
            OutlinedButton(onClick = { export.launch("expenses.csv") },
                enabled = validMonth && filtered.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Export CSV")
            }
        }
        OutlinedTextField(search, { search = it }, label = { Text("Search expenses") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { filterOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text((categoryFilter?.label ?: "All") + " ▾")
                }
                DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                    DropdownMenuItem(text = { Text("All") },
                        onClick = { categoryFilter = null; filterOpen = false })
                    Category.entries.forEach { category ->
                        DropdownMenuItem(text = { Text(category.label) },
                            onClick = { categoryFilter = category; filterOpen = false })
                    }
                }
            }
            OutlinedTextField(monthText, { monthText = it }, label = { Text("Month YYYY-MM") },
                isError = !validMonth, singleLine = true, modifier = Modifier.weight(1f))
        }
        if (!validMonth) Text("Use YYYY-MM or leave month blank.",
            color = MaterialTheme.colorScheme.error)
        if (exportError != null) Text(exportError ?: "", color = MaterialTheme.colorScheme.error)
        Text((if (validMonth) filtered.size else 0).toString() +
            " expenses · " + formatAed(if (validMonth) total else 0L))
        HorizontalDivider()
        if (model.allExpenses.isEmpty()) Text("No expenses yet. Add your first expense.")
        else if (validMonth && filtered.isEmpty()) Text("No expenses match your filters.")
        if (validMonth) filtered.forEach { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.description, modifier = Modifier.weight(1f))
                        Text(formatAed(entry.amount))
                    }
                    Text(entry.category.label + " · " + entry.date, style = MaterialTheme.typography.bodySmall)
                    if (entry.source == ExpenseSource.MANUAL) {
                        Row {
                            TextButton(onClick = { editor = entry; showEditor = true },
                                enabled = !model.loading && !model.syncBlocked) { Text("Edit") }
                            TextButton(onClick = { deleting = entry },
                                enabled = !model.loading && !model.syncBlocked) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        val source = when (entry.source) {
                            ExpenseSource.BILL_PAYMENT -> "bill payment"
                            ExpenseSource.DEBT_PAYMENT -> "debt payment"
                            ExpenseSource.REMITTANCE -> "remittance"
                            else -> "another tracker"
                        }
                        Text("Managed by " + source, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showEditor) ExpenseEditDialog(model, editor) { showEditor = false; editor = null }
    val target = deleting
    if (target != null) {
        val started = remember(target.id) { model.savedMutationSequence }
        LaunchedEffect(model.savedMutationSequence) {
            if (model.savedMutationSequence > started) deleting = null
        }
        AlertDialog(onDismissRequest = { if (!model.loading) deleting = null },
            title = { Text("Delete expense?") },
            text = { Text("Permanently delete " + target.description + "? This cannot be undone.") },
            confirmButton = {
                Button(onClick = { model.deleteExpense(target.id) },
                    enabled = !model.loading && !model.syncBlocked) { Text("Delete expense") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, enabled = !model.loading) { Text("Cancel") }
            })
    }
}
