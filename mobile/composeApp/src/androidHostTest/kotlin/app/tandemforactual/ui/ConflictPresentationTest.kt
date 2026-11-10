package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConflictPresentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun conflictComparisonListsEveryChangedTransactionField() {
        val edited =
            Json
                .parseToJsonElement(
                    """{"amount":1250,"payee":"new-payee","category":"medical","date":"2026-11-02","account":"savings","notes":"new note","cleared":true}""",
                ).jsonObject
        val actual =
            Json
                .parseToJsonElement(
                    """{"amount":1000,"payee":"old-payee","category":"food","date":"2026-11-01","account":"checking","notes":"old note","cleared":false}""",
                ).jsonObject

        val differences =
            conflictFieldComparisons(
                edited = edited,
                actual = actual,
                names =
                    ConflictFieldNameLookup(
                        payees = mapOf("new-payee" to "Target", "old-payee" to "Costco"),
                        accounts = mapOf("savings" to "Savings", "checking" to "Checking"),
                        categories = mapOf("medical" to "Medical", "food" to "Groceries"),
                    ),
                formatAmount = { "minor:$it" },
                formatDate = { it },
            )

        assertEquals(listOf("Amount", "Payee", "Category", "Date", "Account", "Notes", "Cleared"), differences.map { it.label })
        assertEquals(ConflictFieldComparison("Payee", "Target", "Costco"), differences[1])
        assertEquals(ConflictFieldComparison("Category", "Medical", "Groceries"), differences[2])
        assertEquals(ConflictFieldComparison("Cleared", "Cleared", "Uncleared"), differences.last())
    }

    @Test
    fun conflictComparisonOmitsMatchingFieldsAndIdentifiesReconciliationState() {
        val snapshot =
            Json
                .parseToJsonElement(
                    """{"amount":1000,"payee":"market","category":"food","date":"2026-11-01","account":"checking","notes":"same note","cleared":"reconciled"}""",
                ).jsonObject
        val actual =
            Json
                .parseToJsonElement(
                    """{"amount":1000,"payee":"market","category":"food","date":"2026-11-01","account":"checking","notes":"same note","cleared":false}""",
                ).jsonObject

        val differences =
            conflictFieldComparisons(
                edited = snapshot,
                actual = actual,
                names =
                    ConflictFieldNameLookup(
                        payees = mapOf("market" to "Market"),
                        categories = mapOf("food" to "Groceries"),
                        accounts =
                            mapOf(
                                "checking" to "Checking",
                            ),
                    ),
                formatAmount = { "minor:$it" },
                formatDate = { it },
            )

        assertEquals(listOf(ConflictFieldComparison("Cleared", "Reconciled", "Uncleared")), differences)
    }

    @Test
    fun actualIsNeverShownAsDeletedBeforeTheServerConfirmsIt() {
        val formatAmount: (Long) -> String = { "minor:$it" }

        assertEquals("Not checked", conflictActualAmountText(false, false, true, null, formatAmount))
        assertEquals("Checking…", conflictActualAmountText(false, true, true, null, formatAmount))
        assertEquals("Deleted", conflictActualAmountText(true, false, true, null, formatAmount))
        assertEquals("minor:1250", conflictActualAmountText(true, false, false, 1250, formatAmount))
    }

    @Test
    fun conflictReasonExplainsDeletionAndChangedFieldsInPlainLanguage() {
        assertEquals(
            "Actual changed this transaction while you were deleting it.",
            conflictReasonMessage("delete-versus-edit", listOf("amount"), "DELETE", actualDeleted = false),
        )
        assertEquals(
            "Actual deleted this transaction while your edit was waiting.",
            conflictReasonMessage("deleted-remotely", listOf("\$entity"), "UPDATE", actualDeleted = false),
        )
        assertEquals(
            "Actual changed the amount and category while your edit was waiting.",
            conflictReasonMessage("same-field-edit", listOf("amount", "category"), "UPDATE", actualDeleted = false),
        )
    }

    @Test
    fun conflictDetailsShowBothVersionsForEachChangedField() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ConflictComparisonCard(
                    listOf(
                        ConflictFieldComparison("Payee", "Target", "Costco"),
                        ConflictFieldComparison("Notes", "Hardware", "Groceries"),
                        ConflictFieldComparison("Cleared", "Cleared", "Uncleared"),
                    ),
                )
            }
        }

        listOf("Changed details", "Payee", "Target", "Costco", "Notes", "Hardware", "Groceries", "Uncleared")
            .forEach { composeTestRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun cancellingKeepLastSyncedConfirmationKeepsTheConflictEdit() {
        var editDiscarded = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                KeepLastSyncedConflictAction(
                    operationId = "conflicted-edit",
                    enabled = true,
                    onResolveConflict = { _, keepYourEdit ->
                        if (!keepYourEdit) editDiscarded = true
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Keep Actual’s version").performClick()
        composeTestRule.onNodeWithText("Discard your edit?").assertExists()
        composeTestRule.onNodeWithText("Keep editing").performClick()

        composeTestRule.onNodeWithText("Discard your edit?").assertDoesNotExist()
        assertFalse(editDiscarded)
    }

    @Test
    fun confirmingKeepActualDiscardsTheEditOnlyAfterConfirmation() {
        var editDiscarded = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                KeepLastSyncedConflictAction(
                    operationId = "conflicted-edit",
                    enabled = true,
                    onResolveConflict = { _, keepYourEdit ->
                        if (!keepYourEdit) editDiscarded = true
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Keep Actual’s version").performClick()
        assertFalse(editDiscarded)
        composeTestRule.onNodeWithText("Discard edit").performClick()

        assertTrue(editDiscarded)
    }
}
