package app.tandemforactual.ui.design.templates

import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.WindowWidthClass
import app.tandemforactual.ui.feature.reports.reportVisibleMonths
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardLayoutTest {
    @Test
    fun phonesAndMediumWindowsKeepOneColumn() {
        assertEquals(1, dashboardColumnCount(WindowWidthClass.COMPACT, 371.dp, 16.dp))
        assertEquals(1, dashboardColumnCount(WindowWidthClass.MEDIUM, 760.dp, 16.dp))
    }

    @Test
    fun columnsGrowWithTheWindowClassAndTheRoomLeft() {
        assertEquals(2, dashboardColumnCount(WindowWidthClass.EXPANDED, 720.dp, 16.dp))
        assertEquals(3, dashboardColumnCount(WindowWidthClass.LARGE, 1_080.dp, 16.dp))
        assertEquals(3, dashboardColumnCount(WindowWidthClass.EXTRA_LARGE, 1_480.dp, 16.dp))
    }

    @Test
    fun aNarrowPaneOnAWideWindowStaysOneColumn() {
        assertEquals(1, dashboardColumnCount(WindowWidthClass.LARGE, 380.dp, 16.dp))
        assertEquals(2, dashboardColumnCount(WindowWidthClass.LARGE, 700.dp, 16.dp))
    }

    @Test
    fun widerDashboardsCentreAtTheirOwnWidth() {
        assertEquals(20.dp, dashboardPadding(760.dp, 20.dp, 1))
        assertEquals((1_520.dp - TandemLayout.dashboardContentWidth) / 2, dashboardPadding(1_520.dp, 20.dp, 3))
        assertEquals((1_520.dp - TandemLayout.readableContentWidth) / 2, dashboardPadding(1_520.dp, 20.dp, 1))
    }

    @Test
    fun cardsFillTheShortestLaneLeftToRight() {
        val slots = dashboardSlots(listOf(300, 200, 100, 100), List(4) { false }, lanes = 2, gap = 10)

        assertEquals(listOf(DashboardSlot(0, 0), DashboardSlot(1, 0), DashboardSlot(1, 210), DashboardSlot(0, 310)), slots)
        assertEquals(410, dashboardHeight(slots, listOf(300, 200, 100, 100)))
    }

    @Test
    fun nearlyEvenLanesKeepReadingOrderLeftToRight() {
        val slots = dashboardSlots(listOf(100, 95, 50), List(3) { false }, lanes = 2, gap = 10)

        assertEquals(DashboardSlot(0, 110), slots[2])
    }

    @Test
    fun fullWidthCardsStartBelowEverythingBeforeThem() {
        val slots = dashboardSlots(listOf(40, 300, 100, 60, 80), listOf(true, false, false, true, false), lanes = 2, gap = 10)

        assertEquals(DashboardSlot(0, 0), slots[0])
        assertEquals(DashboardSlot(0, 50), slots[1])
        assertEquals(DashboardSlot(1, 50), slots[2])
        assertEquals(DashboardSlot(0, 360), slots[3])
        assertEquals(DashboardSlot(0, 430), slots[4])
    }

    @Test
    fun emptyCardsTakeNoSpace() {
        val slots = dashboardSlots(listOf(0, 100), listOf(true, false), lanes = 3, gap = 10)

        assertEquals(DashboardSlot(0, 0), slots[1])
    }

    @Test
    fun reportChartsShowMoreMonthsAsTheyWiden() {
        assertEquals(6, reportVisibleMonths(280.dp))
        assertEquals(8, reportVisibleMonths(540.dp))
        assertEquals(12, reportVisibleMonths(800.dp))
        assertEquals(24, reportVisibleMonths(4_000.dp))
    }
}
