package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.templates.LocalFloatingActionClearance
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FloatingActionClearanceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        clearance: Dp,
        page: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalFloatingActionClearance provides clearance) { page() }
                    FloatingTransactionAction(
                        compact = false,
                        enabled = true,
                        onClick = {},
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = TandemSpacing.large, bottom = TandemSpacing.large)
                                .testTag(FAB),
                    )
                }
            }
        }
    }

    private fun scrollToEnd() = repeat(12) { composeTestRule.onNodeWithTag(LIST).performTouchInput { swipeUp() } }

    private fun lastRowClearsTheButton(): Boolean {
        val row = composeTestRule.onNodeWithTag(LAST_ROW).getBoundsInRoot()
        val fab = composeTestRule.onNodeWithTag(FAB).getBoundsInRoot()
        return row.bottom <= fab.top
    }

    private val lazyPage: @Composable () -> Unit = {
        PageLazyColumn(modifier = Modifier.testTag(LIST)) {
            items((0 until ROWS).toList()) { row ->
                Box(Modifier.fillMaxWidth().height(64.dp).then(if (row == ROWS - 1) Modifier.testTag(LAST_ROW) else Modifier))
            }
        }
    }

    private val columnPage: @Composable () -> Unit = {
        PageColumn(modifier = Modifier.testTag(LIST)) {
            repeat(ROWS) { row ->
                Box(Modifier.fillMaxWidth().height(64.dp).then(if (row == ROWS - 1) Modifier.testTag(LAST_ROW) else Modifier))
            }
        }
    }

    @Test
    fun lastListRowScrollsFullyAboveTheAddButton() {
        show(FloatingActionClearance, lazyPage)
        scrollToEnd()
        composeTestRule.waitForIdle()
        assertTrue(lastRowClearsTheButton())
    }

    @Test
    fun withoutClearanceTheButtonCoversTheLastListRow() {
        show(0.dp, lazyPage)
        scrollToEnd()
        composeTestRule.waitForIdle()
        assertTrue(!lastRowClearsTheButton())
    }

    @Test
    fun lastRowOfAScrollingPageScrollsFullyAboveTheAddButton() {
        show(FloatingActionClearance, columnPage)
        scrollToEnd()
        composeTestRule.waitForIdle()
        assertTrue(lastRowClearsTheButton())
    }

    private companion object {
        const val FAB = "fab"
        const val LIST = "list"
        const val LAST_ROW = "last-row"
        const val ROWS = 30
    }
}
