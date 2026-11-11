package app.tandemforactual.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.tandemforactual.data.database.OutboxOperationEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BudgetPendingChangeBannersTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun reviewBannersRemainVisibleWhenPhoneAlertPreferencesAreOff() {
        assertReviewBannersVisible(conflictAlerts = false, rejectedAlerts = false)
    }

    @Test
    fun reviewBannersRemainVisibleWhenPhoneAlertPreferencesAreOn() {
        assertReviewBannersVisible(conflictAlerts = true, rejectedAlerts = true)
    }

    private fun assertReviewBannersVisible(
        conflictAlerts: Boolean,
        rejectedAlerts: Boolean,
    ) {
        val state =
            AppState().apply {
                selectedBudgetId = "budget-1"
                conflictReviewAlerts = conflictAlerts
                rejectedEditAlerts = rejectedAlerts
            }
        val pendingWrites =
            listOf(
                pendingWrite("conflict", "conflict"),
                pendingWrite("rejected", "rejected"),
                pendingWrite("uncertain", "uncertain"),
            )
        val navigation =
            AppNavigation(
                open = {},
                reset = {},
                replaceTop = {},
                newTransaction = {},
                selectTab = {},
                back = {},
                backToList = {},
            )

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                LazyColumn {
                    budgetPendingChangeBannerItems(state, navigation, pendingWrites)
                }
            }
        }

        composeTestRule.onNodeWithText("Needs review").assertExists()
        composeTestRule.onNodeWithText("Rejected").assertExists()
        composeTestRule.onNodeWithText("Status unknown").assertExists()
    }

    private fun pendingWrite(
        operationId: String,
        status: String,
    ) = OutboxOperationEntity(
        operationId = operationId,
        idempotencyKey = "key-$operationId",
        budgetId = "budget-1",
        recordType = "transaction",
        recordId = "transaction-$operationId",
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
        attemptCount = 1,
        serverOperationId = operationId,
        lastErrorCode = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 2L,
    )
}
