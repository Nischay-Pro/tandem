package app.tandemforactual.ui.feature.budget

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BudgetMonthsWindowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun expandedWindowsKeepOneMonth() {
        assertEquals(1, measuredMonthCount())
    }

    @Test
    @Config(qualifiers = "w1200dp-h900dp")
    fun largeWindowsShowThreeMonths() {
        assertEquals(3, measuredMonthCount())
    }

    @Test
    @Config(qualifiers = "w1600dp-h1000dp")
    fun extraLargeWindowsShowFourMonths() {
        assertEquals(4, measuredMonthCount())
    }

    private fun measuredMonthCount(): Int {
        var count = 0
        composeTestRule.setContent {
            val window = currentWindowSizeInfo()
            Row(Modifier.fillMaxSize()) {
                if (window.usesRail) Spacer(Modifier.width(TandemLayout.railWidth))
                BoxWithConstraints(Modifier.weight(1f)) {
                    count = budgetMonthCount(window.widthClass, maxWidth - TandemSpacing.screen * 2)
                }
            }
        }
        composeTestRule.waitForIdle()
        return count
    }
}
