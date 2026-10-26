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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.budgettracker.android.FinanceViewModel
import dev.budgettracker.android.domain.DeviceBackup
import dev.budgettracker.android.domain.PreparedDeviceBackup
import dev.budgettracker.android.domain.WorkspaceAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun DataSafetyScreen(model: FinanceViewModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val snapshot=model.trackerData
    val audit=remember(snapshot) { WorkspaceAudit.inspect(snapshot) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PreparedDeviceBackup?>(null) }
    val localMode=!model.accountConfigured
    val enabled=localMode && !model.loading && !busy

    val export=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if(uri!=null && localMode) {
            val frozen=JSONObject(snapshot.toString())
            scope.launch {
                busy=true
                try {
                    withContext(Dispatchers.IO) {
                        val bytes=DeviceBackup.encode(frozen).toByteArray(Charsets.UTF_8)
                        requireNotNull(context.contentResolver.openOutputStream(uri)) {
                            "Document provider did not accept the backup"
                        }.use { it.write(bytes) }
                    }
                    message="Device-only backup exported. Store it securely: the JSON file is NOT encrypted."
                } catch(ex:Exception) {
                    message="Backup export failed: "+(ex.message ?: "Unknown error")
                } finally { busy=false }
            }
        }
    }
    val import=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if(uri!=null && localMode) {
            scope.launch {
                busy=true
                try {
                    pending=withContext(Dispatchers.IO) {
                        val backupText=requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "Cannot read selected backup file"
                        }.use { stream ->
                            DeviceBackup.readBounded { buffer -> stream.read(buffer) }
                        }
                        DeviceBackup.decode(backupText)
                    }
                    message=null
                } catch(ex:Exception) {
                    pending=null
                    message="Backup could not be opened: "+(ex.message ?: "Invalid file")
                } finally { busy=false }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Data integrity & backup",style=MaterialTheme.typography.titleLarge)
        Text("Check linked payment consistency across bills, debts, savings, remittances, government contributions and MP2. This audit never sends your data anywhere.",
            style=MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(audit.passed)"No integrity issues found" else "Integrity review required",
                    style=MaterialTheme.typography.titleMedium,
                    color=if(audit.passed)MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    modifier=Modifier.testTag("audit:status"))
                Text(audit.counts.values.sum().toString()+" total records across "+
                    audit.counts.size+" financial modules")
                Text(if(audit.passed)"Amounts, dates, parent records and linked expenses passed local checks."
                    else audit.issues.size.toString()+" potential inconsistencies detected.")
                if(!audit.passed){
                    audit.issues.take(12).forEach { issue ->
                        Text(issue.code+": "+issue.detail,style=MaterialTheme.typography.bodySmall)
                    }
                    if(audit.issues.size>12)Text("Additional issues not shown")
                }
                Text("Checks cannot confirm actual bank balances, remittance delivery or government payments.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        Text("Device-only backup",style=MaterialTheme.typography.titleMedium)
        if(!localMode) {
            Text("Signed in to web account. These actions are disabled so a remote workspace cannot be mistaken for a device backup. Sign out before backing up or restoring device-only records.",
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodyMedium)
        } else {
            Text("Export a versioned, checksum-validated JSON document using the Android file picker. The file is plaintext; keep it in a secure private location.",
                style=MaterialTheme.typography.bodyMedium)
            Text("Restoring replaces ALL existing device-only budgets and transactions. No merging, syncing or web-account changes occur.",
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodyMedium)
        }
        Button(onClick={export.launch("budget-tracker-device-backup.json")},
            enabled=enabled&&audit.passed,modifier=Modifier.fillMaxWidth()
                .testTag("backup:export")) { Text("Export device-only backup") }
        OutlinedButton(onClick={
            import.launch(arrayOf("application/json","text/plain","application/octet-stream"))
        },enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("backup:import")){
            Text("Choose backup to restore")
        }
        if(busy||model.loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        message?.let { Text(it,style=MaterialTheme.typography.bodyMedium) }
        model.backupStatus?.let { success ->
            Text(success,color=MaterialTheme.colorScheme.primary)
            TextButton(onClick=model::dismissBackupStatus){Text("Dismiss")}
        }
    }

    pending?.let { backup ->
        AlertDialog(onDismissRequest={if(!model.loading)pending=null},
            title={Text("Replace all device-only data?")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Backup created "+backup.createdAt)
                    Text(backup.budgetMonths.toString()+" budget months · "+
                        backup.counts.values.sum()+" financial records")
                    Text("All current device-only records will be permanently replaced by this backup. This cannot be undone. Your web-account workspace will not be changed.",
                        color=MaterialTheme.colorScheme.error)
                }
            },
            confirmButton={
                Button(onClick={
                    model.restoreDeviceBackup(backup.data)
                    pending=null
                },enabled=enabled,modifier=Modifier.testTag("backup:confirm")){
                    Text("Replace local data")
                }
            },
            dismissButton={
                TextButton(onClick={pending=null},enabled=!model.loading){Text("Cancel")}
            }
        )
    }
}
