package app.tandemforactual.ui.reports

import androidx.compose.ui.graphics.Color
import app.tandemforactual.ui.design.charts.areaChartIndexAt
import app.tandemforactual.ui.design.charts.areaChartRange
import app.tandemforactual.ui.design.charts.areaChartX
import app.tandemforactual.ui.design.charts.areaChartY
import app.tandemforactual.ui.design.theme.TandemPalette
import app.tandemforactual.ui.feature.reports.CategorySpendingPoint
import app.tandemforactual.ui.feature.reports.CustomReportShareColumn
import app.tandemforactual.ui.feature.reports.categoryChartPoints
import app.tandemforactual.ui.feature.reports.categorySliceColor
import app.tandemforactual.ui.feature.reports.customReportShareColumn
import app.tandemforactual.ui.feature.reports.netWorthPointOffsets
import app.tandemforactual.ui.feature.reports.reportAxisLabelTop
import app.tandemforactual.ui.feature.reports.reportSlotCenterX
import app.tandemforactual.ui.feature.reports.reportZeroY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportChartGeometryTest {
    @Test
    fun netWorthPointsSitInTheMiddleOfTheirMonthSlots() {
        val points = netWorthPointOffsets(listOf(100L, 200L, 300L), listOf(400L, 0L), 300f, 0f, 100f)

        assertEquals(listOf(50f, 150f, 250f), points.map { it.x })
        assertEquals(listOf(50f, 150f, 250f), (0..2).map { reportSlotCenterX(it, 3, 300f) })
        assertEquals(listOf(75f, 50f, 25f), points.map { it.y })
        assertEquals(150f, netWorthPointOffsets(listOf(5L), null, 300f, 0f, 100f).single().x)
    }

    @Test
    fun zeroLineIsInsideMixedRangesAndOnTheNearestEdgeOtherwise() {
        assertEquals(100f, reportZeroY(-100.0, 100.0, 0f, 200f))
        assertEquals(200f, reportZeroY(50.0, 150.0, 0f, 200f))
        assertEquals(0f, reportZeroY(-150.0, -50.0, 0f, 200f))
    }

    @Test
    fun axisLabelsAreCentredOnTheirGridlinesAndStayInside() {
        val tops = (0 until 5).map { reportAxisLabelTop(it, 5, 216f, 8f, 16f) }

        assertEquals(listOf(0f, 50f, 100f, 150f, 200f), tops)
        assertEquals(0f, reportAxisLabelTop(0, 5, 216f, 8f, 40f))
        assertEquals(176f, reportAxisLabelTop(4, 5, 216f, 8f, 40f))
        assertEquals(88f, reportAxisLabelTop(2, 5, 216f, 8f, 40f))
    }

    @Test
    fun categoryRingLeavesOutCategoriesWithNoSpending() {
        val points =
            categoryChartPoints(
                listOf(
                    CategorySpendingPoint("rent", "Rent", 500),
                    CategorySpendingPoint("food", "Food", 0),
                    CategorySpendingPoint("refunds", "Refunds", -20),
                ),
            )

        assertEquals(listOf("Rent"), points.map(CategorySpendingPoint::category))
        assertTrue(categoryChartPoints(listOf(CategorySpendingPoint("a", "A", 0), CategorySpendingPoint("b", "B", -5))).isEmpty())
    }

    @Test
    fun categoryRingGivesEverySliceItsOwnColourAndMutesOther() {
        val palette =
            TandemPalette(
                background = Color(0xFF000000),
                surface = Color(0xFF111111),
                raised = Color(0xFF222222),
                border = Color(0xFF333333),
                text = Color(0xFFFFFFFF),
                muted = Color(0xFF888888),
                accent = Color(0xFFC9B1FF),
                accentContent = Color(0xFF111827),
                accentSurface = Color(0xFF384B6C),
                positive = Color(0xFF73D9A4),
                syncing = Color(0xFF73B7FF),
                warning = Color(0xFFFFD18A),
                negative = Color(0xFFFF9CA5),
                negativeContent = Color(0xFF111827),
            )
        val colors = (0 until 8).map { categorySliceColor(it, isOther = false, palette = palette) }

        assertEquals(8, colors.toSet().size)
        assertTrue(palette.muted !in colors)
        assertEquals(palette.muted, categorySliceColor(3, isOther = true, palette = palette))
    }

    @Test
    fun areaChartScrubPicksTheNearestPoint() {
        assertEquals(1, areaChartIndexAt(90f, 300f, 4))
        assertEquals(1, areaChartIndexAt(149f, 300f, 4))
        assertEquals(2, areaChartIndexAt(151f, 300f, 4))
        assertEquals(0, areaChartIndexAt(-5f, 300f, 4))
        assertEquals(3, areaChartIndexAt(400f, 300f, 4))
        assertEquals(0, areaChartIndexAt(200f, 300f, 1))
        assertEquals(200f, areaChartX(2, 4, 300f))
    }

    @Test
    fun areaChartRangeAlwaysIncludesZero() {
        assertEquals(0L to 10L, areaChartRange(listOf(5L, 10L)))
        assertEquals(-5L to 10L, areaChartRange(listOf(-5L, 10L)))
        assertEquals(-10L to 0L, areaChartRange(listOf(-10L, -5L)))
        assertEquals(0L to 1L, areaChartRange(listOf(0L, 0L)))
        assertEquals(50f, areaChartY(0L, -5L to 5L, 0f, 100f))
        assertEquals(100f, areaChartY(-5L, -5L to 5L, 0f, 100f))
    }

    @Test
    fun customReportSharesNeverGoNegativeAndTotalOnlyClaimsAllWhenItCan() {
        assertEquals(CustomReportShareColumn(listOf("60%", "40%"), "100%"), customReportShareColumn(listOf(600L, 400L)))
        assertEquals(
            CustomReportShareColumn(listOf("60%", "40%", "—"), "—"),
            customReportShareColumn(listOf(600L, 400L, -200L)),
        )
        assertEquals(CustomReportShareColumn(listOf("—", "100%"), "—"), customReportShareColumn(listOf(0L, 100L)))
        assertNull(customReportShareColumn(listOf(-100L)))
    }
}
