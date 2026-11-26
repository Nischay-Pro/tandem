package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readBudgetMutationQueue
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloseAccountDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun anAccountAtZeroTodayWithAFutureTransactionAsksWhereItsBalanceGoesAndCloses() {
        val database =
            Room
                .inMemoryDatabaseBuilder<TandemDatabase>()
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        try {
            val repository = LocalBudgetRepository(database) { 100L }
            runBlocking {
                repository.applyFullSnapshot(
                    budgetId = BUDGET_ID,
                    generation = "generation-1",
                    cursor = "cursor-1",
                    expectedCheckpoint = null,
                    records = listOf(futureTransaction()),
                )
                AppPreferencesStore(database).putValue(
                    "internal:budget-cache-owner-v1",
                    """{"serverUrl":"https://old.example.test","principalId":"principal-old"}""",
                )
            }
            val session = TandemMobileSession(TandemApiClient(), database, { 100L })
            val state = AppState().apply { selectedBudgetId = BUDGET_ID }
            val card = LiveAccountSummary("card", "Card", 0L, null, false)
            val checking = LiveAccountSummary("checking", "Checking", 10_000L, null, false)
            var closed = false
            composeTestRule.setContent {
                TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                    var transferAccountId by remember { mutableStateOf("") }
                    var transferCategoryId by remember { mutableStateOf("") }
                    CloseAccountDialog(
                        liveAccount = card,
                        liveAccounts = listOf(card, checking),
                        transferAccountId = transferAccountId,
                        onTransferAccountIdChange = { transferAccountId = it },
                        transferCategoryId = transferCategoryId,
                        onTransferCategoryIdChange = { transferCategoryId = it },
                        onClose = { closed = true },
                        state = state,
                        mobileSession = session,
                        navigation =
                            AppNavigation(
                                {},
                                reset = {},
                                replaceTop = {},
                                newTransaction = {},
                                selectTab = {},
                                back = {},
                                backToList = {},
                            ),
                        scope = rememberCoroutineScope(),
                    )
                }
            }

            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodes(hasText("Transfer balance to")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule
                .onNodeWithText(
                    "in future-dated transactions, so its balance needs to go somewhere",
                    substring = true,
                ).assertExists()
            composeTestRule.onNodeWithText("Close account").assertIsNotEnabled()
            composeTestRule.onNodeWithText("Choose account").performClick()
            composeTestRule.onNodeWithText("Checking").performClick()
            composeTestRule.onNodeWithText("Close account").assertIsEnabled().performClick()
            composeTestRule.waitUntil(5_000) { closed }

            val close =
                runBlocking { repository.readBudgetMutationQueue() }
                    .items
                    .flatMap { decodeBudgetReorderQueue(it.patchJson).moves }
                    .single { it.kind == BudgetReorderKind.CLOSE_ACCOUNT }
            assertEquals("card", close.itemId)
            assertEquals(
                "checking",
                Json
                    .parseToJsonElement(close.payloadJson!!)
                    .jsonObject["transferAccountId"]!!
                    .jsonPrimitive.content,
            )
        } finally {
            database.close()
        }
    }

    private fun futureTransaction() =
        RemoteBudgetRecord(
            budgetId = BUDGET_ID,
            recordType = "transaction",
            recordId = "future-1",
            revision = "revision-1",
            bodyJson = """{"id":"future-1","amount":2500,"date":"2099-01-01","account":"card"}""",
            generation = "generation-1",
            recordDate = "2099-01-01",
            accountId = "card",
            amountMinor = 2_500L,
            budgetMonth = "2099-01",
        )

    private companion object {
        const val BUDGET_ID = "budget-1"
    }
}
