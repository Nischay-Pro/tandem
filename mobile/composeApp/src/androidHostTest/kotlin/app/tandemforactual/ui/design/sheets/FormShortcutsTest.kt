package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class FormShortcutsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun keysMapToSaveEnterAndEscape() {
        assertEquals(FormKey.SAVE, formKey(Key.S, command = true, otherModifier = false))
        assertEquals(FormKey.SAVE, formKey(Key.Enter, command = true, otherModifier = false))
        assertEquals(FormKey.ENTER, formKey(Key.Enter, command = false, otherModifier = false))
        assertEquals(FormKey.ESCAPE, formKey(Key.Escape, command = false, otherModifier = false))
        assertNull(formKey(Key.S, command = false, otherModifier = false))
        assertNull(formKey(Key.Enter, command = false, otherModifier = true))
    }

    @Test
    fun commandSAndEnterPressTheOnlyPrimaryButton() {
        var saves = 0
        setSheet { TandemActionButton("Save", TandemActionButtonTone.PRIMARY) { saves++ } }

        composeTestRule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        composeTestRule.onRoot().performKeyInput { pressKey(Key.Enter) }

        assertEquals(2, saves)
    }

    @Test
    fun aDisabledPrimaryButtonIsNotPressed() {
        var saves = 0
        setSheet { TandemActionButton("Save", TandemActionButtonTone.PRIMARY, enabled = false) { saves++ } }

        composeTestRule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }

        assertEquals(0, saves)
    }

    @Test
    fun twoPrimaryButtonsLeaveTheChoiceToThePerson() {
        var saves = 0
        setSheet {
            Column {
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY) { saves++ }
                TandemActionButton("Save and add another", TandemActionButtonTone.PRIMARY) { saves++ }
                SecondaryButton("Cancel") {}
            }
        }

        composeTestRule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }

        assertEquals(0, saves)
    }

    @Test
    fun escapeClosesTheSheet() {
        var closed = 0
        setSheet(onEscape = { closed++ }) { SecondaryButton("Cancel") {} }

        composeTestRule.onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, closed)
    }

    private fun setSheet(
        onEscape: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FormShortcutHost(onEscape) { content() }
            }
        }
        composeTestRule.waitForIdle()
    }
}
