package dev.budgettracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.budgettracker.android.ui.BudgetApp

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SDK 36 enforces edge-to-edge; bars are inset by the Compose root.
        enableEdgeToEdge()
        setContent {
            val finance:FinanceViewModel=viewModel()
            BudgetApp(finance)
        }
    }
}
