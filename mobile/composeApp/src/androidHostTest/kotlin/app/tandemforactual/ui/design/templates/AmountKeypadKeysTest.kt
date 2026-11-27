package app.tandemforactual.ui.design.templates

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.transactions.components.TransactionAmountEntryState
import app.tandemforactual.ui.feature.transactions.components.keypadActions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AmountKeypadKeysTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hardwareKeysEnterDigitsTheDecimalSeparatorAndFinish() {
        val state = AppState()
        val entry = TransactionAmountEntryState(keypadVisible = true)
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TransactionAmountKeypad(entry.keypadActions(state, editing = false) { entry.keypadVisible = false })
            }
        }
        val keypad = composeTestRule.onNodeWithTag(AMOUNT_KEYPAD_TAG)

        keypad.performKeyInput {
            pressKey(Key.One)
            pressKey(Key.Two)
            pressKey(Key.Period)
            pressKey(Key.Five)
        }
        composeTestRule.waitForIdle()
        assertEquals("12.50", state.transactionAmount)

        keypad.performKeyInput {
            pressKey(Key.Backspace)
            pressKey(Key.Backspace)
            pressKey(Key.NumPad7)
        }
        composeTestRule.waitForIdle()
        assertEquals("1.27", state.transactionAmount)

        keypad.performKeyInput { pressKey(Key.Enter) }
        composeTestRule.waitForIdle()
        assertFalse(entry.keypadVisible)
    }

    @Test
    fun hardwareOperatorKeysRunTheCalculator() {
        val state = AppState()
        val entry = TransactionAmountEntryState(keypadVisible = true)
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TransactionAmountKeypad(entry.keypadActions(state, editing = false) {})
            }
        }

        composeTestRule.onNodeWithTag(AMOUNT_KEYPAD_TAG).performKeyInput {
            pressKey(Key.Two)
            pressKey(Key.NumPadDot)
            pressKey(Key.NumPadAdd)
            pressKey(Key.Three)
            pressKey(Key.Period)
            pressKey(Key.NumPadEquals)
        }
        composeTestRule.waitForIdle()

        assertEquals("5.00", state.transactionAmount)
    }

    @Test
    fun decimalSeparatorTurnsTypedDigitsIntoWholeUnitsAndBackspaceUndoesIt() {
        val state = AppState()
        val entry = TransactionAmountEntryState(keypadVisible = true)
        val actions = entry.keypadActions(state, editing = false) {}

        actions.onDigit(4)
        actions.onDecimal?.invoke()
        actions.onDigit(0)
        actions.onDigit(5)
        actions.onDigit(9)
        assertEquals("4.05", state.transactionAmount)

        repeat(3) { actions.onDelete() }
        assertEquals("0.04", state.transactionAmount)
    }
}
