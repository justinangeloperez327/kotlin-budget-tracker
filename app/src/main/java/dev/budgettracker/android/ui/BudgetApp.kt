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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
        var showAccount by remember { mutableStateOf(false) }
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
                        TextButton(onClick = { showAccount = true }) {
                            Text(if (model.accountConfigured) "Account" else "Sign in")
                        }
                        TextButton(onClick = { picker = true }) { Text("Theme") }
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (model.accountConfigured)
                                (model.accountEmail?.let { "Web account: " + it } ?: "Web account: refresh required")
                            else "Device-only mode · not synced",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f).padding(top = 11.dp)
                        )
                        if (model.accountConfigured) {
                            TextButton(onClick = model::reload, enabled = !model.loading) { Text("Refresh") }
                        }
                    }
                    if (model.syncBlocked) {
                        Text("Server changes may exist. Refresh before editing; no automatic retry.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
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
                        "Budget vs. actual" -> BudgetPlanScreen(model)
                        "Expense tracker" -> ExpenseLedgerScreen(model)
                        "Bills & recurring" -> TrackerScreen(model, dev.budgettracker.android.domain.TrackerKind.BILL)
                        "Debt / Utang" -> TrackerScreen(model, dev.budgettracker.android.domain.TrackerKind.DEBT)
                        "Savings goals" -> TrackerScreen(model, dev.budgettracker.android.domain.TrackerKind.SAVINGS)
                        else -> Placeholder(page)
                    }
                }
            }
        }
        if (picker) ThemePicker(model) { picker = false }
        if (showAccount) AccountDialog(model) { showAccount = false }
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


@Composable
private fun AccountDialog(model: FinanceViewModel, onClose: () -> Unit) {
    var registration by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(model.serverOrigin) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val alreadySignedInWhenOpened = remember { model.accountConfigured }
    LaunchedEffect(model.accountEmail) {
        if (!alreadySignedInWhenOpened && model.accountEmail != null) {
            password = ""
            onClose()
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (model.accountConfigured) "Web account" else
            if (registration) "Register account" else "Sign in to web account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.accountConfigured) {
                    Text(model.accountEmail ?: "Session saved; refresh needed",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Edits use the existing Next.js PostgreSQL account. The device-only records stay separate.")
                    if (model.syncBlocked) {
                        Text("Sync is unavailable. Refresh before saving.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { model.reload() }, enabled = !model.loading) {
                        Text("Refresh account")
                    }
                    TextButton(onClick = { model.signOut(); onClose() }, enabled = !model.loading) {
                        Text("Sign out from this device")
                    }
                } else {
                    Text("Sign in using the account from next-budget-tracker. Enter the HTTPS origin where its mobile API is hosted.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = url, onValueChange = { url = it },
                        label = { Text("Server URL (https://...)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it },
                        label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth())
                    if (registration) OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { model.signIn(url, email, password, registration, name) },
                        enabled = !model.loading && email.isNotBlank() && password.isNotBlank() &&
                            url.isNotBlank() && (!registration || name.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (registration) "Create account" else "Sign in") }
                    TextButton(onClick = { registration = !registration }) {
                        Text(if (registration) "Already registered? Sign in" else "New account? Register")
                    }
                    Text("Device-only budgets and expenses are not uploaded automatically. Signing in switches to your server workspace.",
                        style = MaterialTheme.typography.bodySmall)
                    if (model.error != null) Text(model.error ?: "",
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
