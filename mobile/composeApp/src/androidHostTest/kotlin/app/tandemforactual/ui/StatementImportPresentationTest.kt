package app.tandemforactual.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.api.client.StatementLedgerTransaction
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.importing.StatementCsvColumnMapping
import app.tandemforactual.ui.core.importing.StatementDateOrder
import app.tandemforactual.ui.core.importing.StatementImportDraft
import app.tandemforactual.ui.core.importing.StatementImportIds
import app.tandemforactual.ui.core.importing.StatementReview
import app.tandemforactual.ui.core.importing.StatementRowAction
import app.tandemforactual.ui.core.importing.defaultActions
import app.tandemforactual.ui.core.importing.reviewStatement
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.accounts.STATEMENT_IMPORT_CONNECTION_REQUIRED
import app.tandemforactual.ui.feature.accounts.StatementImportCallbacks
import app.tandemforactual.ui.feature.accounts.StatementImportContent
import app.tandemforactual.ui.feature.accounts.StatementImportDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatementImportPresentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nothingCanBeAppliedWhileTheAccountIsBeingCompared() {
        show(content(review = null, ledgerLoading = true))

        composeTestRule.onNodeWithText("Comparing with this account…").assertExists()
        composeTestRule.onNodeWithText("Apply", substring = true).assertDoesNotExist()
    }

    @Test
    fun aMissingConnectionOffersToTryAgain() {
        var retried = false
        show(content(review = null, ledgerError = STATEMENT_IMPORT_CONNECTION_REQUIRED), callbacks(onRetry = { retried = true }))

        composeTestRule.onNodeWithText(STATEMENT_IMPORT_CONNECTION_REQUIRED).assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    fun rowsToPairAreShownFirstAndChoosingOneReportsIt() {
        val review = groupReview()
        var chosen: Pair<Int, StatementRowAction>? = null
        show(content(review), callbacks(onAction = { index, action -> chosen = index to action }))

        composeTestRule.onNodeWithText("To review 2").assertExists()
        composeTestRule.onNodeWithText("Nothing to apply").assertIsNotEnabled()
        composeTestRule.onNodeWithText("2 for later").assertExists()
        composeTestRule.onNodeWithText("row 1", substring = true).performClick()
        composeTestRule.onNodeWithText("Uber · -$12.00").performScrollTo().performClick()

        assertEquals(0 to StatementRowAction.Match("u1"), chosen)
    }

    @Test
    fun applyCountsMatchesAndNewRows() {
        val review =
            reviewStatement(
                listOf(row("2026-11-04", -1_240, "TESCO STORES"), row("2026-11-05", -500, "Kiosk")),
                listOf(ledger("tesco", "2026-11-04", -1_240, "Tesco")),
            )
        var applied = false
        show(content(review), callbacks(onApply = { applied = true }))

        composeTestRule.onNodeWithText("1 matched · 1 new").assertExists()
        composeTestRule.onNodeWithText("Apply 2 changes").assertIsEnabled().performClick()
        assertTrue(applied)
    }

    @Test
    fun aStatementShowingSpendingAsPositiveOffersToReverseSigns() {
        val review =
            reviewStatement(
                listOf(row("2026-11-04", 2_598, "AMZN MKTP"), row("2026-11-03", 625, "BLUE BOTTLE")),
                listOf(ledger("a", "2026-11-04", -2_598, "Amazon"), ledger("b", "2026-11-03", -625, "Blue Bottle")),
            )
        var flipped = false
        show(content(review), callbacks(onFlip = { flipped = true }))

        composeTestRule.onNodeWithText("Reverse signs").performClick()
        assertTrue(flipped)
    }

    private fun show(
        content: StatementImportContent,
        callbacks: StatementImportCallbacks = callbacks(),
    ) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                StatementImportDialog(
                    visible = true,
                    state = AppState(),
                    content = content,
                    currencyDisplaySettings = usdSettings,
                    actionMessage = null,
                    busy = false,
                    callbacks = callbacks,
                )
            }
        }
    }

    private fun content(
        review: StatementReview?,
        ledgerLoading: Boolean = false,
        ledgerError: String? = null,
    ) = StatementImportContent(
        accountName = "Checking",
        filename = "statement.csv",
        showDateOrder = false,
        parsed = true,
        parseError = null,
        skippedRows = 0,
        firstSkippedReason = null,
        review = review,
        actions = review?.defaultActions().orEmpty(),
        flipSigns = false,
        ledgerLoading = ledgerLoading,
        ledgerError = ledgerError,
        dateOrder = StatementDateOrder.MONTH_DAY_YEAR,
        csvLayout = null,
        csvMapping = StatementCsvColumnMapping(),
        showCsvMapping = false,
    )

    private fun callbacks(
        onRetry: () -> Unit = {},
        onFlip: () -> Unit = {},
        onAction: (Int, StatementRowAction) -> Unit = { _, _ -> },
        onApply: () -> Unit = {},
    ) = StatementImportCallbacks(
        onDateOrderChange = {},
        onCsvMappingChange = {},
        onToggleCsvMapping = {},
        onRetryLedger = onRetry,
        onFlipSigns = onFlip,
        onAction = onAction,
        onDismiss = {},
        onDismissed = {},
        onApply = onApply,
    )

    private fun groupReview() =
        reviewStatement(
            listOf(row("2026-11-03", -1_200, "UBER TRIP", "f1", 1), row("2026-11-03", -1_200, "UBER TRIP", "f2", 2)),
            listOf(ledger("u1", "2026-11-03", -1_200, "Uber"), ledger("u2", "2026-11-03", -1_200, "Uber")),
        )

    private fun row(
        date: String,
        amount: Long,
        payee: String,
        bankId: String? = null,
        rowNumber: Int = 0,
    ) = StatementImportDraft(date, amount, payee, null, StatementImportIds().next(date, amount, payee + bankId.orEmpty()), bankId, rowNumber)

    private fun ledger(
        id: String,
        date: String,
        amount: Long,
        payee: String,
    ) = StatementLedgerTransaction(id, date, amount, payee, null, null, null, cleared = false, reconciled = false)

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
