package app.tandemforactual.ui.feature.budget

import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.WindowWidthClass
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetMonthsLayoutTest {
    private val year = (1..12).map { "2026-${it.toString().padStart(2, '0')}" }

    @Test
    fun oneMonthBelowLargeWidth() {
        assertEquals(1, budgetMonthCount(WindowWidthClass.COMPACT, 371.dp))
        assertEquals(1, budgetMonthCount(WindowWidthClass.EXPANDED, 1_100.dp))
    }

    @Test
    fun largeWindowsFitSeveralMonths() {
        assertEquals(3, budgetMonthCount(WindowWidthClass.LARGE, 1_080.dp))
        assertEquals(4, budgetMonthCount(WindowWidthClass.EXTRA_LARGE, 1_480.dp))
        assertEquals(4, budgetMonthCount(WindowWidthClass.EXTRA_LARGE, 3_000.dp))
    }

    @Test
    fun aNarrowPaneOnALargeWindowKeepsOneMonth() {
        assertEquals(1, budgetMonthCount(WindowWidthClass.LARGE, 700.dp))
    }

    @Test
    fun theFirstWindowStartsAtTheSelectedMonth() {
        assertEquals(listOf("2026-03", "2026-04", "2026-05"), budgetMonthWindow(year, "2026-03", 3, null))
    }

    @Test
    fun theWindowStaysPutWhileTheSelectionMovesInsideIt() {
        assertEquals(listOf("2026-03", "2026-04", "2026-05"), budgetMonthWindow(year, "2026-05", 3, "2026-03"))
        assertEquals(listOf("2026-03", "2026-04", "2026-05"), budgetMonthWindow(year, "2026-03", 3, "2026-03"))
    }

    @Test
    fun theWindowSlidesOneMonthWhenTheSelectionStepsPastAnEdge() {
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), budgetMonthWindow(year, "2026-06", 3, "2026-03"))
        assertEquals(listOf("2026-02", "2026-03", "2026-04"), budgetMonthWindow(year, "2026-02", 3, "2026-03"))
    }

    @Test
    fun theWindowStaysFullAtTheEndOfTheBudget() {
        assertEquals(listOf("2026-10", "2026-11", "2026-12"), budgetMonthWindow(year, "2026-12", 3, null))
    }

    @Test
    fun aSelectedMonthWithoutDataStillShows() {
        assertEquals(listOf("2026-12", "2027-01"), budgetMonthWindow(year, "2027-01", 2, null))
    }

    @Test
    fun shortBudgetsShowWhatTheyHave() {
        assertEquals(listOf("2026-01", "2026-02"), budgetMonthWindow(year.take(2), "2026-01", 4, null))
    }
}
