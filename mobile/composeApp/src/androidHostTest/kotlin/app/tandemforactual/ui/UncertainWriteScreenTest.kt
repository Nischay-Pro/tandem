package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.data.database.OutboxOperationEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UncertainWriteScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun successfulUnsettledStatusShowsRecoveryActionsAndWarnsBeforeRemovingEdit() {
        var retryRequested = false
        var removalConfirmed = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteDetailsScreen(
                    state = AppState(),
                    operation = uncertainWrite(),
                    transaction = transactionItem(),
                    isChecked = true,
                    isNotFound = false,
                    checkError = null,
                    isSyncing = false,
                    onCheckStatus = {},
                    onRetry = { retryRequested = true },
                    onDiscard = { removalConfirmed = true },
                    onReviewConflict = {},
                    onReviewRejected = {},
                    onDone = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Status unknown").assertExists()
        composeTestRule
            .onNodeWithText(
                "Actual still can’t confirm whether this edit was saved. You can try again or remove it.",
            ).assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertTrue(retryRequested)

        composeTestRule.onNodeWithText("Remove edit").performClick()
        composeTestRule.onNodeWithText("Actual may already have saved it", substring = true).assertExists()
        composeTestRule.onAllNodesWithText("Remove edit")[1].performClick()
        assertTrue(removalConfirmed)
    }

    @Test
    fun noStatusResultOffersOnlyCheckStatus() {
        var retryRequested = false
        var removalRequested = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteDetailsScreen(
                    state = AppState(),
                    operation = uncertainWrite(),
                    transaction = transactionItem(),
                    isChecked = false,
                    isNotFound = false,
                    checkError = null,
                    isSyncing = false,
                    onCheckStatus = {},
                    onRetry = { retryRequested = true },
                    onDiscard = { removalRequested = true },
                    onReviewConflict = {},
                    onReviewRejected = {},
                    onDone = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Check status").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Remove edit").assertDoesNotExist()
        assertFalse(retryRequested)
        assertFalse(removalRequested)
    }

    @Test
    fun failedApiStatusCheckOffersOnlyCheckAgain() {
        var retryRequested = false
        var removalRequested = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteDetailsScreen(
                    state = AppState(),
                    operation = uncertainWrite(),
                    transaction = transactionItem(),
                    isChecked = true,
                    isNotFound = false,
                    checkError = "Tandem rejected the status check.",
                    isSyncing = false,
                    onCheckStatus = {},
                    onRetry = { retryRequested = true },
                    onDiscard = { removalRequested = true },
                    onReviewConflict = {},
                    onReviewRejected = {},
                    onDone = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Couldn’t check this change").assertExists()
        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Remove edit").assertDoesNotExist()
        assertFalse(retryRequested)
        assertFalse(removalRequested)
    }

    @Test
    fun failedOfflineStatusCheckOffersOnlyCheckAgain() {
        var retryRequested = false
        var removalRequested = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteDetailsScreen(
                    state = AppState(),
                    operation = uncertainWrite(),
                    transaction = transactionItem(),
                    isChecked = true,
                    isNotFound = false,
                    checkError = "Couldn’t reach Tandem. Check the connection and try again.",
                    isSyncing = false,
                    onCheckStatus = {},
                    onRetry = { retryRequested = true },
                    onDiscard = { removalRequested = true },
                    onReviewConflict = {},
                    onReviewRejected = {},
                    onDone = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Couldn’t check this change").assertExists()
        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Remove edit").assertDoesNotExist()
        assertFalse(retryRequested)
        assertFalse(removalRequested)
    }

    @Test
    fun authoritativeNotFoundDoesNotOfferRetryOrRemove() {
        var retryRequested = false
        var removalRequested = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteDetailsScreen(
                    state = AppState(),
                    operation = uncertainWrite().copy(lastErrorCode = "operation_not_found"),
                    transaction = transactionItem(),
                    isChecked = true,
                    isNotFound = true,
                    checkError = null,
                    isSyncing = false,
                    onCheckStatus = {},
                    onRetry = { retryRequested = true },
                    onDiscard = { removalRequested = true },
                    onReviewConflict = {},
                    onReviewRejected = {},
                    onDone = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Status not found").assertExists()
        composeTestRule
            .onNodeWithText(
                "Keep it on this device and check Actual before retrying.",
                substring = true,
            ).assertExists()
        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Remove edit").assertDoesNotExist()
        assertFalse(retryRequested)
        assertFalse(removalRequested)
    }

    private fun uncertainWrite() =
        OutboxOperationEntity(
            operationId = "uncertain-operation",
            idempotencyKey = "uncertain-idempotency-key",
            budgetId = "budget-1",
            recordType = "transaction",
            recordId = "transaction-1",
            action = "UPDATE",
            baseRevision = "revision-1",
            baseSnapshotJson = "{}",
            patchJson = "{}",
            optimisticJson = "{}",
            optimisticRecordDate = "2026-10-31",
            optimisticAccountId = "checking",
            optimisticCategoryId = "medical",
            optimisticPayeeId = "walgreens",
            optimisticAmountMinor = -10814,
            optimisticBudgetMonth = "2026-10",
            status = "uncertain",
            attemptCount = 1,
            serverOperationId = "uncertain-operation",
            lastErrorCode = "network-error",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
        )

    private fun transactionItem() =
        TransactionItem(
            payee = "Walgreens",
            category = "Medical",
            dateLabel = "10/31/26",
            amountMinorUnits = -10814,
            icon = "pill",
            recordId = "transaction-1",
        )
}
