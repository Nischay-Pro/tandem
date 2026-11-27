package app.tandemforactual.ui.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.PlanningCipher
import app.tandemforactual.data.planning.PlanningStore
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.UnsyncedPlanningRecord
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EraseDataConfirmationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val database: TandemDatabase =
        Room
            .inMemoryDatabaseBuilder<TandemDatabase>()
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private var opened: AppRoute? = null
    private val navigation =
        AppNavigation(open = { opened = it }, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})

    @AfterTest
    fun close() = database.close()

    private fun show(state: AppState = AppState().apply { selectedBudgetId = "budget-1" }) {
        val session = TandemMobileSession(TandemApiClient(), database, { 1_000L })
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ServerDeviceScreen(state, "Pixel", session, navigation, onForgetDevice = {}, onForgetDeviceLocally = {})
            }
        }
        composeTestRule.onNodeWithText("Sign out and erase data").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000) { composeTestRule.onAllNodesWithText("Erase").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun erasingSaysWhichPlanningRecordsExistOnlyHereAndOffersABackup() {
        runBlocking {
            val store = PlanningStore(database.planningRecordDao(), "budget-1", PlanningCipher(PlanningCipher.newKey())) { 1L }
            store.put("goal-1", SavingsGoal.TYPE, JsonObject(emptyMap()))
            store.put("goal-2", SavingsGoal.TYPE, JsonObject(emptyMap()))
            store.put("bill-1", SharedBill.TYPE, JsonObject(emptyMap()))
        }
        show()

        composeTestRule
            .onNodeWithText("2 goals and 1 shared bill haven’t synced anywhere else. Erasing deletes them for good.", substring = true)
            .assertExists()
        composeTestRule.onNodeWithText("Erase").assertIsEnabled()
        composeTestRule.onNodeWithText("Back up first").performClick()
        composeTestRule.runOnIdle { assertEquals(AppRoute.EXPORT, opened) }
    }

    @Test
    fun unsentEditsBlockErasingBeforeConfirming() {
        runBlocking { database.outboxDao().put(queuedWrite()) }
        show()

        composeTestRule.onNodeWithText("Sync or resolve pending changes before signing out.").assertExists()
        composeTestRule.onNodeWithText("Erase").assertIsNotEnabled()
    }

    @Test
    fun summaryNamesKindsAndPointsAtOtherBudgets() {
        assertNull(eraseLossSummary(emptyList(), "budget-1"))
        assertEquals(
            "1 saved report and 2 other planning details haven’t synced anywhere else. Erasing deletes them for good. " +
                "Some belong to other budgets; open each one to back it up.",
            eraseLossSummary(
                listOf(
                    UnsyncedPlanningRecord("budget-1", "planning.custom_report"),
                    UnsyncedPlanningRecord("budget-1", "planning.settings"),
                    UnsyncedPlanningRecord("budget-2", "profile.jurisdiction_period"),
                ),
                "budget-1",
            ),
        )
    }

    private fun queuedWrite() =
        OutboxOperationEntity(
            operationId = "edit-1",
            idempotencyKey = "edit-1-key",
            budgetId = "budget-1",
            recordType = "transaction",
            recordId = "txn-1",
            action = "UPDATE",
            baseRevision = null,
            baseSnapshotJson = null,
            patchJson = "{}",
            optimisticJson = null,
            optimisticRecordDate = null,
            optimisticAccountId = null,
            optimisticCategoryId = null,
            optimisticPayeeId = null,
            optimisticAmountMinor = null,
            optimisticBudgetMonth = null,
            status = "queued",
            attemptCount = 0,
            serverOperationId = null,
            lastErrorCode = null,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
}
