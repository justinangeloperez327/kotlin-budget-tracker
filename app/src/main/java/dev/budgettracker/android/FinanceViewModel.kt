package dev.budgettracker.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.budgettracker.android.data.FinanceDb
import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Expense
import dev.budgettracker.android.domain.FinanceSnapshot
import dev.budgettracker.android.domain.parseMinor
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinanceViewModel(application: Application): AndroidViewModel(application) {
    private val db = FinanceDb(application)
    private val preferences = application.getSharedPreferences("appearance",0)
    var month by mutableStateOf(YearMonth.now()); private set
    var data by mutableStateOf(FinanceSnapshot(month)); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var theme by mutableStateOf(preferences.getString("palette", "sakura") ?: "sakura"); private set
    var appearance by mutableStateOf(preferences.getString("mode", "system") ?: "system"); private set

    init { reload() }

    fun setTheme(value: String) { theme=value; preferences.edit().putString("palette",value).apply() }
    fun setAppearance(value: String) { appearance=value; preferences.edit().putString("mode",value).apply() }
    fun dismissError(){ error=null }
    fun moveMonth(delta: Long) { month = month.plusMonths(delta); reload() }
    fun reload() {
        val requested=month
        viewModelScope.launch {
            loading=true
            try {
                val result=withContext(Dispatchers.IO){db.load(requested)}
                if (month==requested) data=result
                error=null
            } catch(ex: Exception){ error=ex.message ?: "Failed to read budget" }
            finally { loading=false }
        }
    }
    private fun write(operation: ()->Unit) {
        val requested=month
        viewModelScope.launch {
            loading=true
            try {
                withContext(Dispatchers.IO){ operation() }
                val result=withContext(Dispatchers.IO){db.load(requested)}
                if (month==requested) data=result
                error=null
            } catch(ex: Exception){error=ex.message ?: "Unable to save changes"}
            finally {loading=false}
        }
    }
    fun saveBudget(amount: String) {
        val target=month
        write { db.setBudget(target,parseMinor(amount)) }
    }
    fun saveAllocation(category:Category, amount:String){
        val target=month
        write {db.setAllocation(target,category,parseMinor(amount))}
    }
    fun addExpense(description:String,amount:String,category:Category,date:String){
        write {
            val parsedDate=LocalDate.parse(date)
            require(!parsedDate.isAfter(LocalDate.now())){"Future dates are not allowed"}
            db.addExpense(Expense(description=description.trim(),amount=parseMinor(amount),category=category,date=parsedDate))
        }
    }
    fun deleteExpense(id:String)=write {db.deleteExpense(id)}
    override fun onCleared(){db.close();super.onCleared()}
}
