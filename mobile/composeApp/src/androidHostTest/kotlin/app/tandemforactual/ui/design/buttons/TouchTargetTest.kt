package app.tandemforactual.ui.design.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.inputs.PillGrid
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.templates.CompactActionButton
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchTargetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun smallControlsKeepAFullSizeTouchTarget() {
        var picked = ""
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column {
                    FilterPills(listOf("all" to "All", "open" to "Open"), selected = "all", onSelect = { picked = it })
                    PillGrid(listOf("week" to "Week", "month" to "Month"), selected = "week", onSelect = { picked = it }, columns = 2)
                    SegmentedSelector(listOf("Spent" to true, "Left" to false), onSelect = {})
                    CompactActionButton("Edit", onClick = {})
                    ReorderDragHandle(
                        itemName = "Rent",
                        enabled = true,
                        canMoveUp = true,
                        canMoveDown = true,
                        onMoveUp = {},
                        onMoveDown = {},
                    )
                    CollapsibleSectionCard(title = "Bills", expanded = true, onToggleExpanded = {}) {}
                }
            }
        }

        listOf("All", "Week", "Spent", "Edit").forEach {
            composeTestRule.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
        composeTestRule.onNodeWithText("Open").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("open", picked)
        composeTestRule.onNodeWithText("Month").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("All").assertIsSelected()
        composeTestRule
            .onNodeWithContentDescription("Drag to reorder Rent")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeTestRule
            .onNodeWithContentDescription("Collapse Bills")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
