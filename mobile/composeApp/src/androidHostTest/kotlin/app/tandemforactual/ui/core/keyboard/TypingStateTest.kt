package app.tandemforactual.ui.core.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypingStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aFocusedTextFieldCountsAsTyping() {
        val typing = TypingState()
        composeTestRule.setContent {
            typing.TrackTyping {
                Column {
                    Text("Budget")
                    BasicTextField("", {}, Modifier.testTag("field"))
                }
            }
        }
        composeTestRule.waitForIdle()
        assertFalse(typing.active)

        composeTestRule.onNodeWithTag("field").performClick()
        composeTestRule.waitForIdle()

        assertTrue(typing.active)
    }
}
