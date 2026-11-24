package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.tandemforactual.ui.app.MainCloudStatusButton
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.mainCloudStatusAccessibilityDescription
import app.tandemforactual.ui.core.state.resolveMainCloudStatus
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainCloudStatusButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cloudDescriptionCountsEachReviewReasonAndIgnoresOtherWriteStates() {
        assertEquals(
            "4 changes need review: 2 conflicts, 1 edit with unknown status, 1 rejected edit.",
            mainCloudStatusAccessibilityDescription(
                MainCloudStatus.ERROR,
                listOf("conflict", "conflict", "uncertain", "rejected", "queued", "sending"),
            ),
        )
        assertEquals(
            "4 changes need review: 2 edits with unknown status, 2 rejected edits.",
            mainCloudStatusAccessibilityDescription(
                MainCloudStatus.ERROR,
                listOf("uncertain", "uncertain", "rejected", "rejected"),
            ),
        )
        assertEquals(
            "1 change needs review: 1 conflict.",
            mainCloudStatusAccessibilityDescription(MainCloudStatus.ERROR, listOf("conflict")),
        )
        assertEquals(
            "1 change needs review: 1 conflict.",
            mainCloudStatusAccessibilityDescription(MainCloudStatus.OFF, listOf("conflict")),
        )
    }

    @Test
    fun cloudDescriptionKeepsGenericSyncMessagesWhenNoReviewItemsExist() {
        assertEquals(
            "Sync needs attention",
            mainCloudStatusAccessibilityDescription(MainCloudStatus.ERROR, listOf("queued", "sending")),
        )
        assertEquals(
            "Syncing changes",
            mainCloudStatusAccessibilityDescription(MainCloudStatus.SYNCING, emptyList()),
        )
    }

    @Test
    fun reviewWritesKeepTheCloudInAttentionStateEvenWhenSyncIsOffOrInProgress() {
        listOf("conflict", "uncertain", "rejected").forEach { status ->
            assertEquals(
                MainCloudStatus.ERROR,
                resolveMainCloudStatus(
                    syncEnabled = false,
                    isSyncing = false,
                    hasSyncError = false,
                    persistedSyncStatus = "READY",
                    pendingWriteStatuses = listOf(status),
                    hasSuccessfulSync = true,
                ),
                status,
            )
            assertEquals(
                MainCloudStatus.ERROR,
                resolveMainCloudStatus(
                    syncEnabled = true,
                    isSyncing = true,
                    hasSyncError = false,
                    persistedSyncStatus = "SYNCING",
                    pendingWriteStatuses = listOf(status),
                    hasSuccessfulSync = true,
                ),
                status,
            )
        }
    }

    @Test
    fun cloudButtonExposesTheCountAndReasonAsItsAccessibleDescription() {
        val description =
            "2 changes need review: 1 edit with unknown status, 1 rejected edit."
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                MainCloudStatusButton(
                    syncStatus = MainCloudStatus.ERROR,
                    pendingWriteStatuses = listOf("uncertain", "rejected"),
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(description).assertExists()
    }
}
