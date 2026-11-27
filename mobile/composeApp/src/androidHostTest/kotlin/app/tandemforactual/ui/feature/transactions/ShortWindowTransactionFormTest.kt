package app.tandemforactual.ui.feature.transactions

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.templates.AMOUNT_KEYPAD_HEIGHT
import app.tandemforactual.ui.design.templates.AMOUNT_KEYPAD_TAG
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.transactions.components.NewTransactionForm
import app.tandemforactual.ui.feature.transactions.components.TransactionFormPickerState
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ShortWindowTransactionFormTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val enterAmount =
        SemanticsMatcher("opens the amount keypad") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Enter amount" }

    private fun showNewTransaction() {
        val navigation =
            AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NewTransactionForm(
                    AppState(),
                    navigation,
                    TandemMobileSession(TandemApiClient(), null, { 0L }),
                    emptyList(),
                    TransactionFormPickerState(),
                    onCreateTransaction = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun tallWindowOpensWithTheFullKeypadUnderTheForm() {
        showNewTransaction()

        composeTestRule.onNodeWithTag(AMOUNT_KEYPAD_TAG).assertIsDisplayed().assertHeightIsEqualTo(AMOUNT_KEYPAD_HEIGHT)
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h500dp")
    fun shortWindowStartsWithTheKeypadClosedAndKeepsActionsVisibleWhenItOpens() {
        showNewTransaction()

        composeTestRule.onNodeWithTag(AMOUNT_KEYPAD_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        composeTestRule.onNode(enterAmount).performClick()

        composeTestRule.onNodeWithTag(AMOUNT_KEYPAD_TAG).assertIsDisplayed().assertHeightIsEqualTo(200.dp)
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun wideShortWindowPutsTheKeypadBesideTheForm() {
        showNewTransaction()

        val keypad = composeTestRule.onNodeWithTag(AMOUNT_KEYPAD_TAG).assertIsDisplayed().assertWidthIsEqualTo(320.dp)
        val cancel = composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        assertTrue(keypad.getBoundsInRoot().left >= cancel.getBoundsInRoot().right)
    }
}
