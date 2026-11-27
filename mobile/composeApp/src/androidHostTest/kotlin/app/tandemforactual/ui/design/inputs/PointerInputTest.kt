package app.tandemforactual.ui.design.inputs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.core.ui.transactionRowMenuActions
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.accounts.accountRowMenuActions
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class PointerInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rightClickOnATransactionOffersWhatLongPressAndSwipeDo() {
        var opened = 0
        var selected = 0
        var deleted = 0
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TransactionRow(
                    transaction = transaction(),
                    privacyModeEnabled = false,
                    modifier = Modifier.testTag("row"),
                    onLongClick = { selected++ },
                    onSwipeDelete = { deleted++ },
                    onClick = { opened++ },
                )
            }
        }

        composeTestRule.onNodeWithTag("row").performMouseInput { rightClick() }
        composeTestRule.onNodeWithText("Delete").assertExistsAndClick()
        composeTestRule.onNodeWithTag("row").performMouseInput { rightClick() }
        composeTestRule.onNodeWithText("Select").assertExistsAndClick()

        assertEquals(0, opened)
        assertEquals(1, selected)
        assertEquals(1, deleted)
    }

    @Test
    fun menusOnlyListActionsTheRowHas() {
        assertTrue(transactionRowMenuActions(selectionMode = false, onOpen = {}, onSelect = null, onDelete = null).isEmpty())
        assertEquals(
            listOf("Open", "Delete"),
            transactionRowMenuActions(selectionMode = true, onOpen = {}, onSelect = {}, onDelete = {}).map { it.label },
        )
        assertEquals(listOf("Open", "Reorder accounts"), accountRowMenuActions({}, null, {}).map { it.label })
    }

    @Test
    fun aVerticalWheelScrollsAChartSideways() {
        val state = ScrollState(300)
        composeTestRule.setContent {
            Box(
                Modifier
                    .size(200.dp)
                    .testTag("chart")
                    .wheelScrollsHorizontally(state, reduceMotion = true)
                    .horizontalScroll(state),
            ) {
                Box(Modifier.size(1_000.dp, 200.dp))
            }
        }
        composeTestRule.waitForIdle()
        val start = state.value

        composeTestRule.onNodeWithTag("chart").performMouseInput { scroll(2f) }
        composeTestRule.waitForIdle()

        assertNotEquals(start, state.value)
    }

    private fun SemanticsNodeInteraction.assertExistsAndClick() {
        assertExists()
        performClick()
        composeTestRule.waitForIdle()
    }

    private fun transaction() =
        TransactionItem(
            payee = "Costco Gas",
            category = "Groceries",
            dateLabel = "May 14, 2026",
            amountMinorUnits = -1_245,
            icon = "C",
            recordId = "t1",
        )
}
