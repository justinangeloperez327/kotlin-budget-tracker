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
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun currencyAmount(currency: Currency, minor: Long): String {
    val value=Money(currency,kotlin.math.abs(minor)).decimal().toPlainString()
    return (if(minor<0L)"−" else "")+currency.name+" "+value
}

@Composable
private fun FinanceMetric(label:String,currency:Currency,value:Long,subtitle:String?=null) {
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text(label,style=MaterialTheme.typography.labelLarge)
            Text(currencyAmount(currency,value),style=MaterialTheme.typography.titleLarge)
            if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReportMonthPicker(month:YearMonth,onMonth:(YearMonth)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        TextButton(onClick={onMonth(month.minusMonths(1))}){Text("‹ Previous")}
        Text(month.toString(),style=MaterialTheme.typography.titleMedium,
            modifier=Modifier.padding(top=14.dp))
        TextButton(onClick={onMonth(month.plusMonths(1))}){Text("Next ›")}
    }
}

@Composable
fun FinancialOverviewScreen(model:FinanceViewModel,onNavigate:(String)->Unit) {
    val today=LocalDate.now()
    var selected by remember {mutableStateOf(model.month)}
    val overview=remember(model.trackerData,selected,today) {
        FinancialAnalytics.overview(model.trackerData,selected,today)
    }
    val snap=overview.month
    val reminders=remember(model.trackerData,today) {
        FinancialReminders.generate(model.trackerData,today)
    }
    val counts=FinancialReminders.counts(reminders)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Financial overview",style=MaterialTheme.typography.titleLarge)
        Text("Historical figures stop at the earlier of the month end or today. AED and PHP are not added together.",
            style=MaterialTheme.typography.bodySmall)
        ReportMonthPicker(selected){selected=it}
        Text("AED · budget and commitments",style=MaterialTheme.typography.titleMedium)
        FinanceMetric("Planned budget",Currency.AED,snap.budget)
        FinanceMetric("Spent",Currency.AED,snap.spent,
            String.format(Locale.US,"%.1f%% of budget used",snap.usedRate))
        FinanceMetric("Net budget variance",Currency.AED,snap.variance,
            "A positive unspent amount is not a cash savings deposit.")
        FinanceMetric("Unpaid scheduled bills",Currency.AED,overview.billsOutstanding,
            overview.overdueBills.toString()+" overdue as of "+snap.asOf)
        FinanceMetric("Remaining debt",Currency.AED,overview.debtRemaining,
            overview.overdueDebts.toString()+" overdue debts")
        FinanceMetric("Savings recorded",Currency.AED,overview.savingsBalance,
            "Remaining goals: "+currencyAmount(Currency.AED,overview.savingsRemaining))
        FinanceMetric("Completed remittance principal",Currency.AED,snap.remitted)
        FinanceMetric("Pending remittance principal",Currency.AED,snap.pendingRemittances)
        FinanceMetric("Remittance fees",Currency.AED,snap.remittanceFees)
        FinanceMetric("Own-account transfers",Currency.AED,snap.ownTransfers,
            "Already part of remittances; never added to consumption spending.")
        Text("PHP · recorded activity",style=MaterialTheme.typography.titleMedium)
        FinanceMetric("Government paid in selected month",Currency.PHP,snap.governmentPaid)
        FinanceMetric("Government paid YTD as of period",Currency.PHP,overview.governmentYtdPaid,
            overview.contributionGaps.toString()+" missing expected-period records")
        FinanceMetric("MP2 saved in selected month",Currency.PHP,snap.mp2Saved)
        FinanceMetric("MP2 total saved as of period",Currency.PHP,overview.mp2TotalSaved)
        FinanceMetric("Completed remittances received",Currency.PHP,snap.remittanceReceived)
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Attention",style=MaterialTheme.typography.titleMedium)
                Text(counts.overdue.toString()+" overdue · "+counts.dueSoon+" due soon · "+
                    counts.pending+" pending")
                TextButton(onClick={onNavigate("Reminders")}){Text("Open reminders")}
            }
        }
    }
}

@Composable
fun FinancialReportsScreen(model:FinanceViewModel) {
    val today=LocalDate.now()
    val data=model.trackerData
    val years=remember(data,today){FinancialAnalytics.availableYears(data,today)}
    var year by remember {mutableIntStateOf(today.year)}
    var month by remember {mutableStateOf(model.month)}
    var historyMode by remember{mutableStateOf(false)}
    var exportError by remember{mutableStateOf<String?>(null)}
    var pendingExport by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null){
            try{
                val bytes=requireNotNull(pendingExport){ "No report selected" }.toByteArray(Charsets.UTF_8)
                val output=requireNotNull(context.contentResolver.openOutputStream(uri)) {
                    "Cannot open selected document for writing"
                }
                output.use{it.write(bytes)}
                exportError=null
            }catch(e:Exception){exportError=e.message ?: "CSV export failed"}
            finally{pendingExport=null}
        }else pendingExport=null
    }
    val monthly=remember(data,month,today){FinancialAnalytics.monthly(data,month,today)}
    val annual=remember(data,year,today){FinancialAnalytics.yearly(data,year,today)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Financial reports",style=MaterialTheme.typography.titleLarge)
        Text("All reporting uses actual recorded entries and respects the reporting cutoff. Amounts remain in their original currency.",
            style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={historyMode=false},modifier=Modifier.weight(1f)){
                Text("Monthly")
            }
            OutlinedButton(onClick={historyMode=true},modifier=Modifier.weight(1f)){
                Text("Yearly / History")
            }
        }
        if(!historyMode){
            ReportMonthPicker(month){month=it}
            Button(onClick={
                pendingExport=FinancialAnalytics.monthlyCsv(monthly)
                export.launch("finance-"+month+".csv")
            },modifier=Modifier.fillMaxWidth()){Text("Export monthly CSV")}
            FinanceMetric("Budget",Currency.AED,monthly.budget)
            FinanceMetric("Spending",Currency.AED,monthly.spent)
            FinanceMetric("Variance",Currency.AED,monthly.variance)
            Text("AED movements",style=MaterialTheme.typography.titleMedium)
            listOf(
                "Bills paid" to monthly.billsPaid,
                "Debt repaid" to monthly.debtRepaid,
                "Savings added" to monthly.savingsAdded,
                "Completed remittances" to monthly.remitted,
                "Pending remittances" to monthly.pendingRemittances,
                "Remittance fees" to monthly.remittanceFees,
                "Family support remitted" to monthly.supportRemitted,
                "Own-account transfers" to monthly.ownTransfers
            ).forEach{(label,value)->FinanceMetric(label,Currency.AED,value)}
            Text("PHP movements",style=MaterialTheme.typography.titleMedium)
            FinanceMetric("Government contributions paid",Currency.PHP,monthly.governmentPaid)
            FinanceMetric("MP2 savings",Currency.PHP,monthly.mp2Saved)
            FinanceMetric("Remittance received",Currency.PHP,monthly.remittanceReceived)
            Text("Spending by category",style=MaterialTheme.typography.titleMedium)
            Category.entries.forEach{category->
                val spent=monthly.categorySpent[category] ?: 0L
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(category.label+" · "+currencyAmount(Currency.AED,spent))
                        val fraction=if(monthly.spent>0L)
                            (spent.toDouble()/monthly.spent).toFloat().coerceIn(0f,1f) else 0f
                        LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    }
                }
            }
        }else{
            val options=if(year in years)years else (years+year).sortedDescending()
            var open by remember{mutableStateOf(false)}
            Box{
                OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){
                    Text("Year "+year+" ▾")
                }
                DropdownMenu(expanded=open,onDismissRequest={open=false}){
                    options.forEach{item->DropdownMenuItem(text={Text(item.toString())},
                        onClick={year=item;open=false})}
                }
            }
            Button(onClick={
                pendingExport=FinancialAnalytics.yearlyCsv(annual)
                export.launch("finance-"+year+".csv")
            },enabled=annual.months.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
                Text("Export yearly CSV")
            }
            if(annual.months.isEmpty())Text("No elapsed months available for this future year.")
            else {
                FinanceMetric("Year budget",Currency.AED,annual.totalBudget)
                FinanceMetric("Year spending",Currency.AED,annual.totalSpent)
                FinanceMetric("Net variance",Currency.AED,annual.netVariance)
                FinanceMetric("Gross under budget",Currency.AED,annual.totalUnspent,
                    "Sum of positive monthly budget differences, not deposits.")
                FinanceMetric("Gross overspent",Currency.AED,annual.totalOverspent)
                FinanceMetric("Bills paid",Currency.AED,annual.aed{it.billsPaid})
                FinanceMetric("Debt repaid",Currency.AED,annual.aed{it.debtRepaid})
                FinanceMetric("Savings deposited",Currency.AED,annual.aed{it.savingsAdded})
                FinanceMetric("Completed remittances",Currency.AED,annual.aed{it.remitted})
                FinanceMetric("Remittance fees",Currency.AED,annual.aed{it.remittanceFees})
                FinanceMetric("Government paid",Currency.PHP,annual.php{it.governmentPaid})
                FinanceMetric("MP2 deposited",Currency.PHP,annual.php{it.mp2Saved})
                FinanceMetric("Remittances received",Currency.PHP,annual.php{it.remittanceReceived})
                Text("Category performance",style=MaterialTheme.typography.titleMedium)
                FinancialAnalytics.categoryHistory(data,annual).forEach { category ->
                    if(category.activeMonths>0) {
                        Card(Modifier.fillMaxWidth()){
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(category.category.label,style=MaterialTheme.typography.titleSmall)
                                Text("Budget "+currencyAmount(Currency.AED,category.budget)+
                                    " · Actual "+currencyAmount(Currency.AED,category.actual))
                                Text("Net variance "+currencyAmount(Currency.AED,category.variance))
                                Text(category.activeMonths.toString()+" active months · "+
                                    category.underBudgetMonths+" under · "+category.overBudgetMonths+" over",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Text("Monthly budget trend",style=MaterialTheme.typography.titleMedium)
                annual.months.forEach{entry->
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                            Text(entry.month.toString(),style=MaterialTheme.typography.titleSmall)
                            Text("Budget "+currencyAmount(Currency.AED,entry.budget)+
                                " · Spent "+currencyAmount(Currency.AED,entry.spent))
                            val scale=maxOf(1L,entry.budget,entry.spent)
                            LinearProgressIndicator(
                                progress={(entry.spent.toDouble()/scale).toFloat().coerceIn(0f,1f)},
                                modifier=Modifier.fillMaxWidth())
                            Text("Variance "+currencyAmount(Currency.AED,entry.variance),
                                color=if(entry.variance<0)MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if(exportError!=null)Text(exportError ?: "",color=MaterialTheme.colorScheme.error)
        Text("Historical reports reflect the available workspace records only. They do not calculate currency conversions or external bank balances.",
            style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FinancialRemindersScreen(model:FinanceViewModel,onNavigate:(String)->Unit){
    val now=LocalDate.now()
    val reminders=remember(model.trackerData,now){
        FinancialReminders.generate(model.trackerData,now)
    }
    val summary=FinancialReminders.counts(reminders)
    var selection by remember{mutableStateOf("All")}
    val filtered=if(selection=="All")reminders else reminders.filter{it.severity==selection}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Reminders",style=MaterialTheme.typography.titleLarge)
        Text("In-app financial reminders based on recorded data as of "+now+
            ". No automatic payments or background notifications.",
            style=MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(summary.total.toString()+" reminders",style=MaterialTheme.typography.titleMedium)
                Text(summary.overdue.toString()+" overdue · "+summary.dueSoon+
                    " due soon · "+summary.pending+" pending · "+summary.info+" info")
            }
        }
        var dropdown by remember{mutableStateOf(false)}
        Box {
            OutlinedButton(onClick={dropdown=true},modifier=Modifier.fillMaxWidth()){
                Text("Show "+selection+" ▾")
            }
            DropdownMenu(expanded=dropdown,onDismissRequest={dropdown=false}){
                listOf("All","overdue","due-soon","pending","info").forEach{choice->
                    DropdownMenuItem(text={Text(choice)},onClick={selection=choice;dropdown=false})
                }
            }
        }
        if(filtered.isEmpty())Text("No reminders in this category.")
        filtered.forEach { reminder ->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text(reminder.source,style=MaterialTheme.typography.labelMedium)
                        Text(reminder.severity,style=MaterialTheme.typography.labelSmall,
                            color=if(reminder.severity=="overdue")MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                    }
                    Text(reminder.title,style=MaterialTheme.typography.titleMedium)
                    Text(reminder.detail,style=MaterialTheme.typography.bodySmall)
                    if(reminder.amount!=null && reminder.currency!=null)
                        Text(currencyAmount(reminder.currency,reminder.amount))
                    TextButton(onClick={onNavigate(reminder.destination)}){
                        Text("Open "+reminder.destination)
                    }
                }
            }
        }
        Text("An expected contribution period without a record is not automatically marked Missed. Verify account statuses with official sources.",
            style=MaterialTheme.typography.bodySmall)
    }
}
