package app.tandemforactual.ui.design.sheets

import androidx.compose.material.Text
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h360dp")
class ShortWindowSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun taskSheetKeepsHeaderAndActionsInViewWhileItsBodyScrolls() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TandemTaskSheet(visible = true, onDismissRequest = {}, onDismissed = {}) {
                    SheetCard(
                        header = { TandemModalHeader("Move money", onClose = {}) },
                        actions = { PrimaryButton("Save", onClick = {}) },
                    ) {
                        repeat(20) { Text("Line $it") }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Move money").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed().assertWidthIsAtMost(SHEET_CARD_MAX_WIDTH)
        composeTestRule.onNodeWithTag(SHEET_CARD_BODY_TAG).performScrollToNode(hasText("Line 19"))
        composeTestRule.onNodeWithText("Line 19").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun choiceDialogCardStaysWithinItsWidthCap() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TandemChoiceDialog(visible = true, onDismissRequest = {}) {
                    SheetCard(header = { TandemModalHeader("Confirm", onClose = {}) }) { Text("Body") }
                }
            }
        }

        composeTestRule.onNodeWithText("Body").assertIsDisplayed()
        composeTestRule.onNodeWithTag(SHEET_CARD_BODY_TAG, useUnmergedTree = true).assertWidthIsAtMost(SHEET_CARD_MAX_WIDTH)
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun longChoiceListShrinksSoDoneStaysVisible() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ChoiceSheet(
                    title = "Accounts",
                    options = (1..30).map { "id$it" to "Account $it" },
                    initial = emptySet(),
                    onDismiss = {},
                    onDone = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Accounts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
    }
}

private fun SemanticsNodeInteraction.assertWidthIsAtMost(max: Dp): SemanticsNodeInteraction {
    val width = getUnclippedBoundsInRoot().width
    assertTrue(width <= max, "Width $width is above $max")
    return this
}
