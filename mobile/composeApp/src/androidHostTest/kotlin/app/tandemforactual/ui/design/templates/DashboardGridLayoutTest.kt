@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DashboardGridLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phonesStackCardsInOneColumn() {
        renderGrid()
        assertEquals(1, columnCount())
        assertReadingOrderTopToBottom()
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun expandedWindowsShowTwoColumns() {
        renderGrid()
        assertEquals(2, columnCount())
        assertFullWidthCardSpansTheGrid()
    }

    @Test
    @Config(qualifiers = "w1200dp-h900dp")
    fun largeWindowsShowThreeColumns() {
        renderGrid()
        assertEquals(3, columnCount())
        assertFullWidthCardSpansTheGrid()
    }

    @Test
    @Config(qualifiers = "w1600dp-h1000dp")
    fun extraLargeWindowsCapTheDashboardWidth() {
        renderGrid()
        assertEquals(3, columnCount())
        val header = bounds(HEADER_TAG)
        assertEquals(TandemLayout.dashboardContentWidth.value, (header.right - header.left).value, 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phonesPutSupportingContentBelowTheMainContent() {
        renderColumns()
        val primary = bounds(PRIMARY_TAG)
        val secondary = bounds(SECONDARY_TAG)
        assertEquals(primary.left, secondary.left)
        assertTrue(secondary.top >= primary.bottom)
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun expandedWindowsPutSupportingContentBesideTheMainContent() {
        renderColumns()
        val primary = bounds(PRIMARY_TAG)
        val secondary = bounds(SECONDARY_TAG)
        assertEquals(primary.top, secondary.top)
        assertTrue(secondary.left >= primary.right)
    }

    private fun renderGrid() {
        render {
            PageGrid(
                listOf(DashboardCard(HEADER_TAG, fullWidth = true) { TestCard(HEADER_TAG, 40) }) +
                    CARD_HEIGHTS.mapIndexed { index, height -> DashboardCard("card-$index") { TestCard("card-$index", height) } },
            )
        }
    }

    private fun renderColumns() {
        render {
            PageColumns(
                primary = { TestCard(PRIMARY_TAG, 200) },
                secondary = { TestCard(SECONDARY_TAG, 120) },
            )
        }
    }

    private fun render(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val window = currentWindowSizeInfo()
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Row(Modifier.fillMaxSize()) {
                    if (window.usesRail) Spacer(Modifier.width(TandemLayout.railWidth))
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun columnCount(): Int =
        CARD_HEIGHTS.indices
            .map { bounds("card-$it").left }
            .distinct()
            .size

    private fun assertReadingOrderTopToBottom() {
        val tops = CARD_HEIGHTS.indices.map { bounds("card-$it").top }
        assertEquals(tops.sortedBy { it.value }, tops)
    }

    private fun assertFullWidthCardSpansTheGrid() {
        val header = bounds(HEADER_TAG)
        val cards = CARD_HEIGHTS.indices.map { bounds("card-$it") }
        assertEquals(header.left, cards.minOf { it.left.value }.dp)
        assertEquals(header.right.value, cards.maxOf { it.right.value }, 4f)
        assertTrue(cards.all { it.top >= header.bottom })
    }

    private companion object {
        const val HEADER_TAG = "header"
        const val PRIMARY_TAG = "primary"
        const val SECONDARY_TAG = "secondary"
        val CARD_HEIGHTS = listOf(120, 80, 100, 60, 90, 110)
    }
}

@Composable
private fun TestCard(
    tag: String,
    height: Int,
) {
    Box(Modifier.fillMaxWidth().height(height.dp).testTag(tag)) { Text(tag) }
}
