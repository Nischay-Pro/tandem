package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HingePanesRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = WindowPresets.BOOK_FOLD)
    fun panesMeetEitherSideOfAVerticalHinge() {
        val (left, right) = renderWithCentredHinge(hingeWidthPx = 40f)

        assertNear(left, composeTestRule.onNodeWithTag(LIST).getUnclippedBoundsInRoot().right)
        assertNear(right, composeTestRule.onNodeWithTag(DETAIL).getUnclippedBoundsInRoot().left)
    }

    @Test
    @Config(qualifiers = WindowPresets.PHONE)
    fun aHingeThatLeavesNarrowPanesKeepsOnePane() {
        renderWithCentredHinge(hingeWidthPx = 0f)

        composeTestRule.onNodeWithTag(LIST).assertExists()
        composeTestRule.onNodeWithTag(DETAIL).assertDoesNotExist()
    }

    private fun renderWithCentredHinge(hingeWidthPx: Float): Pair<Dp, Dp> {
        var hingeDp = 0.dp to 0.dp
        composeTestRule.setContent {
            val window = currentWindowSizeInfo()
            val density = LocalDensity.current.density
            val centre = LocalWindowInfo.current.containerSize.width / 2f
            val hinge = WindowHinge(centre - hingeWidthPx / 2, centre + hingeWidthPx / 2)
            hingeDp = (hinge.left / density).dp to (hinge.right / density).dp
            CompositionLocalProvider(LocalWindowHinge provides hinge) {
                TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                    Row(Modifier.fillMaxSize()) {
                        if (window.usesRail) Spacer(Modifier.width(TandemLayout.railWidth))
                        Box(Modifier.weight(1f)) {
                            ListDetailPanes(
                                detailKey = "detail",
                                emptyTitle = "Nothing selected",
                                emptyMessage = "Pick an item",
                                list = { Box(Modifier.fillMaxSize().testTag(LIST)) },
                                detail = { Box(Modifier.fillMaxSize().testTag(DETAIL)) },
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        return hingeDp
    }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = assertTrue(abs(expected.value - actual.value) <= 1f, "expected an edge at $expected but it was at $actual")

    private companion object {
        const val LIST = "list"
        const val DETAIL = "detail"
    }
}
