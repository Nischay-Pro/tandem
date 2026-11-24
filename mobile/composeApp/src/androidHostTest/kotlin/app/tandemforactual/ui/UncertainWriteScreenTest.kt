package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.sync.UncertainWriteDetailsScreen
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

        composeTestRule.onNodeWithText("Status unknown.").assertExists()
        composeTestRule
            .onNodeWithText(
                "Actual couldn’t confirm whether this was saved. Check Actual before retrying.",
            ).assertExists()
        composeTestRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertTrue(retryRequested)

        composeTestRule.onNodeWithText("Remove edit").performScrollTo().performClick()
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
    fun failedApiStatusCheckKeepsServerGatedRecoveryActionsAvailable() {
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

        composeTestRule.onNodeWithText("Couldn’t check this change.").assertExists()
        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertTrue(retryRequested)
        composeTestRule.onNodeWithText("Remove edit").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Actual may already have saved it", substring = true).assertExists()
        assertFalse(removalRequested)
    }

    @Test
    fun failedOfflineStatusCheckOffersActionsThatStillRequireServerConfirmation() {
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

        composeTestRule.onNodeWithText("Couldn’t check this change.").assertExists()
        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertTrue(retryRequested)
        composeTestRule.onNodeWithText("Remove edit").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Actual may already have saved it", substring = true).assertExists()
        assertTrue(retryRequested)
        assertFalse(removalRequested)
    }

    @Test
    fun authoritativeNotFoundOffersRetryAndRemoveChoices() {
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

        composeTestRule.onNodeWithText("Status not found.").assertExists()
        composeTestRule
            .onNodeWithText(
                "Actual has no record of this edit. Keep it here or remove it.",
                substring = true,
            ).assertExists()
        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertTrue(retryRequested)
        composeTestRule.onNodeWithText("Remove edit").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Actual may already have saved it", substring = true).assertExists()
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
