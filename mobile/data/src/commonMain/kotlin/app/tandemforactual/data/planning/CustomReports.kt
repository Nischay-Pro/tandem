package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.Day
import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ReportGrouping { CATEGORY, PAYEE, ACCOUNT }

/** Which money a report adds up: outflows, inflows, or what's left once both are counted. */
enum class ReportFlow { SPENDING, INCOME, NET }

enum class ReportRange {
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    LAST_6_MONTHS,
    LAST_12_MONTHS,
    YEAR_TO_DATE,
    LAST_YEAR,
    CUSTOM,
}

/** A report the person built and named. An empty filter list means everything of that kind is included. */
data class CustomReport(
    val id: String,
    val name: String,
    val range: ReportRange = ReportRange.LAST_3_MONTHS,
    val grouping: ReportGrouping = ReportGrouping.CATEGORY,
    val flow: ReportFlow = ReportFlow.SPENDING,
    val startDate: String? = null,
    val endDate: String? = null,
    val accountIds: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val payeeIds: List<String> = emptyList(),
) {
    /** The first and last day the report covers, both included. */
    fun dates(today: String): Pair<String, String> {
        val month = today.take(7)
        val year = today.take(4).toInt()

        fun monthsBack(count: Int) = "${SpendingInsights.shiftMonth(month, 1 - count)}-01" to today
        return when (range) {
            ReportRange.THIS_MONTH -> {
                monthsBack(1)
            }

            ReportRange.LAST_MONTH -> {
                SpendingInsights.shiftMonth(month, -1).let { "$it-01" to monthEnd(it) }
            }

            ReportRange.LAST_3_MONTHS -> {
                monthsBack(3)
            }

            ReportRange.LAST_6_MONTHS -> {
                monthsBack(6)
            }

            ReportRange.LAST_12_MONTHS -> {
                monthsBack(12)
            }

            ReportRange.YEAR_TO_DATE -> {
                "$year-01-01" to today
            }

            ReportRange.LAST_YEAR -> {
                "${year - 1}-01-01" to "${year - 1}-12-31"
            }

            ReportRange.CUSTOM -> {
                val from = startDate ?: today
                val to = endDate ?: today
                if (from <= to) from to to else to to from
            }
        }
    }

    fun toJson(): JsonObject =
        jsonOf(
            "name" to JsonPrimitive(name),
            "range" to JsonPrimitive(range.name),
            "grouping" to JsonPrimitive(grouping.name),
            "flow" to JsonPrimitive(flow.name),
            "startDate" to startDate.json(),
            "endDate" to endDate.json(),
            "accountIds" to JsonArray(accountIds.map(::JsonPrimitive)),
            "categoryIds" to JsonArray(categoryIds.map(::JsonPrimitive)),
            "payeeIds" to JsonArray(payeeIds.map(::JsonPrimitive)),
        )

    companion object {
        const val TYPE = "planning.custom_report"

        fun fromJson(
            id: String,
            body: JsonObject,
        ) = CustomReport(
            id = id,
            name = body.text("name").orEmpty(),
            range = ReportRange.entries.firstOrNull { it.name == body.text("range") } ?: ReportRange.LAST_3_MONTHS,
            grouping = ReportGrouping.entries.firstOrNull { it.name == body.text("grouping") } ?: ReportGrouping.CATEGORY,
            flow = ReportFlow.entries.firstOrNull { it.name == body.text("flow") } ?: ReportFlow.SPENDING,
            startDate = body.text("startDate"),
            endDate = body.text("endDate"),
            accountIds = body.texts("accountIds"),
            categoryIds = body.texts("categoryIds"),
            payeeIds = body.texts("payeeIds"),
        )
    }
}

/** One group in a report: the category, payee or account id (null when the transaction has none), its total and count. */
data class ReportRow(
    val key: String?,
    val totalMinor: Long,
    val count: Int,
)

data class CustomReportResult(
    val from: String,
    val to: String,
    val rows: List<ReportRow>,
    /** The total for every month in the range, oldest first, including months with nothing in them. */
    val months: List<Pair<String, Long>>,
    val totalMinor: Long,
    val count: Int,
)

object CustomReports {
    private const val MAX_MONTHS = 240

    /**
     * Runs [report] over the budget's transactions. Transfers and split parents are left out, like Actual's reports; a
     * transaction in an income category, or an uncategorized deposit, is income and everything else is spending.
     */
    fun run(
        report: CustomReport,
        transactions: List<SearchableBudgetTransaction>,
        incomeCategoryIds: Set<String>,
        today: String,
    ): CustomReportResult {
        val (from, to) = report.dates(today)
        val values =
            transactions.mapNotNull { tx ->
                val date = tx.recordDate?.takeIf { it.length >= 10 }?.take(10) ?: return@mapNotNull null
                val amount = tx.amountMinor ?: return@mapNotNull null
                val included =
                    !tx.isParent &&
                        tx.transfer != true &&
                        date in from..to &&
                        report.accountIds.matches(tx.accountId) &&
                        report.categoryIds.matches(tx.categoryId) &&
                        report.payeeIds.matches(tx.payeeId)
                if (!included) return@mapNotNull null
                val income = tx.categoryId?.let { it in incomeCategoryIds } ?: (amount > 0)
                val value =
                    when (report.flow) {
                        ReportFlow.SPENDING -> if (income) null else -amount
                        ReportFlow.INCOME -> if (income) amount else null
                        ReportFlow.NET -> amount
                    } ?: return@mapNotNull null
                val key =
                    when (report.grouping) {
                        ReportGrouping.CATEGORY -> tx.categoryId
                        ReportGrouping.PAYEE -> tx.payeeId
                        ReportGrouping.ACCOUNT -> tx.accountId
                    }
                Triple(key, date, value)
            }
        val rows =
            values
                .groupBy { it.first }
                .map { (key, list) -> ReportRow(key, list.sumOf { it.third }, list.size) }
                .sortedWith(compareByDescending<ReportRow> { it.totalMinor }.thenBy { it.key.orEmpty() })
        val byMonth = values.groupBy { it.second.take(7) }.mapValues { (_, list) -> list.sumOf { it.third } }
        val months =
            generateSequence(from.take(7)) { SpendingInsights.shiftMonth(it, 1) }
                .takeWhile { it <= to.take(7) }
                .take(MAX_MONTHS)
                .map { it to (byMonth[it] ?: 0) }
                .toList()
        return CustomReportResult(from, to, rows, months, values.sumOf { it.third }, values.size)
    }

    private fun List<String>.matches(id: String?) = isEmpty() || id in this
}

private fun monthEnd(monthId: String): String = Day.parse("${SpendingInsights.shiftMonth(monthId, 1)}-01").plus(-1L).toString()

private fun JsonObject.texts(name: String): List<String> =
    (this[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
