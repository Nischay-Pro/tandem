package app.tandemforactual.ui.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionSyncStatusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theMostUrgentUnsyncedWritePerTransactionWins() {
        val operations =
            transactionSyncOperations(
                listOf(
                    operation("a", "t1", "queued"),
                    operation("b", "t1", "conflict"),
                    operation("c", "t2", "sending"),
                    operation("d", "t3", "committed"),
                    operation("e", "t4", "queued", recordType = "category"),
                ),
            )

        assertEquals(mapOf("t1" to "b", "t2" to "c"), operations.mapValues { it.value.operationId })
        assertTrue(transactionSyncNeedsReview("conflict"))
        assertTrue(transactionSyncNeedsReview("uncertain"))
        assertFalse(transactionSyncNeedsReview("queued"))
        assertEquals("Unconfirmed", transactionSyncShortLabel("uncertain"))
    }

    @Test
    fun rowsShowTheirSyncStateNextToTheAmount() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column(
                    Modifier.fillMaxSize().background(tandemPalette.background).padding(TandemSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    SyncNoticeCard(
                        Lucide.Clock,
                        tandemPalette.accent,
                        "2 changes waiting to sync",
                        "Saved on this device. Sent on the next sync.",
                        onClick = {},
                    )
                    listOf("queued", "conflict", "rejected", null).forEachIndexed { index, status ->
                        TransactionRow(
                            TransactionItem("NumberBarn", "Phone", "Nov 19", -2L - index, "N", recordId = "t$index"),
                            activityStyle = true,
                            showDateHeader = index == 0,
                            showDivider = true,
                            syncStatus = status,
                        ) {}
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Waiting", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Needs review", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Rejected", useUnmergedTree = true).assertExists()

        val output = File("build/reports/transaction-sync-status.png")
        output.parentFile.mkdirs()
        output.outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun operation(
        operationId: String,
        recordId: String,
        status: String,
        recordType: String = "transaction",
    ) = OutboxOperationEntity(
        operationId = operationId,
        idempotencyKey = "key-$operationId",
        budgetId = "budget-1",
        recordType = recordType,
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
