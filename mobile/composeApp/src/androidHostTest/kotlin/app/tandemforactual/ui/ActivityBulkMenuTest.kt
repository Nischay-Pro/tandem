package app.tandemforactual.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.activity.components.ActivityBulkAction
import app.tandemforactual.ui.feature.activity.components.ActivityBulkChoice
import app.tandemforactual.ui.feature.activity.components.ActivityBulkItem
import app.tandemforactual.ui.feature.activity.components.ActivityBulkMenu
import app.tandemforactual.ui.feature.activity.components.ActivityBulkOption
import app.tandemforactual.ui.feature.activity.components.ActivityBulkPicker
import app.tandemforactual.ui.feature.activity.components.ActivityBulkPickerSheet
import app.tandemforactual.ui.feature.activity.components.ActivityBulkSources
import app.tandemforactual.ui.feature.activity.components.ActivitySelectionState
import app.tandemforactual.ui.feature.activity.components.activityBulkMenuEntries
import app.tandemforactual.ui.feature.activity.components.activityBulkOptions
import app.tandemforactual.ui.feature.activity.components.activityBulkPickedEdit
import app.tandemforactual.ui.feature.activity.components.request
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityBulkMenuTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun item(
        id: String,
        amount: Long = -500,
        cleared: Boolean = false,
        reconciled: Boolean = false,
        notes: String = "",
    ) = ActivityBulkItem(id, amount, cleared, reconciled, notes)

    @Test
    fun theMenuShowsTheSelectionTotalAndOffersMergeOnlyForTwoOfTheSameAmount() {
        var chosen: ActivityBulkChoice? = null
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ActivityBulkMenu(true, listOf(item("a"), item("b", amount = -700)), "−$12.00", onDismiss = {}) { chosen = it }
            }
        }
        composeTestRule.onNodeWithText("Edit 2 transactions").assertExists()
        composeTestRule.onNodeWithText("−$12.00").assertExists()
        composeTestRule.onNodeWithText("Only transactions with the same amount merge").assertExists()
        composeTestRule.onNode(hasText("Merge") and hasClickAction() and !hasScrollAction()).assertDoesNotExist()
        composeTestRule.onNodeWithText("Remove tag").assertExists()
        composeTestRule.onNodeWithText("Set category").performClick()
        assertEquals(ActivityBulkChoice.Pick(ActivityBulkPicker.CATEGORY), chosen)
        composeTestRule.onNodeWithText("Mark cleared").performClick()
        assertEquals(ActivityBulkChoice.Run(ActivityBulkAction.Edit(BulkTransactionEdit.SetCleared(true))), chosen)
    }

    @Test
    fun menuEntriesFollowTheSelection() {
        val entries = activityBulkMenuEntries(listOf(item("a", cleared = true, notes = "#work"), item("b", cleared = true)))
        assertEquals("Mark uncleared", entries[3].title)
        assertNull(entries.first { it.title == "Remove tag" }.unavailable)
        assertNull(entries.first { it.title == "Merge" }.unavailable)
        val single = activityBulkMenuEntries(listOf(item("a")))
        assertEquals("Select 2 transactions to merge them", single.first { it.title == "Merge" }.unavailable)
    }

    @Test
    fun addingATagOffersTheTypedOneAndPicksIt() {
        var picked: Pair<ActivityBulkPicker, ActivityBulkOption>? = null
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ActivityBulkPickerSheet(ActivityBulkPicker.ADD_TAG, listOf(ActivityBulkOption("home", "#home")), onDismiss = {}) { a, b ->
                    picked = a to b
                }
            }
        }
        composeTestRule.onNodeWithText("#home").assertExists()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("work")
        composeTestRule.onNodeWithText("Add #work").performClick()
        assertEquals(ActivityBulkPicker.ADD_TAG to ActivityBulkOption("work", "#work"), picked)
    }

    @Test
    fun pickersListUsableValuesAndTurnThemIntoEdits() {
        fun record(
            id: String,
            name: String,
            body: String = "{}",
        ) = BudgetRecordEntity(
            budgetId = "b",
            recordType = "x",
            recordId = id,
            remoteJson = body,
            remoteRevision = "1",
            localJson = null,
            localDeleted = false,
            remoteDeleted = false,
            syncGeneration = "g",
            recordDate = null,
            accountId = null,
            categoryId = null,
            payeeId = null,
            amountMinor = null,
            budgetMonth = null,
            displayName = name,
        )
        val sources =
            ActivityBulkSources(
                categories = listOf(record("food", "Food"), record("old", "Old", """{"hidden":true}""")),
                payees = listOf(record("zed", "Zed"), record("cafe", "Cafe"), record("move", "Savings", """{"transfer_acct":"s"}""")),
                accounts = listOf(record("chk", "Checking"), record("gone", "Gone", """{"closed":true}""")),
                schedules =
                    listOf(LiveScheduleSummary("rent", "Rent", "2026-12-01", "{}", null, "Checking", null, null, null, "is", false)),
                knownTags = listOf("Home", "home", "bills"),
            )
        val items = listOf(item("a", notes = "#work trip"))

        fun titles(picker: ActivityBulkPicker) = activityBulkOptions(picker, sources, items).map { it.title }
        assertEquals(listOf("No category", "Food"), titles(ActivityBulkPicker.CATEGORY))
        assertEquals(listOf("No payee", "Cafe", "Zed"), titles(ActivityBulkPicker.PAYEE))
        assertEquals(listOf("Checking"), titles(ActivityBulkPicker.ACCOUNT))
        assertEquals(listOf("No schedule", "Rent"), titles(ActivityBulkPicker.SCHEDULE))
        assertEquals(listOf("#bills", "#Home", "#work"), titles(ActivityBulkPicker.ADD_TAG))
        assertEquals(listOf("#work"), titles(ActivityBulkPicker.REMOVE_TAG))

        val option = ActivityBulkOption("x", "X")
        assertEquals(BulkTransactionEdit.SetCategory("x"), activityBulkPickedEdit(ActivityBulkPicker.CATEGORY, option))
        val noPayee = ActivityBulkOption(null, "No payee")
        assertEquals(BulkTransactionEdit.SetPayee(null), activityBulkPickedEdit(ActivityBulkPicker.PAYEE, noPayee))
        assertEquals(BulkTransactionEdit.MoveToAccount("x"), activityBulkPickedEdit(ActivityBulkPicker.ACCOUNT, option))
        assertEquals(BulkTransactionEdit.LinkSchedule("x"), activityBulkPickedEdit(ActivityBulkPicker.SCHEDULE, option))
        assertEquals(BulkTransactionEdit.AddTag("x"), activityBulkPickedEdit(ActivityBulkPicker.ADD_TAG, option))
        assertEquals(BulkTransactionEdit.RemoveTag("x"), activityBulkPickedEdit(ActivityBulkPicker.REMOVE_TAG, option))
    }

    @Test
    fun reconciledTransactionsAreAskedAboutExceptWhenClearingLeavesThemAlone() {
        val selection = ActivitySelectionState()
        val reconciled = listOf(item("a", reconciled = true))
        selection.request(ActivityBulkAction.Duplicate, reconciled)
        assertEquals(ActivityBulkAction.Duplicate, selection.confirmingBulkAction)
        assertNull(selection.runningBulkAction)
        val clear = ActivityBulkAction.Edit(BulkTransactionEdit.SetCleared(true))
        selection.request(clear, reconciled)
        assertEquals(clear, selection.runningBulkAction)
        selection.request(ActivityBulkAction.Merge, listOf(item("b")))
        assertEquals(ActivityBulkAction.Merge, selection.runningBulkAction)
    }
}
