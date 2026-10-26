package dev.budgettracker.android.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.budgettracker.android.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual on-device Compose navigation smoke tests.
 * Test on a clean emulator; no bank/server credentials or real financial data.
 */
@RunWith(AndroidJUnit4::class)
class BudgetUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun awaitReady() {
        compose.waitUntil(timeoutMillis=20_000) {
            compose.onAllNodesWithTag("finance:loading").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun open(page:String) {
        awaitReady()
        compose.onNodeWithTag("navigation-menu").performClick()
        compose.onNodeWithTag("nav:$page").performScrollTo().performClick()
        compose.onNodeWithTag("screen:$page").assertExists()
    }

    @Test fun drawerNavigationAndBackStack() {
        compose.onNodeWithTag("screen:Dashboard").assertExists()
        open("Financial overview")
        open("Reminders")
        compose.onNodeWithTag("navigate-back").performClick()
        compose.onNodeWithTag("screen:Financial overview").assertExists()
        compose.onNodeWithTag("navigate-back").performClick()
        compose.onNodeWithTag("screen:Dashboard").assertExists()
    }

    @Test fun budgetAndExpenseFormsHaveClearValidationAndCancellation() {
        open("Budget vs. actual")
        compose.onNodeWithText("Save complete plan").assertExists()
        compose.onNodeWithText("Monthly budget AED").assertExists()
        open("Expense tracker")
        compose.onNodeWithText("Add expense").performClick()
        compose.onNodeWithTag("expense:save").assertIsNotEnabled()
        compose.onNodeWithTag("expense:description").performTextInput("Grocery test")
        compose.onNodeWithTag("expense:amount").performTextInput("12.50")
        compose.onNodeWithTag("expense:save").assertIsEnabled()
        compose.onNodeWithTag("expense:amount").performTextClearance()
        compose.onNodeWithTag("expense:amount").performTextInput("0")
        compose.onNodeWithTag("expense:save").assertIsNotEnabled()
        compose.onNodeWithText("Description").assertExists()
        compose.onNodeWithText("Amount (AED)").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Search expenses").assertExists()
    }

    @Test fun reportAndRemindersNavigationLoadsRealScreens() {
        open("Reports")
        compose.onNodeWithText("Financial reports").assertExists()
        compose.onNodeWithText("Export monthly CSV").assertExists()
        open("Reminders")
        compose.onNodeWithText("In-app financial reminders",substring=true).assertExists()
        compose.onNodeWithText("Show All",substring=true).assertExists()
        open("MP2")
        compose.onNodeWithText("MP2 Savings",substring=true).assertExists()
    }

    @Test fun themeControlsAreAccessibleWithoutMutatingFinances() {
        compose.onNodeWithTag("theme-button").performClick()
        compose.onNodeWithText("Choose your theme").assertExists()
        compose.onNodeWithText("Kuromi").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("screen:Dashboard").assertExists()
        compose.onNodeWithTag("theme-button").performClick()
        compose.onNodeWithText("Choose your theme").assertExists()
        compose.onNodeWithText("Done").performClick()
    }
}
