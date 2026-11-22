package app.tandemforactual.data.planning

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomReportsTest {
    private fun tx(
        date: String?,
        amount: Long?,
        category: String?,
        payee: String? = "shop",
        account: String? = "checking",
        transfer: Boolean? = false,
        parent: Boolean = false,
    ) = SearchableBudgetTransaction("id", date, account, category, payee, amount, null, null, null, null, null, transfer, parent)

    private val today = "2026-11-21"

    @Test
    fun resolvesEachRange() {
        fun dates(
            range: ReportRange,
            start: String? = null,
            end: String? = null,
        ) = CustomReport("r", "R", range, startDate = start, endDate = end).dates(today)
        assertEquals("2026-11-01" to today, dates(ReportRange.THIS_MONTH))
        assertEquals("2026-10-01" to "2026-10-31", dates(ReportRange.LAST_MONTH))
        assertEquals("2026-09-01" to today, dates(ReportRange.LAST_3_MONTHS))
        assertEquals("2026-06-01" to today, dates(ReportRange.LAST_6_MONTHS))
        assertEquals("2025-12-01" to today, dates(ReportRange.LAST_12_MONTHS))
        assertEquals("2026-01-01" to today, dates(ReportRange.YEAR_TO_DATE))
        assertEquals("2025-01-01" to "2025-12-31", dates(ReportRange.LAST_YEAR))
        assertEquals("2026-02-01" to "2026-03-01", dates(ReportRange.CUSTOM, "2026-03-01", "2026-02-01"))
        assertEquals("2026-02-01" to today, dates(ReportRange.CUSTOM, "2026-02-01"))
        assertEquals(today to today, dates(ReportRange.CUSTOM))
    }

    @Test
    fun groupsAndFiltersTransactions() {
        val transactions =
            listOf(
                tx("2026-11-02", -1_000, "food", "market"),
                tx("2026-11-03", -500, "food", "cafe"),
                tx("2026-10-15", -2_000, "rent", "landlord"),
                tx("2026-10-20", 200, "food", "market"),
                tx("2026-11-01", 300_000, "salary", "employer"),
                tx("2026-11-04", 4_000, null, "friend"),
                tx("2026-11-05", -700, null, null, "card"),
                tx("2026-11-05", -900, "food", transfer = true),
                tx("2026-11-05", -900, "food", parent = true),
                tx("2026-11-05", null, "food"),
                tx(null, -10, "food"),
                tx("2026-08-01", -50, "food"),
                tx("2026-11-06", -100, "food", transfer = null),
            )
        val income = setOf("salary")
        val spending = CustomReports.run(CustomReport("r", "Spending"), transactions, income, today)
        assertEquals("2026-09-01", spending.from)
        assertEquals(
            listOf(ReportRow("rent", 2_000, 1), ReportRow("food", 1_400, 4), ReportRow(null, 700, 1)),
            spending.rows,
        )
        assertEquals(listOf("2026-09" to 0L, "2026-10" to 1_800L, "2026-11" to 2_300L), spending.months)
        assertEquals(4_100, spending.totalMinor)
        assertEquals(6, spending.count)

        val earned =
            CustomReports.run(
                CustomReport("r", "Income", flow = ReportFlow.INCOME, grouping = ReportGrouping.PAYEE),
                transactions,
                income,
                today,
            )
        assertEquals(listOf(ReportRow("employer", 300_000, 1), ReportRow("friend", 4_000, 1)), earned.rows)

        val net =
            CustomReports.run(
                CustomReport("r", "Net", ReportRange.THIS_MONTH, ReportGrouping.ACCOUNT, ReportFlow.NET),
                transactions,
                income,
                today,
            )
        assertEquals(listOf(ReportRow("checking", 302_400, 5), ReportRow("card", -700, 1)), net.rows)

        val filtered =
            CustomReports.run(
                CustomReport(
                    "r",
                    "Market",
                    ReportRange.LAST_12_MONTHS,
                    accountIds = listOf("checking"),
                    categoryIds = listOf("food"),
                    payeeIds = listOf("market", "cafe"),
                ),
                transactions,
                income,
                today,
            )
        assertEquals(listOf(ReportRow("food", 1_300, 3)), filtered.rows)
        assertEquals(12, filtered.months.size)
    }

    @Test
    fun keepsReportsAsJson() {
        val report =
            CustomReport(
                "r1",
                "Trips",
                ReportRange.CUSTOM,
                ReportGrouping.PAYEE,
                ReportFlow.NET,
                "2026-01-01",
                "2026-03-31",
                listOf("a"),
                listOf("c"),
                listOf("p"),
            )
        assertEquals(report, CustomReport.fromJson("r1", report.toJson()))
        val odd = Json.parseToJsonElement("""{"range":"NOPE","grouping":"NOPE","flow":"NOPE","accountIds":[1,{}]}""").jsonObject
        assertEquals(CustomReport("r2", "", accountIds = listOf("1")), CustomReport.fromJson("r2", odd))
    }
}
