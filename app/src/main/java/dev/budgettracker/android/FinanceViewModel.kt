package dev.budgettracker.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.budgettracker.android.data.FinanceDb
import dev.budgettracker.android.domain.BudgetFeatures
import dev.budgettracker.android.domain.PhilippineCommand
import dev.budgettracker.android.domain.PhilippineEngine
import dev.budgettracker.android.domain.TrackerCommand
import dev.budgettracker.android.domain.TrackerEngine
import dev.budgettracker.android.domain.Category
import dev.budgettracker.android.domain.Expense
import dev.budgettracker.android.domain.FinanceSnapshot
import dev.budgettracker.android.domain.parseMinor
import dev.budgettracker.android.network.MobileApi
import dev.budgettracker.android.network.MobileApiException
import dev.budgettracker.android.network.SecureSession
import dev.budgettracker.android.network.ServerSnapshot
import dev.budgettracker.android.network.StoredSession
import dev.budgettracker.android.network.validatedServerOrigin
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Device-only records are never uploaded automatically. When a mobile session exists,
 * all displayed/account edits are sourced from the revision-checked PostgreSQL API.
 * Incomplete/uncertain remote saves are blocked until explicit refresh.
 */
class FinanceViewModel(application: Application): AndroidViewModel(application) {
    private val db = FinanceDb(application)
    private val credentialStore = SecureSession(application)
    private val preferences = application.getSharedPreferences("appearance", 0)
    private var mobileSession: StoredSession? = null
    private var remote: ServerSnapshot? = null

    var month by mutableStateOf(YearMonth.now()); private set
    var data by mutableStateOf(FinanceSnapshot(month)); private set
    var allExpenses by mutableStateOf<List<Expense>>(emptyList()); private set
    var trackerData by mutableStateOf(TrackerEngine.blank()); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var syncBlocked by mutableStateOf(false); private set
    var accountEmail by mutableStateOf<String?>(null); private set
    var savedExpenseSequence by mutableStateOf(0L); private set
    var savedMutationSequence by mutableStateOf(0L); private set
    var accountConfigured by mutableStateOf(false); private set
    var serverOrigin by mutableStateOf(""); private set
    var theme by mutableStateOf(preferences.getString("palette", "sakura") ?: "sakura"); private set
    var appearance by mutableStateOf(preferences.getString("mode", "system") ?: "system"); private set

    init {
        viewModelScope.launch {
            try {
                mobileSession = withContext(Dispatchers.IO) { credentialStore.read() }
                accountConfigured = mobileSession != null
                serverOrigin = mobileSession?.origin ?: ""
            } catch (_: Exception) {
                error = "Stored mobile credentials could not be restored."
            }
            reload()
        }
    }

    fun selectTheme(value: String) {
        theme = value
        preferences.edit().putString("palette", value).apply()
    }
    fun selectAppearance(value: String) {
        appearance = value
        preferences.edit().putString("mode", value).apply()
    }
    fun dismissError() { error = null }

    fun moveMonth(delta: Long) {
        month = month.plusMonths(delta)
        if (mobileSession != null) {
            // Changing month does not modify or re-upload the server workspace.
            val snapshot = remote
            if (snapshot != null) data = snapshot.forMonth(month)
            else reload()
        } else {
            reload()
        }
    }

    fun reload() {
        if (loading && accountConfigured && remote != null) return
        val requested = month
        val session = mobileSession
        viewModelScope.launch {
            loading = true
            try {
                if (session != null) {
                    val latest = withContext(Dispatchers.IO) {
                        MobileApi(session.origin).workspace(session.token)
                    }
                    if (mobileSession == session) {
                        remote = latest
                        accountEmail = latest.email
                        accountConfigured = true
                        if (month == requested) data = latest.forMonth(requested)
                        allExpenses = latest.allExpenses()
                        trackerData = JSONObject(latest.fullData.toString())
                        syncBlocked = false
                    }
                } else {
                    val (local, ledger, modules) = withContext(Dispatchers.IO) {
                        Triple(db.load(requested),db.loadAllExpenses(),db.loadTrackerData())
                    }
                    if (mobileSession == null && month == requested) {
                        data = local
                        allExpenses = ledger
                        trackerData = modules
                    }
                    syncBlocked = false
                }
                error = null
            } catch (ex: Exception) {
                if (session != null) {
                    syncBlocked = true
                    if (ex is MobileApiException && ex.status == 401) {
                        clearRemoteCredentials()
                        error = "Mobile session expired. Sign in again."
                    } else {
                        error = "Unable to refresh web-account data. Editing is disabled until a successful refresh. " +
                            (ex.message ?: "")
                    }
                } else error = ex.message ?: "Failed to load local financial data"
            } finally {
                loading = false
            }
        }
    }

    fun signIn(origin: String, email: String, password: String, register: Boolean, name: String) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            try {
                val canonical = validatedServerOrigin(origin)
                val result = withContext(Dispatchers.IO) {
                    val api = MobileApi(canonical)
                    val auth = api.authenticate(if (register) "register" else "login", email, password, name)
                    val token = auth.getString("accessToken")
                    require(token.matches(Regex("[a-f0-9]{64}"))) { "Server returned an invalid mobile token" }
                    val snapshot = try {
                        api.workspace(token)
                    } catch (failure: Exception) {
                        runCatching { api.logout(token) }
                        throw failure
                    }
                    try {
                        credentialStore.save(canonical, token)
                    } catch (failure: Exception) {
                        runCatching { api.logout(token) }
                        throw failure
                    }
                    StoredSession(canonical, token) to snapshot
                }
                mobileSession = result.first
                serverOrigin = canonical
                accountConfigured = true
                remote = result.second
                accountEmail = result.second.email
                data = result.second.forMonth(month)
                allExpenses = result.second.allExpenses()
                trackerData = JSONObject(result.second.fullData.toString())
                syncBlocked = false
                error = null
            } catch (ex: Exception) {
                error = ex.message ?: "Sign-in failed. Verify your account and HTTPS server URL."
            } finally { loading = false }
        }
    }

    fun signOut() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            val old = mobileSession
            // Clearing the local key happens even when the server is unreachable.
            clearRemoteCredentials()
            try {
                if (old != null) withContext(Dispatchers.IO) {
                    MobileApi(old.origin).logout(old.token)
                }
                error = null
            } catch (_: Exception) {
                error = "Signed out on this device. The old remote token will expire automatically; server revocation could not be confirmed."
            }
            try {
                val (local, ledger, modules) = withContext(Dispatchers.IO) {
                    Triple(db.load(month),db.loadAllExpenses(),db.loadTrackerData())
                }
                data = local
                allExpenses = ledger
                trackerData = modules
            }
            catch (ex: Exception) { error = ex.message ?: "Unable to read device-only records" }
            loading = false
        }
    }

    private fun clearRemoteCredentials() {
        credentialStore.clear()
        mobileSession = null
        remote = null
        accountEmail = null
        accountConfigured = false
        syncBlocked = false
        serverOrigin = ""
        // Never display a previous account's finance data after logout/session expiry.
        data = FinanceSnapshot(month)
        allExpenses = emptyList()
        trackerData = TrackerEngine.blank()
    }

    private fun write(local: () -> Unit, mobile: (ServerSnapshot) -> JSONObject, onSaved: () -> Unit = {}) {
        if (loading || (mobileSession != null && (syncBlocked || remote == null))) {
            error = "Refresh the web account before making changes."
            return
        }
        val requested = month
        val session = mobileSession
        val starting = remote
        viewModelScope.launch {
            loading = true
            try {
                if (session != null && starting != null) {
                    // Never upload a partial SQLite snapshot or overwrite unported modules.
                    val fullData = mobile(starting)
                    val latest = withContext(Dispatchers.IO) {
                        val api = MobileApi(session.origin)
                        api.save(session.token, starting.revision, fullData)
                        api.workspace(session.token)
                    }
                    if (mobileSession == session) {
                        remote = latest
                        accountEmail = latest.email
                        data = latest.forMonth(month)
                        allExpenses = latest.allExpenses()
                        trackerData = JSONObject(latest.fullData.toString())
                    }
                } else {
                    withContext(Dispatchers.IO) { local() }
                    if (mobileSession == null) {
                        val (localSnapshot, ledger, modules) = withContext(Dispatchers.IO) {
                            Triple(db.load(month),db.loadAllExpenses(),db.loadTrackerData())
                        }
                        data = localSnapshot
                        allExpenses = ledger
                        trackerData = modules
                    }
                }
                error = null
                savedMutationSequence += 1L
                onSaved()
            } catch (ex: Exception) {
                if (session != null) {
                    if (ex is MobileApiException && ex.status == 401) {
                        clearRemoteCredentials()
                        error = "Mobile session expired. Sign in again."
                    } else {
                        // Even a lost response could follow a committed save.
                        // Never retry automatically or guess the next revision.
                        syncBlocked = ex !is MobileApiException ||
                            ex.status == 409 || ex.status >= 500
                        error = if (syncBlocked)
                            "Save cannot be confirmed or server data changed. Refresh before editing. " +
                                (ex.message ?: "")
                        else ex.message ?: "Server rejected this change"
                    }
                } else error = ex.message ?: "Unable to save local changes"
            } finally { loading = false }
        }
    }

    var backupStatus by mutableStateOf<String?>(null); private set
    fun dismissBackupStatus() { backupStatus = null }

    /**
     * Restore is allowed ONLY in device-only mode and only after the UI has
     * displayed the backup preview and obtained explicit replacement consent.
     */
    fun restoreDeviceBackup(snapshot: JSONObject) {
        if(loading || mobileSession!=null || accountConfigured) {
            error="Sign out and wait for local mode before restoring a device backup."
            return
        }
        viewModelScope.launch {
            loading=true
            try {
                val (local,ledger,modules)=withContext(Dispatchers.IO) {
                    db.restoreDeviceSnapshot(JSONObject(snapshot.toString()))
                    Triple(db.load(month),db.loadAllExpenses(),db.loadTrackerData())
                }
                if(mobileSession==null) {
                    data=local
                    allExpenses=ledger
                    trackerData=modules
                    error=null
                    backupStatus="Local financial data restored successfully."
                    savedMutationSequence++
                }
            } catch(ex:Exception) {
                error="Restore rejected; your existing device records were not replaced. "+
                    (ex.message ?: "Unknown restore error")
            } finally {
                loading=false
            }
        }
    }

    fun philippineMutation(command: PhilippineCommand) = write(
        local = { db.applyPhilippineCommand(command) },
        mobile = { PhilippineEngine.apply(it.fullData,command) }
    )

    fun trackerMutation(command: TrackerCommand) = write(
        local = { db.applyTrackerCommand(command) },
        mobile = { TrackerEngine.apply(it.fullData,command) }
    )

    fun saveBudget(input: String) {
        val target = month
        write(
            local = { db.setBudget(target, parseMinor(input)) },
            mobile = { it.updateBudget(target, parseMinor(input)) },
        )
    }
    fun saveAllocation(category: Category, input: String) {
        val target = month
        write(
            local = { db.setAllocation(target, category, parseMinor(input)) },
            mobile = { it.updateAllocation(target, category, parseMinor(input)) },
        )
    }
    /** Save the complete budget plan in one atomic SQLite or server revision transaction. */
    fun savePlan(budget: String, allocations: Map<Category, String>) {
        val selectedMonth = month
        write(
            local = {
                val amount = parseMinor(budget)
                val limits = allocations.mapValues { parseMinor(it.value) }
                BudgetFeatures.plan(selectedMonth, amount, limits)
                db.savePlan(selectedMonth, amount, limits)
            },
            mobile = {
                val amount = parseMinor(budget)
                val limits = allocations.mapValues { entry -> parseMinor(entry.value) }
                BudgetFeatures.plan(selectedMonth, amount, limits)
                it.updatePlan(selectedMonth, amount, limits)
            }
        )
    }

    fun editExpense(id: String, description: String, amount: String, category: Category, date: String) {
        val existing = allExpenses.firstOrNull { it.id == id }
        if (existing == null || existing.source != dev.budgettracker.android.domain.ExpenseSource.MANUAL) {
            error = "Linked or missing expenses cannot be edited here."
            return
        }
        write(
            local = {
                db.updateManualExpense(Expense(
                    id = id, description = description.trim(), amount = parseMinor(amount),
                    category = category, date = LocalDate.parse(date)
                ))
            },
            mobile = {
                it.updateManualExpense(Expense(
                    id = id, description = description.trim(), amount = parseMinor(amount),
                    category = category, date = LocalDate.parse(date)
                ))
            },
            onSaved = { savedExpenseSequence += 1L }
        )
    }

    fun addExpense(description: String, amount: String, category: Category, date: String) {
        write(
            local = {
                val parsed = LocalDate.parse(date)
                db.addExpense(Expense(description = description.trim(),
                    amount = parseMinor(amount), category = category, date = parsed))
            },
            mobile = {
                val parsed = LocalDate.parse(date)
                it.addManualExpense(description.trim(), parseMinor(amount), category, parsed)
            },
            onSaved = { savedExpenseSequence += 1L },
        )
    }
    fun deleteExpense(id: String) = write(
        local = { db.deleteExpense(id) },
        mobile = { it.removeManualExpense(id) },
    )
    override fun onCleared() { db.close(); super.onCleared() }
}
