package app.tandemforactual.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActivityTransactionRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun activityRowShowsPayeeNoteAndAccessibleIncomeAmountOnTheRight() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TransactionRow(
                    transaction = transaction(amountMinorUnits = 1_245),
                    privacyModeEnabled = false,
                    activityStyle = true,
                    showAccount = true,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Costco Gas").assertExists()
        composeTestRule.onNodeWithText("May 14, 2026").assertExists()
        composeTestRule.onNodeWithText("Weekly shop · Groceries · Checking").assertExists()
        composeTestRule.onNodeWithContentDescription("Income, +$12.45").assertExists()

        val payeeBounds = composeTestRule.onNodeWithText("Costco Gas", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val amountBounds =
            composeTestRule
                .onNodeWithContentDescription("Income, +$12.45", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(amountBounds.right > payeeBounds.right)
    }

    @Test
    fun activityRowRemainsReadableAtLargeTextScaleInWideLayout() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                    Box(Modifier.width(720.dp)) {
                        TransactionRow(
                            transaction = transaction(amountMinorUnits = 1_245),
                            privacyModeEnabled = false,
                            activityStyle = true,
                            showAccount = true,
                            onClick = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Costco Gas").assertExists()
        composeTestRule.onNodeWithText("Weekly shop · Groceries · Checking").assertExists()
        composeTestRule.onNodeWithContentDescription("Income, +$12.45").assertExists()
    }

    @Test
    fun hideAmountsMasksTheAmountInEveryRowStyle() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column {
                    TransactionRow(transaction(amountMinorUnits = -4_210), privacyModeEnabled = true, onClick = {})
                    TransactionRow(
                        transaction(amountMinorUnits = 1_245),
                        privacyModeEnabled = true,
                        activityStyle = true,
                        onClick = {},
                    )
                    TransactionRow(
                        transaction(amountMinorUnits = -990),
                        privacyModeEnabled = true,
                        activityStyle = true,
                        syncStatus = "queued",
                        onClick = {},
                    )
                }
            }
        }

        composeTestRule.onAllNodesWithContentDescription("Amount hidden").assertCountEquals(3)
        listOf("-$42.10", "+$12.45", "-$9.90").forEach { amount ->
            composeTestRule.onAllNodesWithText(amount, substring = true, useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithContentDescription(amount, substring = true, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun transaction(amountMinorUnits: Long) =
        TransactionItem(
            payee = "Costco Gas",
            category = "Groceries",
            dateLabel = "May 14, 2026",
            amountMinorUnits = amountMinorUnits,
            icon = "C",
            account = "Checking",
            notes = "Weekly shop",
            displaySettings = usdSettings,
        )

    private companion object {
        val usdSettings =
            BudgetDisplaySettings(
                currencyCode = "USD",
                currencySymbol = "$",
                currencyFractionDigits = 2,
                numberFormat = "comma-dot",
                hideFraction = false,
                currencySymbolPosition = "before",
                currencySpaceBetweenAmountAndSymbol = false,
            )
    }
}
