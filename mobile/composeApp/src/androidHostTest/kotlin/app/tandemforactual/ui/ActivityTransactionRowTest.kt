package app.tandemforactual.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

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
