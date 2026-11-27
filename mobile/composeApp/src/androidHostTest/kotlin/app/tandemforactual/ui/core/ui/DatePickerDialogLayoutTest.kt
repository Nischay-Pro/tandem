package app.tandemforactual.ui.core.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import app.tandemforactual.ui.design.sheets.SHEET_CARD_MAX_WIDTH
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DatePickerDialogLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun showPicker(onSelect: (String) -> Unit = {}) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                DatePickerDialog(visible = true, initialDate = "2026-11-14", onDismiss = {}, onSelect = onSelect)
            }
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun tabletCardKeepsItsWidthCap() {
        showPicker()

        composeTestRule.onNodeWithText("Today").assertIsDisplayed().assertWidthIsAtMost(SHEET_CARD_MAX_WIDTH)
    }

    @Test
    @Config(qualifiers = "w411dp-h500dp")
    fun shortPhoneWindowKeepsTodayPinnedAndEveryDayReachable() {
        var picked: String? = null
        showPicker { picked = it }

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
        composeTestRule.onNodeWithText("30").performClick()

        assertEquals("2026-11-30", picked)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun landscapePhoneShowsTheMonthBesideTheDays() {
        showPicker()

        val today = composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        val lastDay = composeTestRule.onNodeWithText("30").assertIsDisplayed()
        assertTrue(lastDay.getBoundsInRoot().left > today.getBoundsInRoot().right)
    }
}

private fun SemanticsNodeInteraction.assertWidthIsAtMost(max: Dp): SemanticsNodeInteraction {
    val width = getUnclippedBoundsInRoot().width
    assertTrue(width <= max, "Width $width is above $max")
    return this
}
