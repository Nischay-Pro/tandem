package app.tandemforactual.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.data.store.BudgetDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StatementImportPresentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun importStaysDisabledUntilExactDuplicatePreviewSucceeds() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                StatementImportDialog(
                    visible = true,
                    state = AppState(),
                    currencyDisplaySettings = usdSettings,
                    accountName = "Checking",
                    filename = "statement.csv",
                    parseResult = parsedStatement,
                    parseError = null,
                    actionMessage = null,
                    dateOrder = StatementDateOrder.MONTH_DAY_YEAR,
                    csvLayout = null,
                    csvMapping = StatementCsvColumnMapping(),
                    showCsvMapping = false,
                    importPreview = null,
                    importPreviewLoading = false,
                    importPreviewError = null,
                    busy = false,
                    onDateOrderChange = {},
                    onCsvMappingChange = {},
                    onToggleCsvMapping = {},
                    onRetryImportPreview = {},
                    onDismiss = {},
                    onDismissed = {},
                    onImport = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Import 2 transactions").assertIsNotEnabled()
    }

    @Test
    fun serverUnreachableRequiresConnectionAndKeepsImportDisabled() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                StatementImportDialog(
                    visible = true,
                    state = AppState(),
                    currencyDisplaySettings = usdSettings,
                    accountName = "Checking",
                    filename = "statement.csv",
                    parseResult = parsedStatement,
                    parseError = null,
                    actionMessage = null,
                    dateOrder = StatementDateOrder.MONTH_DAY_YEAR,
                    csvLayout = null,
                    csvMapping = StatementCsvColumnMapping(),
                    showCsvMapping = false,
                    importPreview = StatementImportPreview(emptySet(), serverChecked = false),
                    importPreviewLoading = false,
                    importPreviewError = null,
                    busy = false,
                    onDateOrderChange = {},
                    onCsvMappingChange = {},
                    onToggleCsvMapping = {},
                    onRetryImportPreview = {},
                    onDismiss = {},
                    onDismissed = {},
                    onImport = {},
                )
            }
        }

        composeTestRule.onNodeWithText(STATEMENT_IMPORT_CONNECTION_REQUIRED).assertExists()
        composeTestRule.onNodeWithText("Import 2 transactions").assertIsNotEnabled()
    }

    @Test
    fun successfulPreviewShowsExactCountsBeforeImport() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                StatementImportDialog(
                    visible = true,
                    state = AppState(),
                    currencyDisplaySettings = usdSettings,
                    accountName = "Checking",
                    filename = "statement.csv",
                    parseResult = parsedStatement,
                    parseError = null,
                    actionMessage = null,
                    dateOrder = StatementDateOrder.MONTH_DAY_YEAR,
                    csvLayout = null,
                    csvMapping = StatementCsvColumnMapping(),
                    showCsvMapping = false,
                    importPreview = StatementImportPreview(setOf("already-imported")),
                    importPreviewLoading = false,
                    importPreviewError = null,
                    busy = false,
                    onDateOrderChange = {},
                    onCsvMappingChange = {},
                    onToggleCsvMapping = {},
                    onRetryImportPreview = {},
                    onDismiss = {},
                    onDismissed = {},
                    onImport = {},
                )
            }
        }

        composeTestRule.onNodeWithText("New in Actual").assertExists()
        composeTestRule.onAllNodesWithText("Already in Actual").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("1").assertCountEquals(2)
        composeTestRule.onNodeWithText("Import 2 transactions").assertIsEnabled()
    }

    @Test
    fun unusualCsvMappingPreviewMatchesTheRowsPassedToImport() {
        val statement =
            "Posting date,Merchant label,Withdrawal amount,Deposit amount,Description text\n" +
                "11/04/2026,Corner Market,18.50,,Groceries\n" +
                "11/05/2026,Payroll,,2500.00,Paycheck\n"
        val layout = inspectStatementCsv(statement)
        val mapping =
            StatementCsvColumnMapping(
                dateColumn = 0,
                debitColumn = 2,
                creditColumn = 3,
                payeeColumn = 1,
                notesColumn = 4,
            )
        val parsed = parseStatementImport("unusual-bank.csv", statement, csvMapping = mapping)
        var itemsPassedToImport = emptyList<app.tandemforactual.api.client.StatementImportItem>()

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                StatementImportDialog(
                    visible = true,
                    state = AppState(),
                    currencyDisplaySettings = usdSettings,
                    accountName = "Checking",
                    filename = "unusual-bank.csv",
                    parseResult = parsed,
                    parseError = null,
                    actionMessage = null,
                    dateOrder = StatementDateOrder.MONTH_DAY_YEAR,
                    csvLayout = layout,
                    csvMapping = mapping,
                    showCsvMapping = true,
                    importPreview = StatementImportPreview(setOf(parsed.transactions.last().importedId)),
                    importPreviewLoading = false,
                    importPreviewError = null,
                    busy = false,
                    onDateOrderChange = {},
                    onCsvMappingChange = {},
                    onToggleCsvMapping = {},
                    onRetryImportPreview = {},
                    onDismiss = {},
                    onDismissed = {},
                    onImport = { itemsPassedToImport = it.toStatementImportItems() },
                )
            }
        }

        composeTestRule.onNodeWithText("Posting date").assertExists()
        composeTestRule.onNodeWithText("Merchant label").assertExists()
        composeTestRule.onNodeWithText("Withdrawal amount").assertExists()
        composeTestRule.onNodeWithText("Deposit amount").assertExists()
        composeTestRule.onNodeWithText("2026-11-04 · Corner Market").assertExists()
        composeTestRule.onNodeWithText("-$18.50").assertExists()
        composeTestRule.onNodeWithText("New in Actual").assertExists()
        composeTestRule.onNodeWithText("Import 2 transactions").performScrollTo().performClick()

        assertEquals(parsed.toStatementImportItems(), itemsPassedToImport)
    }

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

        val parsedStatement =
            StatementParseResult(
                transactions =
                    listOf(
                        StatementImportDraft(
                            date = "2026-11-04",
                            amountMinor = -1_850,
                            payeeName = "Corner Market",
                            notes = null,
                            importedId = "new-row",
                        ),
                        StatementImportDraft(
                            date = "2026-11-05",
                            amountMinor = -2_400,
                            payeeName = "Pet Clinic",
                            notes = null,
                            importedId = "already-imported",
                        ),
                    ),
                skippedRows = 0,
            )
    }
}
