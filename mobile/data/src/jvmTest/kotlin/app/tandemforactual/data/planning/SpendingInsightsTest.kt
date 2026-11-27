package app.tandemforactual.data.planning

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpendingInsightsTest {
    private fun tx(
        date: String?,
        amount: Long?,
        category: String?,
        transfer: Boolean? = false,
        parent: Boolean = false,
    ) = SearchableBudgetTransaction("id", date, "acct", category, "payee", amount, null, null, null, null, null, transfer, parent)

    private fun spend(
        category: String,
        date: String,
        amount: Long,
    ) = SpendingEntry(category, date, amount)

    @Test
    fun readsSpendingLikeTheReports() {
        val entries =
            SpendingInsights.entries(
                listOf(
                    tx(null, -100, "food"),
                    tx("2026-11", -100, "food"),
                    tx("2026-11-02", null, "food"),
                    tx("2026-11-02", -100, "food", parent = true),
                    tx("2026-11-02", -100, "food", transfer = true),
                    tx("2026-11-02", -300, null),
                    tx("2026-11-02", 300, null),
                    tx("2026-11-02", 5_000, "salary"),
                    tx("2026-11-03T00:00", -250, "food", transfer = null),
                    tx("2026-11-04", 40, "food"),
                ),
                setOf("salary"),
            )
        assertEquals(
            listOf(spend("", "2026-11-02", 300), spend("food", "2026-11-03", 250), spend("food", "2026-11-04", -40)),
            entries,
        )
    }

    @Test
    fun comparesEachCategoryWithLastMonthAndTheAverage() {
        val entries =
            listOf(
                spend("gifts", "2026-08-10", 0),
                spend("food", "2026-09-05", 100),
                spend("food", "2026-10-05", 200),
                spend("food", "2026-11-05", 300),
                spend("rent", "2026-10-01", 1_000),
                spend("rent", "2026-11-01", 1_000),
                spend("dining", "2026-10-12", 100),
                spend("dining", "2026-11-12", 50),
                spend("fun", "2026-11-14", 40),
                spend("phone", "2026-10-03", 1_020),
                spend("phone", "2026-11-03", 1_000),
            )
        val last = SpendingInsights.trends(entries, "2026-11", TrendBaseline.LAST_MONTH, "2026-12-05")
        assertEquals(1, last.baselineMonths)
        assertNull(last.throughDay)
        val byId = last.categories.associateBy { it.categoryId }
        assertEquals(CategoryTrend("food", 300, 200, TrendDirection.UP, 5_000), byId["food"])
        assertEquals(TrendDirection.FLAT, byId.getValue("rent").direction)
        assertEquals(CategoryTrend("dining", 50, 100, TrendDirection.DOWN, -5_000), byId["dining"])
        assertEquals(CategoryTrend("fun", 40, 0, TrendDirection.UP, null), byId["fun"])
        assertEquals(TrendDirection.FLAT, byId.getValue("phone").direction)
        assertNull(byId["gifts"])
        assertEquals(listOf("food", "dining", "fun"), last.categories.take(3).map { it.categoryId })
        assertEquals(100, byId.getValue("food").changeMinor)
        assertEquals(CategoryTrend("", 2_390, 2_320, TrendDirection.FLAT, 301), last.total)

        val average = SpendingInsights.trends(entries, "2026-11", TrendBaseline.AVERAGE_6, "2026-12-05")
        assertEquals(3, average.baselineMonths)
        assertEquals(100, average.categories.first { it.categoryId == "food" }.baselineMinor)
        assertEquals(3, TrendBaseline.AVERAGE_3.months)

        val empty = SpendingInsights.trends(emptyList(), "2026-11", TrendBaseline.AVERAGE_3, "2026-11-20")
        assertEquals(0, empty.baselineMonths)
        assertEquals(TrendDirection.FLAT, empty.total.direction)
    }

    @Test
    fun projectsTheMonthFromHowSpendingUsuallyLands() {
        val entries =
            listOf(
                spend("food", "2026-10-05", 100),
                spend("food", "2026-10-20", 200),
                spend("food", "2026-11-03", 150),
                spend("rent", "2026-10-01", 1_000),
                spend("rent", "2026-11-01", 1_000),
                spend("insurance", "2026-10-25", 500),
                spend("dining", "2026-11-04", 300),
                spend("old", "2026-10-02", 50),
                spend("old", "2026-10-03", -50),
                spend("slow", "2026-10-02", 1_000),
                spend("slow", "2026-11-02", 10),
                spend("food", "2026-11-15", 999),
            )
        val forecast =
            SpendingInsights.forecast(
                entries,
                "2026-11",
                "2026-11-10",
                mapOf("food" to 400, "rent" to 1_000, "dining" to 200, "spare" to 100),
            )
        assertEquals(10, forecast.dayOfMonth)
        assertEquals(30, forecast.daysInMonth)
        val byId = forecast.categories.associateBy { it.categoryId }
        assertEquals(
            CategoryForecast("food", 150, 450, 400, ForecastBasis.HISTORY, 1, ForecastStatus.AT_RISK, "2026-11-27"),
            byId["food"],
        )
        assertEquals(ForecastStatus.ON_TRACK, byId.getValue("rent").status)
        assertEquals(1_000, byId.getValue("rent").projectedMinor)
        assertEquals(500, byId.getValue("insurance").projectedMinor)
        assertNull(byId.getValue("insurance").limitMinor)
        assertEquals(ForecastStatus.OVER, byId.getValue("dining").status)
        assertEquals(0, byId.getValue("spare").projectedMinor)
        assertNull(byId["old"])
        assertEquals(10, byId.getValue("slow").projectedMinor)
        assertEquals(forecast.categories.sumOf { it.spentMinor }, forecast.spentMinor)
        assertEquals(1_700, forecast.limitMinor)
        assertEquals(forecast.categories.sumOf { it.projectedMinor }, forecast.projectedMinor)
        assertEquals(forecast.projectedMinor - 500 - 10, forecast.budgetedProjectedMinor)
        assertEquals(
            CategoryForecast("dining", 300, 900, 200, ForecastBasis.PACE, 0, ForecastStatus.OVER, null),
            byId["dining"],
        )
    }

    @Test
    fun comparesAnUnfinishedMonthWithTheSameDaysOfEarlierMonths() {
        val entries =
            listOf(
                spend("food", "2026-09-04", 80),
                spend("food", "2026-09-25", 400),
                spend("food", "2026-10-05", 100),
                spend("food", "2026-10-20", 500),
                spend("food", "2026-11-03", 120),
                spend("food", "2026-11-28", 999),
                spend("rent", "2026-10-01", 1_000),
                spend("rent", "2026-11-01", 1_000),
            )
        val report = SpendingInsights.trends(entries, "2026-11", TrendBaseline.LAST_MONTH, "2026-11-10")
        assertEquals(10, report.throughDay)
        val food = report.categories.first { it.categoryId == "food" }
        assertEquals(CategoryTrend("food", 120, 100, TrendDirection.UP, 2_000), food)
        assertEquals(CategoryTrend("", 1_120, 1_100, TrendDirection.FLAT, 181), report.total)

        val average = SpendingInsights.trends(entries, "2026-11", TrendBaseline.AVERAGE_3, "2026-11-10")
        assertEquals(2, average.baselineMonths)
        assertEquals(90, average.categories.first { it.categoryId == "food" }.baselineMinor)

        val endOfMonth = SpendingInsights.trends(listOf(spend("food", "2026-02-28", 70)), "2026-03", TrendBaseline.LAST_MONTH, "2026-03-31")
        assertEquals(70, endOfMonth.total.baselineMinor)
    }

    @Test
    fun projectsANewCategoryFromItsPaceAndUsesOnlyMonthsSinceItStarted() {
        val entries =
            listOf(
                spend("rent", "2026-06-01", 1_000),
                spend("rent", "2026-11-01", 1_000),
                spend("gym", "2026-10-02", 30),
                spend("gym", "2026-10-25", 30),
                spend("gym", "2026-11-02", 30),
                spend("pet", "2026-11-05", 50),
            )
        val forecast = SpendingInsights.forecast(entries, "2026-11", "2026-11-10", mapOf("pet" to 100, "gym" to 100))
        val byId = forecast.categories.associateBy { it.categoryId }
        assertEquals(ForecastBasis.PACE, byId.getValue("pet").basis)
        assertEquals(150, byId.getValue("pet").projectedMinor)
        assertEquals(ForecastStatus.AT_RISK, byId.getValue("pet").status)
        assertEquals(ForecastBasis.HISTORY, byId.getValue("gym").basis)
        assertEquals(1, byId.getValue("gym").historyMonths)
        assertEquals(60, byId.getValue("gym").projectedMinor)
        assertEquals(5, byId.getValue("rent").historyMonths)
        assertEquals(210, forecast.budgetedProjectedMinor)
    }

    @Test
    fun stretchesThePaceWithoutHistoryAndKeepsFinishedMonthsFinal() {
        val entries = listOf(spend("food", "2026-11-04", 100), spend("food", "2026-11-28", 50))
        val pace = SpendingInsights.forecast(entries, "2026-11", "2026-11-10", mapOf("food" to 250))
        assertEquals(
            CategoryForecast("food", 100, 300, 250, ForecastBasis.PACE, 0, ForecastStatus.AT_RISK, "2026-11-25"),
            pace.categories.single(),
        )
        val final = SpendingInsights.forecast(entries, "2026-11", "2026-12-02", emptyMap()).categories.single()
        assertEquals(ForecastBasis.FINAL, final.basis)
        assertEquals(150, final.projectedMinor)
        val ahead = SpendingInsights.forecast(entries, "2026-12", "2026-11-10", mapOf("food" to 10))
        assertEquals(0, ahead.dayOfMonth)
        assertEquals(ForecastBasis.HISTORY, ahead.categories.single().basis)
        assertEquals(150, ahead.categories.single().projectedMinor)
        val fresh = SpendingInsights.forecast(emptyList(), "2026-12", "2026-11-10", mapOf("food" to 10)).categories.single()
        assertEquals(ForecastBasis.PACE, fresh.basis)
        assertEquals(0, fresh.projectedMinor)
    }

    @Test
    fun countsMonths() {
        assertEquals("2027-01", SpendingInsights.shiftMonth("2026-12", 1))
        assertEquals("2025-12", SpendingInsights.shiftMonth("2026-01", -1))
        assertEquals(29, SpendingInsights.daysInMonth("2028-02"))
        assertEquals(31, SpendingInsights.daysInMonth("2026-12"))
    }
}
