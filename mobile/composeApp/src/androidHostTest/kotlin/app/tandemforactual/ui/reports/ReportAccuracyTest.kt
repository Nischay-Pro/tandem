package app.tandemforactual.ui.reports

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.core.state.ReportPeriodMode
import app.tandemforactual.ui.feature.reports.BudgetedSpentPoint
import app.tandemforactual.ui.feature.reports.CategorySpendingPoint
import app.tandemforactual.ui.feature.reports.MonthlyMoneyPoint
import app.tandemforactual.ui.feature.reports.OTHER_CATEGORY_CHART_ID
import app.tandemforactual.ui.feature.reports.ReportDataBundle
import app.tandemforactual.ui.feature.reports.budgetedSpentPoints
import app.tandemforactual.ui.feature.reports.categoryCardRows
import app.tandemforactual.ui.feature.reports.createReportWindow
import app.tandemforactual.ui.feature.reports.monthlyMoneyPoints
import app.tandemforactual.ui.feature.reports.monthlySpendingAverage
import app.tandemforactual.ui.feature.reports.reportSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportAccuracyTest {
    @Test
    fun averageMonthlySpendingCountsOnlyMonthsSinceTheFirstActivity() {
        val window = createReportWindow("2025-12", ReportPeriodMode.RANGE, 12)!!
        val monthly =
            monthlyMoneyPoints(
                listOf(
                    MonthlyIncomeSpendingAggregate("2025-10", 500_000, 30_000),
                    MonthlyIncomeSpendingAggregate("2025-11", 500_000, 60_000),
                    MonthlyIncomeSpendingAggregate("2025-12", 500_000, 90_000),
                ),
                window,
            )
        val data = ReportDataBundle(emptyList(), monthly, monthly, emptyList(), emptyList(), null)

        val summary = reportSummary(ReportKind.SPENDING_OVER_TIME, data, "All accounts")

        assertTrue(summary.contains("across 3 full months"), summary)
        assertTrue(summary.contains("600"), summary)
    }

    @Test
    fun averageMonthlySpendingLeavesOutTheCurrentPartialMonth() {
        val points =
            listOf(
                point("2026-08", 0),
                point("2026-09", 40_000),
                point("2026-10", 0),
                point("2026-11", 5_000),
            )

        val average = monthlySpendingAverage(points, currentMonthId = "2026-11")!!

        assertEquals(20_000L, average.averageMinor)
        assertEquals(2, average.monthCount)
        assertEquals(false, average.isCurrentMonthSoFar)
    }

    @Test
    fun averageMonthlySpendingFallsBackToTheCurrentMonthSoFar() {
        val points = listOf(point("2026-10", 0), point("2026-11", 5_000))

        val average = monthlySpendingAverage(points, currentMonthId = "2026-11")!!

        assertEquals(5_000L, average.averageMinor)
        assertEquals(1, average.monthCount)
        assertTrue(average.isCurrentMonthSoFar)
        assertNull(monthlySpendingAverage(listOf(point("2026-10", 0)), currentMonthId = "2026-11"))
    }

    @Test
    fun budgetedAndSpentLeavesOutIncomeAndHiddenCategories() {
        val window = createReportWindow("2025-02", ReportPeriodMode.MONTH, 1)!!
        val points =
            budgetedSpentPoints(
                listOf(
                    budgetMonth(
                        "2025-02",
                        """{"categoryGroups":[
                            {"id":"g","categories":[
                                {"id":"food","name":"Groceries","budgeted":10000},
                                {"id":"old","name":"Old","budgeted":0,"hidden":true}]},
                            {"id":"hg","hidden":true,"categories":[{"id":"gift","name":"Gifts","budgeted":0}]},
                            {"id":"inc","is_income":true,"categories":[{"id":"salary","name":"Salary","budgeted":500000}]}]}""",
                    ),
                ),
                listOf(
                    CategoryMonthAggregate("2025-02", "food", 8_000),
                    CategoryMonthAggregate("2025-02", "old", 3_000),
                    CategoryMonthAggregate("2025-02", "gift", 2_000),
                    CategoryMonthAggregate("2025-02", "car", 1_000),
                ),
                window,
                emptyMap(),
            )

        assertEquals(listOf("food", "car"), points.map(BudgetedSpentPoint::categoryId))
        assertEquals(10_000L, points.sumOf(BudgetedSpentPoint::budgetedMinor))
    }

    @Test
    fun categoryCardListsTopFiveAndGroupsTheRestAsOther() {
        val categories = (1..8).map { CategorySpendingPoint("c$it", "Category $it", (10 - it) * 1_000L) }

        val rows = categoryCardRows(categories)

        assertEquals(listOf("c1", "c2", "c3", "c4", "c5", OTHER_CATEGORY_CHART_ID), rows.map(CategorySpendingPoint::categoryId))
        assertEquals(categories.sumOf(CategorySpendingPoint::spendingMinor), rows.sumOf(CategorySpendingPoint::spendingMinor))
        assertEquals(categories.take(6), categoryCardRows(categories.take(6)))
    }

    private fun point(
        monthId: String,
        spendingMinor: Long,
    ) = MonthlyMoneyPoint(monthId, monthId, 0, spendingMinor)

    private fun budgetMonth(
        monthId: String,
        json: String,
    ) = BudgetRecordEntity(
        budgetId = "budget",
        recordType = "budget-month",
        recordId = monthId,
        remoteJson = json,
        remoteRevision = "revision-$monthId",
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "generation",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = monthId,
    )
}
