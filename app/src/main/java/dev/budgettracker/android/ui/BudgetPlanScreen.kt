package dev.budgettracker.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.*
import java.util.Locale

@Composable
fun BudgetPlanScreen(model: FinanceViewModel) {
    val snapshot = model.data
    val report = remember(snapshot) { BudgetFeatures.report(snapshot) }
    var budget by remember(model.month, snapshot.budget) {
        mutableStateOf(Money(Currency.AED, snapshot.budget).decimal().toPlainString())
    }
    val fields = remember(model.month, snapshot.allocations) {
        mutableStateMapOf<Category, String>().apply {
            Category.entries.forEach { category ->
                put(category, Money(Currency.AED, snapshot.allocations[category] ?: 0L).decimal().toPlainString())
            }
        }
    }
    val budgetValid=runCatching { parseMinor(budget) }.isSuccess
    val invalidAllocations=Category.entries.filter { category ->
        runCatching { parseMinor(fields[category] ?: "") }.isFailure
    }
    val planValid=budgetValid && invalidAllocations.isEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { model.moveMonth(-1L) }) { Text("‹ Previous") }
            Text(model.month.toString(), modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { model.moveMonth(1L) }) { Text("Next ›") }
        }
        Text("Budget plan", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(budget, { budget = it }, label = { Text("Monthly budget AED") },
            isError=budget.isNotBlank() && !budgetValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("budget:total"))
        Category.entries.forEach { category ->
            OutlinedTextField(fields[category] ?: "0.00", { fields[category] = it },
                label = { Text(category.label + " allocation AED") },
                isError=category in invalidAllocations,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("budget:"+category.name))
        }
        if(!planValid)Text("Enter valid non-negative AED amounts (max two decimals) for all fields.",
            color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Button(onClick = { model.savePlan(budget, fields.toMap()) },
            enabled = !model.loading && !model.syncBlocked && planValid,
            modifier = Modifier.fillMaxWidth().testTag("budget:save")) { Text("Save complete plan") }
        HorizontalDivider()
        Text("Budget vs. actual", style = MaterialTheme.typography.titleLarge)
        arrayOf(
            "Monthly budget" to report.budget,
            "Actual spent" to report.actual,
            "Net variance" to report.variance,
            "Unallocated" to report.unallocated,
            "Category saved" to report.categorySaved,
            "Category overspent" to report.categoryOverspent
        ).forEach { (label, amount) ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, modifier = Modifier.weight(1f))
                    Text((if (amount < 0) "−" else "") +
                        "AED " + Money(Currency.AED, kotlin.math.abs(amount)).decimal().toPlainString())
                }
            }
        }
        Text("Budget used: " + String.format(Locale.US, "%.1f%%", report.usedRate))
        Text("Category spending", style = MaterialTheme.typography.titleMedium)
        report.categories.forEach { category ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(category.category.label, style = MaterialTheme.typography.titleSmall)
                    val fraction = (category.actual.toDouble() / maxOf(1L, category.actual, category.limit))
                        .toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("Spent AED " + Money(Currency.AED, category.actual).decimal().toPlainString() +
                        " / Allocated AED " + Money(Currency.AED, category.limit).decimal().toPlainString())
                    Text(if (category.difference < 0) "Overspent" else if (category.difference > 0)
                        "Under budget" else "On budget",
                        color = if (category.difference < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Unspent budget is a variance, not a savings-account deposit.",
            style = MaterialTheme.typography.bodySmall)
    }
}
