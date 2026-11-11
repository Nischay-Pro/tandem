package app.tandemforactual.ui.reports

import app.tandemforactual.data.database.AccountDayAggregate
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.ui.model.LiveAccountSummary
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportsPresentationTest {
    @Test
    fun reportWindowIncludesLeapDayAndCrossesYearBoundaries() {
        val leapYearWindow = createReportWindow("2024-03", ReportPeriodMode.RANGE, 3)
        assertEquals(listOf("2024-01", "2024-02", "2024-03"), leapYearWindow?.monthIds)
        assertEquals("2024-01-01", leapYearWindow?.startDate)
        assertEquals("2024-03-31", leapYearWindow?.throughDate)

        val yearBoundaryWindow = createReportWindow("2025-01", ReportPeriodMode.RANGE, 3)
        assertEquals(listOf("2024-11", "2024-12", "2025-01"), yearBoundaryWindow?.monthIds)
        assertEquals("2024-11-01", yearBoundaryWindow?.startDate)
        assertEquals("2025-01-31", yearBoundaryWindow?.throughDate)
        assertEquals(listOf("2025-01"), createReportWindow("2025-01", ReportPeriodMode.MONTH, 12)?.monthIds)
    }

    @Test
    fun reportRangePresetsCoverYearToDateAndLastYear() {
        val yearToDate = createReportWindow("2024-03", ReportPeriodMode.RANGE, ReportRangePreset.YEAR_TO_DATE, "2024-03-15")
        assertEquals("2024-01-01", yearToDate?.startDate)
        assertEquals("2024-03-15", yearToDate?.throughDate)
        assertEquals(listOf("2024-01", "2024-02", "2024-03"), yearToDate?.monthIds)

        val lastYear = createReportWindow("2024-03", ReportPeriodMode.RANGE, ReportRangePreset.LAST_YEAR, "2024-03-15")!!
        assertEquals("2023-01-01", lastYear.startDate)
        assertEquals("2023-12-31", lastYear.throughDate)
        assertEquals(12, lastYear.monthCount)
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Jan 2023 – Dec 2023", reportMonthRangeLabel(lastYear.startMonthId, lastYear.endMonthId))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun reportRangesSupportThreeYearsAndAllTime() {
        val threeYears =
            createReportWindow(
                "2026-11",
                ReportPeriodMode.RANGE,
                ReportRangePreset.LAST_3_YEARS,
                "2026-11-11",
            )
        assertEquals(36, threeYears?.monthCount)
        assertEquals("2023-12", threeYears?.startMonthId)
        assertEquals("2026-11", threeYears?.endMonthId)

        val allTime =
            createReportWindow(
                "2026-11",
                ReportPeriodMode.RANGE,
                ReportRangePreset.ALL_TIME,
                "2026-11-11",
                listOf("2023-05", "2024-12", "2026-01"),
            )!!
        assertTrue(allTime.allDates)
        assertEquals("2023-05", allTime.startMonthId)
        assertEquals("2026-11", allTime.endMonthId)
        assertEquals("2023-05-01", allTime.startDate)
        assertEquals("2026-11-11", allTime.throughDate)
        assertEquals(43, allTime.monthCount)
    }

    @Test
    fun netWorthRangeAlwaysHasSixMonths() {
        val window = createReportWindow("2024-03", ReportPeriodMode.RANGE, ReportRangePreset.LAST_6_MONTHS, "2024-03-15")

        assertEquals(listOf("2023-10", "2023-11", "2023-12", "2024-01", "2024-02", "2024-03"), window?.monthIds)
    }

    @Test
    fun categoryPointsSumAcrossMonthsAndRankByAmount() {
        val points =
            categorySpendingPoints(
                listOf(
                    CategoryMonthAggregate("2025-01", "food", 12_000),
                    CategoryMonthAggregate("2025-02", "food", 8_000),
                    CategoryMonthAggregate("2025-01", "rent", 50_000),
                    CategoryMonthAggregate("2025-02", "other", 50_000),
                ),
                mapOf("food" to "Groceries", "rent" to "Rent", "other" to "Other"),
            )

        assertEquals(listOf("Other", "Rent", "Groceries"), points.map(CategorySpendingPoint::category))
        assertEquals(listOf(50_000L, 50_000L, 20_000L), points.map(CategorySpendingPoint::spendingMinor))
    }

    @Test
    fun categoryChartGroupsSmallSlicesAsOtherWithoutDroppingSpending() {
        val categories =
            buildList {
                add(CategorySpendingPoint("rent", "Rent", 4_500))
                add(CategorySpendingPoint("food", "Groceries", 4_500))
                repeat(8) { index -> add(CategorySpendingPoint("small-$index", "Small $index", 100)) }
            }

        val chartPoints = categoryChartPoints(categories)

        assertEquals(listOf("Rent", "Groceries", "Other"), chartPoints.map(CategorySpendingPoint::category))
        assertEquals(9_800L, chartPoints.sumOf(CategorySpendingPoint::spendingMinor))
        assertEquals(800L, chartPoints.last().spendingMinor)
        assertEquals(OTHER_CATEGORY_CHART_ID, chartPoints.last().categoryId)
    }

    @Test
    fun categoryPercentageShowsShareToOneDecimalAndHandlesWholePercentages() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("33.3%", categoryPercentage(1, 3))
            assertEquals("25%", categoryPercentage(1, 4))
            assertEquals("0%", categoryPercentage(0, 4))
            assertEquals("0%", categoryPercentage(4, 0))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun categoryPercentageUsesTheCurrentLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val percentage = categoryPercentage(1, 3)
            assertTrue(percentage.contains("33,3"))
            assertTrue(percentage.contains("%"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun monthlyPointsFillMissingMonthsAndComputeNetInMinorUnits() {
        val window = createReportWindow("2025-03", ReportPeriodMode.RANGE, 3)!!
        val points =
            monthlyMoneyPoints(
                listOf(
                    MonthlyIncomeSpendingAggregate("2025-01", 300_000, 125_050),
                    MonthlyIncomeSpendingAggregate("2025-03", 100_000, 250_025),
                ),
                window,
            )

        assertEquals(window.monthIds, points.map(MonthlyMoneyPoint::monthId))
        assertEquals(listOf(300_000L, 0L, 100_000L), points.map(MonthlyMoneyPoint::incomeMinor))
        assertEquals(listOf(125_050L, 0L, 250_025L), points.map(MonthlyMoneyPoint::spendingMinor))
        assertEquals(listOf(174_950L, 0L, -150_025L), points.map(MonthlyMoneyPoint::netMinor))
    }

    @Test
    fun historicalMonthlyPointsSpanFromEarliestActivityThroughToday() {
        val window = createReportHistoryWindow(listOf("2024-12", "2025-02"), "2025-03-15")!!
        val points =
            monthlyMoneyPoints(
                listOf(
                    MonthlyIncomeSpendingAggregate("2024-12", 300_000, 125_050),
                    MonthlyIncomeSpendingAggregate("2025-02", 100_000, 250_025),
                ),
                window,
            )

        assertEquals(listOf("2024-12", "2025-01", "2025-02", "2025-03"), points.map(MonthlyMoneyPoint::monthId))
        assertEquals(listOf(300_000L, 0L, 100_000L, 0L), points.map(MonthlyMoneyPoint::incomeMinor))
    }

    @Test
    fun historicalWindowFallsBackToCurrentMonthWhenNoPriorMonthIsAvailable() {
        val window = createReportHistoryWindow(emptyList(), "2025-03-15")

        assertEquals(listOf("2025-03"), window?.monthIds)
        assertEquals("2025-03-01", window?.startDate)
        assertEquals("2025-03-15", window?.throughDate)
    }

    @Test
    fun budgetedAndSpentCombinesMonthlyPlansAndMarksOverspending() {
        val window = createReportWindow("2025-02", ReportPeriodMode.RANGE, 3)!!
        val points =
            budgetedSpentPoints(
                listOf(
                    budgetMonth(
                        "2024-12",
                        """{"categoryGroups":[{"id":"g","categories":[{"id":"food","name":"Groceries","budgeted":10000},{"id":"rent","name":"Rent","assigned":50000},{"id":"hidden","name":"Hidden","budgeted":99999,"hidden":true}]}]}""",
                    ),
                    budgetMonth(
                        "2025-01",
                        """{"categoryGroups":[{"id":"g","categories":[{"id":"food","name":"Groceries","budgeted":12000},{"id":"rent","name":"Rent","assigned":50000}]}]}""",
                    ),
                    budgetMonth(
                        "2025-02",
                        """{"categoryGroups":[{"id":"g","categories":[{"id":"food","name":"Groceries","budgeted":15000},{"id":"rent","name":"Rent","assigned":50000}]}]}""",
                    ),
                ),
                listOf(
                    CategoryMonthAggregate("2024-12", "food", 21_000),
                    CategoryMonthAggregate("2025-01", "food", 14_000),
                    CategoryMonthAggregate("2025-02", "food", 10_000),
                    CategoryMonthAggregate("2025-02", "car", 8_000),
                ),
                window,
                emptyMap(),
            ).associateBy(BudgetedSpentPoint::categoryId)

        assertEquals(setOf("food", "rent", "car"), points.keys)
        assertEquals("Groceries", points.getValue("food").category)
        assertEquals(37_000L, points.getValue("food").budgetedMinor)
        assertEquals(45_000L, points.getValue("food").spentMinor)
        assertTrue(points.getValue("food").isOverBudget)
        assertEquals(150_000L, points.getValue("rent").budgetedMinor)
        assertTrue(points.getValue("car").isOverBudget)
    }

    @Test
    fun netWorthWalksBackFromCurrentBalancesByAccountAndOmitsFutureMonths() {
        val accounts =
            listOf(
                LiveAccountSummary("savings", "Savings", 10_000, "savings", offBudget = true),
                LiveAccountSummary("checking", "Checking", 5_000, "checking", offBudget = false),
                LiveAccountSummary("credit", "Credit card", -3_000, "credit", offBudget = false),
            )
        val points =
            netWorthPoints(
                listOf(
                    AccountDayAggregate("savings", "2024-03-10", 1_000),
                    AccountDayAggregate("checking", "2024-02-25", 300),
                    AccountDayAggregate("savings", "2024-02-20", -500),
                    AccountDayAggregate("checking", "2024-01-10", -200),
                    AccountDayAggregate("savings", "2024-01-15", 2_000),
                ),
                accounts,
                listOf("2024-01", "2024-02", "2024-03", "2024-04"),
                "2024-03-15",
            )

        assertEquals(listOf("2024-01", "2024-02", "2024-03"), points.map(NetWorthPoint::monthId))
        assertEquals(listOf(11_200L, 11_000L, 12_000L), points.map(NetWorthPoint::allAccountsMinor))
        assertEquals(listOf(1_700L, 2_000L, 2_000L), points.map(NetWorthPoint::onBudgetMinor))
        assertEquals(listOf(14_200L, 14_000L, 15_000L), points.map(NetWorthPoint::allAccountsAssetsMinor))
        assertEquals(listOf(3_000L, 3_000L, 3_000L), points.map(NetWorthPoint::allAccountsDebtsMinor))
        assertEquals(listOf(4_700L, 5_000L, 5_000L), points.map(NetWorthPoint::onBudgetAssetsMinor))
        assertEquals(listOf(3_000L, 3_000L, 3_000L), points.map(NetWorthPoint::onBudgetDebtsMinor))
    }

    @Test
    fun netWorthCanBeNegativeWhenDebtsExceedAssets() {
        val points =
            netWorthPoints(
                emptyList(),
                listOf(LiveAccountSummary("credit", "Credit card", -25_000, "credit", offBudget = false)),
                listOf("2025-01"),
                "2025-01-31",
            )

        assertEquals(-25_000L, points.single().allAccountsMinor)
        assertEquals(0L, points.single().allAccountsAssetsMinor)
        assertEquals(25_000L, points.single().allAccountsDebtsMinor)
    }

    @Test
    fun reportChartAxisUsesRoundedTicksForNegativePositiveAndMixedRanges() {
        val allNegative = roundedReportAxisValues(-38_766_000.0, -10_000_000.0)
        val allPositive = roundedReportAxisValues(0.0, 3_710_000.0)
        val mixed = roundedReportAxisValues(-38_766_000.0, 2_871_000.0)

        assertTrue(allNegative.size in 4..6)
        assertTrue(allNegative.all { it % 10_000_000L == 0L })
        assertTrue(allNegative.last() < 0L)
        assertTrue(allPositive.size in 4..6)
        assertTrue(allPositive.all { it % 1_000_000L == 0L })
        assertEquals(0L, allPositive.last())
        assertTrue(mixed.size in 4..6)
        assertTrue(mixed.all { it % 10_000_000L == 0L })
        assertTrue(0L in mixed)
    }

    @Test
    fun netWorthIsEmptyWhenNoCurrentBalancesAreAvailable() {
        assertTrue(netWorthPoints(emptyList(), emptyList(), listOf("2025-01"), "2025-01-31").isEmpty())
        assertTrue(
            netWorthPoints(
                emptyList(),
                listOf(LiveAccountSummary("checking", "Checking", null, "checking", offBudget = false)),
                listOf("2025-01"),
                "2025-01-31",
            ).isEmpty(),
        )
    }

    @Test
    fun reportSummariesUseProvidedCurrencyAndCountFormatters() {
        val currency: (Long) -> String = { "¢$it" }
        val count: (Int) -> String = { "n=$it" }

        assertEquals("You spent ¢350 across n=2 categories.", categorySpendingSummary(350, 2, currency, count))
        assertEquals("Average monthly spending was ¢100 across n=3 months.", spendingOverTimeSummary(300, 3, currency, count))
        assertEquals("No monthly spending is available for this period.", spendingOverTimeSummary(0, 0, currency, count))
        assertEquals("You are spending less than you make.", incomeSpendingSummary(200))
        assertEquals("You are spending more than you make.", incomeSpendingSummary(-200))
        assertEquals("You are spending as much as you make.", incomeSpendingSummary(0))
        assertEquals("You spent ¢300 of ¢500 budgeted.", budgetedSpentSummary(500, 300, currency))
        assertEquals("Estimated net worth was ¢750 at the end of March 2025.", netWorthSummary(750, "March 2025", currency))
    }

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
