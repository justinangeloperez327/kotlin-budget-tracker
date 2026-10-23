package dev.budgettracker.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.BudgetCalculator
import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Currency
import dev.budgettracker.android.domain.ExpenseSource
import dev.budgettracker.android.domain.Money
import java.time.LocalDate
import kotlinx.coroutines.launch

private val sections = listOf(
    "Dashboard", "Financial overview", "Reminders", "Budget vs. actual", "Expense tracker",
    "Bills & recurring", "Debt / Utang", "Savings goals", "Remittances", "Contributions",
    "SSS", "PhilHealth", "Pag-IBIG", "MP2", "Reports"
)
private val themes = linkedMapOf("sakura" to "Sakura", "kuromi" to "Kuromi",
    "keroppi" to "Keroppi", "vegeta" to "Vegeta", "naruto" to "Naruto",
    "kakashi" to "Kakashi", "sasuke" to "Sasuke", "ichigo" to "Ichigo")
private fun accent(name: String) = when(name) {
    "kuromi" -> Color(0xFFA34DAF)
    "keroppi" -> Color(0xFF418854)
    "vegeta" -> Color(0xFF365CC4)
    "naruto" -> Color(0xFFC36322)
    "kakashi" -> Color(0xFF60817B)
    "sasuke" -> Color(0xFF6660BB)
    "ichigo" -> Color(0xFFD46829)
    else -> Color(0xFFAD426D)
}
private fun aed(value: Long) = "AED " + Money(Currency.AED, value.coerceAtLeast(0)).decimal().toPlainString()
private fun variance(value: Long) = if (value < 0) "Over by " + aed(-value) else aed(value) + " remaining"

@Composable
fun BudgetApp(model: FinanceViewModel) {
    val dark = when(model.appearance) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val p = accent(model.theme)
    val scheme = if (dark) darkColorScheme(primary = p, background = Color(0xFF19161B), surface = Color(0xFF28232A))
        else lightColorScheme(primary = p, background = Color(0xFFFFF9FC), surface = Color.White)
    MaterialTheme(colorScheme = scheme) {
        var page by rememberSaveable { mutableStateOf("Dashboard") }
        var picker by remember { mutableStateOf(false) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(drawerState = drawer, drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("✿ Budget Tracker", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                    Text("UAE and Philippine finances", modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall)
                    sections.forEach { section ->
                        NavigationDrawerItem(label = { Text(section) }, selected = page == section,
                            onClick = { page = section; scope.launch { drawer.close() } })
                    }
                }
            }
        }) {
            Scaffold(topBar = {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { scope.launch { drawer.open() } }) { Text("☰ Menu") }
                        TextButton(onClick = { picker = true }) { Text("Appearance") }
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(page, style = MaterialTheme.typography.headlineSmall)
                        if (model.loading) CircularProgressIndicator(Modifier.size(22.dp))
                    }
                    HorizontalDivider()
                }
            }) { inner ->
                Column(Modifier.padding(inner).fillMaxSize()) {
                    if (model.error != null) {
                        Card(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(Modifier.padding(10.dp)) {
                                Text(model.error ?: "Error", color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f))
                                TextButton(onClick = model::dismissError) { Text("Dismiss") }
                            }
                        }
                    }
                    when(page) {
                        "Dashboard" -> Dashboard(model)
                        "Budget vs. actual" -> Budgets(model)
                        "Expense tracker" -> Expenses(model)
                        else -> Placeholder(page)
                    }
                }
            }
        }
        if (picker) ThemePicker(model) { picker = false }
    }
}

@Composable
private fun MonthBar(model: FinanceViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { model.moveMonth(-1L) }) { Text("‹ Previous") }
        Text(model.month.toString(), modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { model.moveMonth(1L) }) { Text("Next ›") }
    }
}

@Composable
private fun Stat(label: String, amount: String, explanation: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(amount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp))
            Text(explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Dashboard(model: FinanceViewModel) {
    val s = BudgetCalculator.summarize(model.data)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        MonthBar(model)
        Text("Your monthly financial picture", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 12.dp))
        Stat("Monthly budget", aed(s.budget), "AED spending plan")
        Stat("Total spent", aed(s.spent), model.data.expenses.size.toString() + " expenses recorded")
        Stat("Remaining", variance(s.variance), "Budget minus actual")
        Stat("Bills, debts and savings", "Not connected", "Additional original tracker modules will be ported")
        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Philippine finances (PHP)", style = MaterialTheme.typography.titleMedium)
                Text("SSS, PhilHealth, Pag-IBIG and MP2 are not yet connected. AED and PHP totals remain separate.",
                    modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun Budgets(model: FinanceViewModel) {
    val data = model.data
    val s = BudgetCalculator.summarize(data)
    var budgetText by remember(model.month, data.budget) {
        mutableStateOf(Money(Currency.AED, data.budget).decimal().toPlainString())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        MonthBar(model)
        Text("Monthly spending plan", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(budgetText, { budgetText = it }, label = { Text("AED budget") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { model.saveBudget(budgetText) }, enabled = !model.loading,
            modifier = Modifier.padding(vertical = 12.dp)) { Text("Save budget") }
        Stat("Spent", aed(s.spent), variance(s.variance))
        Stat("Unallocated", aed(s.budget - s.allocated), "Remaining to assign to categories")
        Text("Category budgets", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp))
        Category.entries.forEach { category ->
            val saved = data.allocations[category] ?: 0L
            var input by remember(model.month, category, saved) {
                mutableStateOf(Money(Currency.AED, saved).decimal().toPlainString())
            }
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(category.label, fontWeight = FontWeight.SemiBold)
                    Text("Spent: " + aed(BudgetCalculator.categorySpent(data, category)),
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(input, { input = it }, label = { Text("AED limit") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { model.saveAllocation(category, input) }, enabled = !model.loading,
                            modifier = Modifier.padding(top = 8.dp)) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Expenses(model: FinanceViewModel) {
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var category by rememberSaveable { mutableStateOf(Category.FOOD.name) }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        MonthBar(model)
        Text("Add an expense", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(description, {description=it}, label={Text("Description")},
            singleLine=true, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(amount, {amount=it}, label={Text("Amount AED")},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
            singleLine=true, modifier=Modifier.fillMaxWidth().padding(top=8.dp))
        Row {
            TextButton(onClick={expanded=true}){Text(Category.valueOf(category).label+" ▾")}
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                Category.entries.forEach { cat ->
                    DropdownMenuItem(text={Text(cat.label)},onClick={category=cat.name;expanded=false})
                }
            }
        }
        OutlinedTextField(date,{date=it},label={Text("Date (YYYY-MM-DD)")},
            singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            model.addExpense(description,amount,Category.valueOf(category),date)
            description=""
            amount=""
        },enabled=!model.loading && description.isNotBlank() && amount.isNotBlank(),
            modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)){Text("Save expense")}
        HorizontalDivider()
        Text("Transactions for "+model.month,style=MaterialTheme.typography.titleMedium,
            modifier=Modifier.padding(vertical=14.dp))
        if(model.data.expenses.isEmpty()) Text("No expenses this month. New accounts start empty.")
        model.data.expenses.forEach { e ->
            Card(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                Row(Modifier.padding(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(e.description,fontWeight=FontWeight.SemiBold)
                        Text(e.category.label+" • "+e.date,style=MaterialTheme.typography.bodySmall)
                        Text(aed(e.amount),color=MaterialTheme.colorScheme.primary)
                    }
                    if(e.source==ExpenseSource.MANUAL){
                        TextButton(onClick={model.deleteExpense(e.id)},enabled=!model.loading){Text("Delete")}
                    }
                }
            }
        }
    }
}

@Composable
private fun Placeholder(name:String) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text(name,style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("This is a feature of next-budget-tracker. Its Android screens, transaction rules and data have not been ported yet.")
        Spacer(Modifier.height(10.dp))
        Text("No example balances are presented as real financial records.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThemePicker(model:FinanceViewModel,onClose:()->Unit) {
    AlertDialog(
        onDismissRequest=onClose,
        title={Text("Choose your theme")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Appearance",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    listOf("light","dark","system").forEach { mode ->
                        FilterChip(selected=model.appearance==mode,
                            onClick={model.selectAppearance(mode)},label={Text(mode)})
                    }
                }
                Text("Character palette",style=MaterialTheme.typography.titleSmall)
                themes.forEach { (key,label) ->
                    FilterChip(selected=model.theme==key,onClick={model.selectTheme(key)},
                        label={Text(label)})
                }
                Text("Companion illustrations will follow in the UI-parity phase.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={TextButton(onClick=onClose){Text("Done")}}
    )
}
