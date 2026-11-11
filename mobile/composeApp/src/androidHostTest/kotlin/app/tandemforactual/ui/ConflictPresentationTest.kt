package app.tandemforactual.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConflictPresentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun conflictActionsRequireTheLatestRevisionThatWasShownForReview() {
        assertFalse(canResolveReviewedConflict(serverConfirmed = false, reviewedRevision = "rev-1", latestRevision = "rev-1"))
        assertFalse(canResolveReviewedConflict(serverConfirmed = true, reviewedRevision = null, latestRevision = "rev-1"))
        assertTrue(canResolveReviewedConflict(serverConfirmed = true, reviewedRevision = "rev-1", latestRevision = "rev-1"))
        assertFalse(canResolveReviewedConflict(serverConfirmed = true, reviewedRevision = "rev-1", latestRevision = "rev-2"))
        assertTrue(conflictReviewRevisionChanged(serverConfirmed = true, reviewedRevision = "rev-1", latestRevision = "rev-2"))
    }

    @Test
    fun replacementConflictForTheSameTransactionStaysOnTheReviewScreen() {
        val replacement = conflictOperation("replacement", "transaction-1", "conflict")

        assertEquals(
            replacement,
            conflictReplacementForRecord(
                listOf(conflictOperation("original", "transaction-1", "superseded"), replacement),
                selectedOperationId = "original",
                recordId = "transaction-1",
            ),
        )
        assertNull(
            conflictReplacementForRecord(
                listOf(replacement),
                selectedOperationId = "replacement",
                recordId = "transaction-1",
            ),
        )
    }

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
    fun conflictComparisonDoesNotLabelUneditedActualFieldsAsTheUsersEdit() {
        val edited = Json.parseToJsonElement("""{"amount":1200,"notes":"old note"}""").jsonObject
        val actual = Json.parseToJsonElement("""{"amount":1500,"notes":"new note"}""").jsonObject

        val differences =
            conflictFieldComparisons(
                edited = edited,
                actual = actual,
                names = ConflictFieldNameLookup(),
                formatAmount = { "minor:$it" },
                formatDate = { it },
                userEditedFields = setOf("notes"),
            )

        assertEquals(listOf(ConflictFieldComparison("Notes", "old note", "new note")), differences)
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
    fun rejectedTransactionReferencesExplainWhatToChooseAgain() {
        assertEquals(
            "This category no longer exists in Actual. Choose another category and try again.",
            rejectedTransactionReferenceMessage("category-not-found"),
        )
        assertEquals(
            "This category is hidden in Actual. Choose another category and try again.",
            rejectedTransactionReferenceMessage("category-hidden"),
        )
        assertEquals(
            "This payee no longer exists in Actual. Choose another payee and try again.",
            rejectedTransactionReferenceMessage("payee-not-found"),
        )
        assertEquals(
            "This account is closed in Actual. Choose another account and try again.",
            rejectedTransactionReferenceMessage("account-closed"),
        )
    }

    @Test
    fun conflictDetailsShowBothVersionsForEachChangedField() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column {
                    Row {
                        ConflictValueCard("Your edit", "-$12.00")
                        ConflictValueCard("Actual", "-$15.00")
                    }
                    ConflictComparisonCard(
                        listOf(
                            ConflictFieldComparison("Payee", "Target", "Costco"),
                            ConflictFieldComparison("Category", "Home supplies", "Medical & Dental"),
                            ConflictFieldComparison("Date", "Nov 2, 2026", "Nov 1, 2026"),
                            ConflictFieldComparison("Account", "Savings", "Checking"),
                            ConflictFieldComparison("Notes", "Hardware", "Groceries"),
                            ConflictFieldComparison("Cleared", "Cleared", "Uncleared"),
                        ),
                    )
                }
            }
        }

        composeTestRule.onAllNodesWithText("Your edit", useUnmergedTree = true).assertCountEquals(7)
        composeTestRule.onAllNodesWithText("Actual", useUnmergedTree = true).assertCountEquals(7)
        composeTestRule.onAllNodesWithText("Cleared", useUnmergedTree = true).assertCountEquals(2)
        listOf(
            "-$12.00",
            "-$15.00",
            "Changed details",
            "Payee",
            "Target",
            "Costco",
            "Category",
            "Home supplies",
            "Medical & Dental",
            "Date",
            "Nov 2, 2026",
            "Nov 1, 2026",
            "Account",
            "Savings",
            "Checking",
            "Notes",
            "Hardware",
            "Groceries",
            "Uncleared",
        ).forEach { composeTestRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun keepActualActionCannotOpenItsDiscardConfirmationBeforeServerConfirmation() {
        var resolutionRequested = false
        val canResolve = canResolveReviewedConflict(serverConfirmed = false, reviewedRevision = null, latestRevision = "revision-1")
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                KeepLastSyncedConflictAction(
                    operationId = "conflicted-edit",
                    reviewedRevision = "",
                    enabled = canResolve,
                    onResolveConflict = { _, _, _ -> resolutionRequested = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Keep Actual’s version").assertIsNotEnabled().performClick()
        composeTestRule.onNodeWithText("Discard your edit?").assertDoesNotExist()
        assertFalse(resolutionRequested)
    }

    @Test
    fun cancellingKeepLastSyncedConfirmationKeepsTheConflictEdit() {
        var editDiscarded = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                KeepLastSyncedConflictAction(
                    operationId = "conflicted-edit",
                    reviewedRevision = "revision-1",
                    enabled = true,
                    onResolveConflict = { _, keepYourEdit, _ ->
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
                    reviewedRevision = "revision-1",
                    enabled = true,
                    onResolveConflict = { _, keepYourEdit, _ ->
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

    private fun conflictOperation(
        operationId: String,
        recordId: String,
        status: String,
    ) = OutboxOperationEntity(
        operationId = operationId,
        idempotencyKey = "key-$operationId",
        budgetId = "budget-1",
        recordType = "transaction",
        recordId = recordId,
        action = "UPDATE",
        baseRevision = "revision-1",
        baseSnapshotJson = "{}",
        patchJson = "{}",
        optimisticJson = "{}",
        optimisticRecordDate = null,
        optimisticAccountId = null,
        optimisticCategoryId = null,
        optimisticPayeeId = null,
        optimisticAmountMinor = null,
        optimisticBudgetMonth = null,
        status = status,
        attemptCount = 0,
        serverOperationId = null,
        lastErrorCode = null,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )
}
