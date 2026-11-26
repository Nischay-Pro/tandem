package app.tandemforactual.ui.design.sheets

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChoiceSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rowsAreFullSizeCheckboxesThatReportTheirState() {
        var done: Set<String>? = null
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ChoiceSheet(
                    title = "Accounts",
                    options = listOf("checking" to "Checking", "savings" to "Savings"),
                    initial = setOf("checking"),
                    onDismiss = {},
                    onDone = { done = it },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Checking")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("Savings").assertIsOff().performClick()
        composeTestRule.onNodeWithText("Savings").assertIsOn()
        composeTestRule.onNodeWithText("Checking").performClick()
        composeTestRule.onNodeWithText("Checking").assertIsOff()
        composeTestRule.onNodeWithText("Done").performClick()

        assertEquals(setOf("savings"), done)
    }
}
