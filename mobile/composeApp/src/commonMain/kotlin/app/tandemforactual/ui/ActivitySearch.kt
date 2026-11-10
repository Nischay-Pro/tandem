package app.tandemforactual.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal enum class ActivitySearchField {
    ANY_CONTAINS,
    PAYEE_CONTAINS,
    NOTES_CONTAIN,
    TAG,
    ACCOUNT,
    CATEGORY,
    DATE_MONTH,
    DATE_ON,
    DATE_RANGE,
    DATE_MONTH_OF_YEAR,
    DATE_MONTH_DAY,
    CLEARED,
    UNCLEARED,
    RECONCILED,
    UNRECONCILED,
    APPROVED,
    UNAPPROVED,
    MATCHED,
    UNMATCHED,
    TRANSFER,
    OUTFLOW_IS,
    OUTFLOW_AT_LEAST,
    OUTFLOW_AT_MOST,
    INFLOW_IS,
    INFLOW_AT_LEAST,
    INFLOW_AT_MOST,
}

internal enum class ActivitySortOrder(
    val label: String,
) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    AMOUNT_HIGH_TO_LOW("Highest amount"),
    AMOUNT_LOW_TO_HIGH("Lowest amount"),
    PAYEE_A_TO_Z("Payee A–Z"),
}

private val activitySearchMonthIdPattern = Regex("\\d{4}-\\d{2}")

internal fun isActivitySearchMonthId(value: String): Boolean {
    if (!activitySearchMonthIdPattern.matches(value)) return false
    val month = value.takeLast(2).toIntOrNull() ?: return false
    return month in 1..12
}

internal data class ActivitySearchFilter(
    val field: ActivitySearchField,
    val value: String,
    val label: String,
)

internal data class SavedActivitySearch(
    val budgetId: String,
    val name: String,
    val query: String,
    val filters: List<ActivitySearchFilter>,
)

internal fun encodeSavedActivitySearches(searches: List<SavedActivitySearch>): String {
    val retained =
        searches
            .groupBy(SavedActivitySearch::budgetId)
            .values
            .flatMap { budgetSearches -> budgetSearches.takeLast(20) }
    return JsonArray(
        retained.map { saved ->
            buildJsonObject {
                put("budgetId", JsonPrimitive(saved.budgetId))
                put("name", JsonPrimitive(saved.name.take(80)))
                put("query", JsonPrimitive(saved.query.take(300)))
                put(
                    "filters",
                    JsonArray(
                        saved.filters.take(20).map { filter ->
                            buildJsonObject {
                                put("field", JsonPrimitive(filter.field.name))
                                put("value", JsonPrimitive(filter.value.take(300)))
                                put("label", JsonPrimitive(filter.label.take(300)))
                            }
                        },
                    ),
                )
            }
        },
    ).toString()
}

internal fun decodeSavedActivitySearches(serialized: String): List<SavedActivitySearch> {
    val root =
        try {
            Json.parseToJsonElement(serialized) as? JsonArray ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    return root
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val budgetId = item.stringValue("budgetId") ?: return@mapNotNull null
            val name = item.stringValue("name")?.take(80)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val query = item.stringValue("query")?.take(300) ?: ""
            val filters =
                (item["filters"] as? JsonArray)
                    .orEmpty()
                    .take(20)
                    .mapNotNull { filterElement ->
                        val filter = filterElement as? JsonObject ?: return@mapNotNull null
                        val field =
                            ActivitySearchField.entries.firstOrNull {
                                it.name == filter.stringValue("field")
                            } ?: return@mapNotNull null
                        val value = filter.stringValue("value")?.take(300) ?: ""
                        val label = filter.stringValue("label")?.take(300) ?: return@mapNotNull null
                        ActivitySearchFilter(field, value, label)
                    }
            SavedActivitySearch(budgetId, name, query, filters)
        }.groupBy(SavedActivitySearch::budgetId)
        .values
        .flatMap { budgetSearches -> budgetSearches.takeLast(20) }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun recentActivitySearchMonthFilters(records: List<ActivitySearchRecord>): List<ActivitySearchFilter> =
    records
        .asSequence()
        .map { it.dateIso.take(7) }
        .filter(::isActivitySearchMonthId)
        .distinct()
        .sortedDescending()
        .take(3)
        .map { monthId ->
            val year = monthId.substringBefore('-').toInt()
            val month = monthId.substringAfter('-').toInt()
            ActivitySearchFilter(ActivitySearchField.DATE_MONTH, monthId, "Date: ${formatMonth(year, month)}")
        }.toList()

internal fun toggleActivitySearchFilter(
    filters: List<ActivitySearchFilter>,
    selected: ActivitySearchFilter,
): List<ActivitySearchFilter> {
    val existing = filters.firstOrNull { it.field == selected.field && it.value == selected.value }
    if (existing != null) return filters - existing
    if (selected.field == ActivitySearchField.DATE_MONTH) {
        val equivalentActiveDateFilters = filters.filter { activityDateFilterCoversMonth(it, selected.value) }
        if (equivalentActiveDateFilters.isNotEmpty()) return filters - equivalentActiveDateFilters.toSet()
    }

    val retained =
        if (
            selected.field in
            setOf(
                ActivitySearchField.DATE_MONTH,
                ActivitySearchField.DATE_ON,
                ActivitySearchField.DATE_RANGE,
                ActivitySearchField.DATE_MONTH_OF_YEAR,
                ActivitySearchField.DATE_MONTH_DAY,
            )
        ) {
            filters.filterNot {
                it.field in
                    setOf(
                        ActivitySearchField.DATE_MONTH,
                        ActivitySearchField.DATE_ON,
                        ActivitySearchField.DATE_RANGE,
                        ActivitySearchField.DATE_MONTH_OF_YEAR,
                        ActivitySearchField.DATE_MONTH_DAY,
                    )
            }
        } else {
            filters
        }
    return retained + selected
}

internal fun isActivitySearchFilterActive(
    filters: List<ActivitySearchFilter>,
    quickFilter: ActivitySearchFilter,
): Boolean {
    if (filters.any { it.field == quickFilter.field && it.value == quickFilter.value }) return true
    return quickFilter.field == ActivitySearchField.DATE_MONTH &&
        filters.any { activityDateFilterCoversMonth(it, quickFilter.value) }
}

internal fun sortActivitySearchIndices(
    indices: List<Int>,
    records: List<ActivitySearchRecord>,
    order: ActivitySortOrder,
): List<Int> =
    indices.sortedWith { firstIndex, secondIndex ->
        val first = records[firstIndex]
        val second = records[secondIndex]
        val primary =
            when (order) {
                ActivitySortOrder.NEWEST_FIRST -> second.dateIso.compareTo(first.dateIso)
                ActivitySortOrder.OLDEST_FIRST -> first.dateIso.compareTo(second.dateIso)
                ActivitySortOrder.AMOUNT_HIGH_TO_LOW ->
                    kotlin.math.abs(second.amountMinorUnits).compareTo(kotlin.math.abs(first.amountMinorUnits))
                ActivitySortOrder.AMOUNT_LOW_TO_HIGH ->
                    kotlin.math.abs(first.amountMinorUnits).compareTo(kotlin.math.abs(second.amountMinorUnits))
                ActivitySortOrder.PAYEE_A_TO_Z -> first.payee.compareTo(second.payee, ignoreCase = true)
            }
        if (primary != 0) primary else second.dateIso.compareTo(first.dateIso)
    }

internal fun sortActivityTransactions(
    transactions: List<TransactionItem>,
    order: ActivitySortOrder,
): List<TransactionItem> =
    if (order == ActivitySortOrder.NEWEST_FIRST) {
        transactions
    } else {
        transactions.sortedWith { first, second ->
            val primary =
                when (order) {
                    ActivitySortOrder.NEWEST_FIRST -> second.dateIso.compareTo(first.dateIso)
                    ActivitySortOrder.OLDEST_FIRST -> first.dateIso.compareTo(second.dateIso)
                    ActivitySortOrder.AMOUNT_HIGH_TO_LOW ->
                        kotlin.math.abs(second.amountMinorUnits).compareTo(kotlin.math.abs(first.amountMinorUnits))
                    ActivitySortOrder.AMOUNT_LOW_TO_HIGH ->
                        kotlin.math.abs(first.amountMinorUnits).compareTo(kotlin.math.abs(second.amountMinorUnits))
                    ActivitySortOrder.PAYEE_A_TO_Z -> first.payee.compareTo(second.payee, ignoreCase = true)
                }
            if (primary != 0) primary else second.dateIso.compareTo(first.dateIso)
        }
    }

internal class ActivityTransactionSortCache {
    private var budgetId: String? = null
    private var source: List<TransactionItem>? = null
    private val byOrder = mutableMapOf<ActivitySortOrder, List<TransactionItem>>()

    fun get(
        budgetId: String,
        source: List<TransactionItem>,
        order: ActivitySortOrder,
    ): List<TransactionItem>? {
        resetFor(budgetId, source)
        return byOrder[order]
    }

    fun put(
        budgetId: String,
        source: List<TransactionItem>,
        order: ActivitySortOrder,
        sorted: List<TransactionItem>,
    ) {
        resetFor(budgetId, source)
        byOrder[order] = sorted
    }

    private fun resetFor(
        budgetId: String,
        source: List<TransactionItem>,
    ) {
        if (this.budgetId != budgetId || this.source !== source) {
            this.budgetId = budgetId
            this.source = source
            byOrder.clear()
        }
    }
}

private fun activityDateFilterCoversMonth(
    filter: ActivitySearchFilter,
    monthId: String,
): Boolean {
    if (!isActivitySearchMonthId(monthId)) return false

    val year = monthId.substringBefore('-').toIntOrNull() ?: return false
    val month = monthId.substringAfter('-').toIntOrNull() ?: return false
    val first = "$monthId-01"
    val last = "$monthId-${daysInSearchMonth(year, month).toString().padStart(2, '0')}"
    return when (filter.field) {
        ActivitySearchField.DATE_MONTH_OF_YEAR -> filter.value.toIntOrNull() == month
        ActivitySearchField.DATE_RANGE -> {
            val range = filter.value.split("..", limit = 2)
            range.size == 2 && range[0] <= first && range[1] >= last
        }
        else -> false
    }
}

internal data class ActivitySearchRecord(
    val payee: String,
    val payeeId: String?,
    val account: String,
    val accountId: String?,
    val category: String,
    val categoryId: String?,
    val notes: String,
    val dateIso: String,
    val amountMinorUnits: Long,
    val cleared: Boolean? = null,
    val reconciled: Boolean? = null,
    val approved: Boolean? = null,
    val matched: Boolean? = null,
    val transfer: Boolean = false,
    val recordId: String? = null,
) {
    val tagNames: List<String> = parseActivitySearchTags(notes)

    val normalizedSearchText: String =
        buildString {
            append(payee.lowercase())
            append('\u0000')
            append(account.lowercase())
            append('\u0000')
            append(category.lowercase())
            append('\u0000')
            append(notes.lowercase())
            append('\u0000')
            append(dateIso.lowercase())
        }
}

internal data class PreparedActivitySearchFilter(
    val filter: ActivitySearchFilter,
    val expectedAmount: Long?,
    val textAmount: Long?,
    val textDatePrefixes: List<String>,
)

internal data class PreparedActivitySearch(
    val normalizedQuery: String,
    val queryTag: String?,
    val queryAmount: Long?,
    val queryDatePrefixes: List<String>,
    val queryDate: ActivitySearchDateQuery?,
    val hasExplicitDatePrefix: Boolean,
    val filters: List<PreparedActivitySearchFilter>,
)

internal data class ActivitySearchDateQuery(
    val startInclusive: String? = null,
    val endInclusive: String? = null,
    val monthOfYear: Int? = null,
    val monthDay: String? = null,
    val label: String,
) {
    fun matches(dateIso: String): Boolean {
        if (monthOfYear != null) return dateIso.getOrNull(5)?.let { it.digitToIntOrNull() } == monthOfYear
        if (monthDay != null) return dateIso.substringOrNull(5, 10) == monthDay
        return (startInclusive == null || dateIso >= startInclusive) && (endInclusive == null || dateIso <= endInclusive)
    }
}

internal fun prepareActivitySearch(
    query: String,
    filters: List<ActivitySearchFilter>,
    currencyCode: String? = null,
): PreparedActivitySearch =
    PreparedActivitySearch(
        normalizedQuery =
            if (
                parseActivitySearchDateQuery(query) != null ||
                hasExplicitActivitySearchDatePrefix(query) ||
                parseExplicitActivitySearchTag(query) != null
            ) {
                ""
            } else {
                query.trim().lowercase()
            },
        queryTag = parseExplicitActivitySearchTag(query),
        queryAmount = parseActivitySearchAmount(query, currencyCode),
        queryDatePrefixes = parseActivitySearchDatePrefixes(query),
        queryDate = parseActivitySearchDateQuery(query),
        hasExplicitDatePrefix = hasExplicitActivitySearchDatePrefix(query),
        filters =
            filters.map { filter ->
                PreparedActivitySearchFilter(
                    filter = filter,
                    expectedAmount = filter.value.toLongOrNull(),
                    textAmount =
                        if (filter.field == ActivitySearchField.ANY_CONTAINS) {
                            parseActivitySearchAmount(filter.value, currencyCode)
                        } else {
                            null
                        },
                    textDatePrefixes =
                        if (filter.field == ActivitySearchField.ANY_CONTAINS) {
                            parseActivitySearchDatePrefixes(filter.value)
                        } else {
                            emptyList()
                        },
                )
            },
    )

internal fun matchesActivitySearch(
    record: ActivitySearchRecord,
    query: String,
    filters: List<ActivitySearchFilter>,
): Boolean = matchesActivitySearch(record, prepareActivitySearch(query, filters))

internal fun matchesActivitySearch(
    record: ActivitySearchRecord,
    prepared: PreparedActivitySearch,
): Boolean {
    if (prepared.hasExplicitDatePrefix && prepared.queryDate == null) return false
    if (prepared.queryDate != null && !prepared.queryDate.matches(record.dateIso)) return false
    if (prepared.queryTag != null && record.tagNames.none { it.equals(prepared.queryTag, ignoreCase = true) }) return false
    if (
        prepared.normalizedQuery.isNotEmpty() &&
        !matchesAnyText(record, prepared.normalizedQuery, prepared.queryAmount, prepared.queryDatePrefixes)
    ) {
        return false
    }
    return prepared.filters.all { filter -> matchesFilter(record, filter) }
}

internal fun activitySearchSuggestions(
    query: String,
    records: List<ActivitySearchRecord>,
    currencyCode: String? = null,
    formatAmount: (Long) -> String = { formatCurrency(it) },
): List<ActivitySearchFilter> {
    val value = query.trim()
    if (value.isEmpty()) return emptyList()
    if (value.startsWith("tag:", ignoreCase = true)) {
        val requestedTag = value.substringAfter(':').removePrefix("#")
        if (requestedTag.isBlank()) return emptyList()
        return records
            .asSequence()
            .flatMap { it.tagNames.asSequence() }
            .distinct()
            .filter { it.startsWith(requestedTag, ignoreCase = true) }
            .take(8)
            .map { ActivitySearchFilter(ActivitySearchField.TAG, it, "Tag: #$it") }
            .toList()
    }
    val explicitDatePrefix = hasExplicitActivitySearchDatePrefix(value)
    val dateQuery = parseActivitySearchDateQuery(value)
    val rawDateLabel = stripActivitySearchDatePrefix(value)
    val bareMonthQuery = dateQuery?.monthOfYear != null && !explicitDatePrefix
    val dateLabel = if (bareMonthQuery) rawDateLabel else dateQuery?.label ?: rawDateLabel
    if (explicitDatePrefix && dateLabel.isBlank()) return emptyList()

    val suggestions = mutableListOf<ActivitySearchFilter>()

    fun add(
        field: ActivitySearchField,
        filterValue: String,
        label: String,
    ) {
        if (suggestions.none { it.field == field && it.value == filterValue }) {
            suggestions += ActivitySearchFilter(field, filterValue, label)
        }
    }

    if (dateQuery != null && !bareMonthQuery) {
        when {
            dateQuery.monthOfYear != null ->
                add(ActivitySearchField.DATE_MONTH_OF_YEAR, dateQuery.monthOfYear.toString(), dateQuery.label)
            dateQuery.monthDay != null ->
                add(ActivitySearchField.DATE_MONTH_DAY, dateQuery.monthDay, dateQuery.label)
            dateQuery.startInclusive == dateQuery.endInclusive && dateQuery.startInclusive != null ->
                add(ActivitySearchField.DATE_ON, dateQuery.startInclusive, dateQuery.label)
            dateQuery.startInclusive != null && dateQuery.endInclusive != null ->
                searchDateMonthId(dateQuery)?.let { monthId ->
                    add(ActivitySearchField.DATE_MONTH, monthId, dateQuery.label)
                } ?: add(
                    ActivitySearchField.DATE_RANGE,
                    "${dateQuery.startInclusive}..${dateQuery.endInclusive}",
                    dateQuery.label,
                )
        }
        return suggestions
    }

    if (!explicitDatePrefix) {
        add(ActivitySearchField.ANY_CONTAINS, value, "Anything contains: $value")
        add(ActivitySearchField.PAYEE_CONTAINS, value, "Payee contains: $value")
        add(ActivitySearchField.NOTES_CONTAIN, value, "Notes contain: $value")
    }

    records
        .asSequence()
        .map { it.account to it.accountId }
        .distinct()
        .filter { it.first.contains(dateLabel, ignoreCase = true) }
        .take(3)
        .forEach { (name, id) ->
            add(ActivitySearchField.ACCOUNT, id ?: name, "Account is: $name")
        }
    records
        .asSequence()
        .map { it.category to it.categoryId }
        .distinct()
        .filter { it.first.contains(dateLabel, ignoreCase = true) }
        .take(4)
        .forEach { (name, id) ->
            add(ActivitySearchField.CATEGORY, id ?: name, "Category is: $name")
        }
    records
        .asSequence()
        .map { it.payee to it.payeeId }
        .distinct()
        .filter { it.first.contains(dateLabel, ignoreCase = true) }
        .take(3)
        .forEach { (name, id) ->
            add(ActivitySearchField.PAYEE_CONTAINS, id ?: name, "Payee is: $name")
        }
    records
        .asSequence()
        .flatMap { it.tagNames.asSequence() }
        .distinct()
        .filter { it.contains(dateLabel, ignoreCase = true) }
        .take(4)
        .forEach { tag -> add(ActivitySearchField.TAG, tag, "Tag: #$tag") }

    val month = dateLabel.takeIf(::isActivitySearchMonthId)
    val date = dateLabel.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    if (month != null) add(ActivitySearchField.DATE_MONTH, month, "Date is: $month")
    if (date != null) add(ActivitySearchField.DATE_ON, date, "Date is: $date")
    parseActivitySearchDatePrefixes(dateLabel)
        .filterNot { it == date }
        .forEach { datePrefix ->
            add(ActivitySearchField.DATE_ON, datePrefix, "Date is: $datePrefix")
        }
    if (month == null && date == null) {
        records
            .asSequence()
            .map { it.dateIso.take(7) }
            .distinct()
            .filter { monthId ->
                val parts = monthId.split('-')
                val year = parts.getOrNull(0)?.toIntOrNull()
                val monthNumber = parts.getOrNull(1)?.toIntOrNull()
                year != null && monthNumber != null && formatMonth(year, monthNumber).contains(dateLabel, ignoreCase = true)
            }.take(3)
            .forEach { monthId ->
                val (year, monthNumber) = monthId.split('-').map(String::toInt)
                add(ActivitySearchField.DATE_MONTH, monthId, "Date is: ${formatMonth(year, monthNumber)}")
            }
    }

    if (!explicitDatePrefix) {
        parseActivitySearchAmount(value, currencyCode)?.let { amount ->
            val amountText = formatAmount(amount)
            add(ActivitySearchField.OUTFLOW_IS, amount.toString(), "Spending is: $amountText")
            add(ActivitySearchField.OUTFLOW_AT_LEAST, amount.toString(), "Spending at least: $amountText")
            add(ActivitySearchField.OUTFLOW_AT_MOST, amount.toString(), "Spending at most: $amountText")
            add(ActivitySearchField.INFLOW_IS, amount.toString(), "Income is: $amountText")
            add(ActivitySearchField.INFLOW_AT_LEAST, amount.toString(), "Income at least: $amountText")
            add(ActivitySearchField.INFLOW_AT_MOST, amount.toString(), "Income at most: $amountText")
        }
    }

    if (explicitDatePrefix) return suggestions
    when (value.lowercase()) {
        "cleared" -> if (records.any { it.cleared != null }) add(ActivitySearchField.CLEARED, "", "Cleared")
        "uncleared" -> if (records.any { it.cleared != null }) add(ActivitySearchField.UNCLEARED, "", "Uncleared")
        "reconciled" -> if (records.any { it.reconciled != null }) add(ActivitySearchField.RECONCILED, "", "Reconciled")
        "unreconciled" -> if (records.any { it.reconciled != null }) add(ActivitySearchField.UNRECONCILED, "", "Unreconciled")
        "approved" -> add(ActivitySearchField.APPROVED, "", "Approved")
        "unapproved" -> add(ActivitySearchField.UNAPPROVED, "", "Unapproved")
        "matched" -> add(ActivitySearchField.MATCHED, "", "Matched")
        "unmatched" -> add(ActivitySearchField.UNMATCHED, "", "Unmatched")
        "transfer", "transfers" -> add(ActivitySearchField.TRANSFER, "", "Transfer")
    }

    return suggestions
}

internal fun parseActivitySearchAmount(
    value: String,
    currencyCode: String? = null,
): Long? {
    val code = currencyCode?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{3}")) }
    val codePattern =
        code?.let {
            Regex("(?i)^(?:${Regex.escape(it)}\\s*)|(?:\\s*${Regex.escape(it)})$")
        }
    val ungrouped =
        (codePattern?.replace(value.trim(), "") ?: value.trim())
            .replace(Regex("[$€£¥]"), "")
            .replace(Regex("\\s"), "")
            .trim()
    val decimalSeparator =
        when {
            ungrouped.lastIndexOf('.') > ungrouped.lastIndexOf(',') -> '.'
            ungrouped.lastIndexOf(',') > ungrouped.lastIndexOf('.') -> {
                if (ungrouped.substringAfterLast(',').length in 1..2) ',' else null
            }
            else -> null
        }
    val normalized =
        when (decimalSeparator) {
            '.' -> ungrouped.replace(",", "")
            ',' -> ungrouped.replace(".", "").replace(',', '.')
            else -> ungrouped.replace(",", "").replace(".", "")
        }
    if (!normalized.matches(Regex("[+-]?(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})"))) return null
    val amount = normalized.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || kotlin.math.abs(amount) > Long.MAX_VALUE / 100.0) return null
    return kotlin.math.round(kotlin.math.abs(amount) * 100.0).toLong()
}

internal fun parseActivitySearchDatePrefixes(value: String): List<String> {
    val trimmed = stripActivitySearchDatePrefix(value)
    val iso = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(trimmed)
    if (iso != null) {
        val year = iso.groupValues[1].toIntOrNull() ?: return emptyList()
        val month = iso.groupValues[2].toIntOrNull() ?: return emptyList()
        val day = iso.groupValues[3].toIntOrNull() ?: return emptyList()
        return validIsoDate(year, month, day)?.let(::listOf) ?: emptyList()
    }

    val numeric = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})$").matchEntire(trimmed) ?: return emptyList()
    val month = numeric.groupValues[1].toIntOrNull() ?: return emptyList()
    val day = numeric.groupValues[2].toIntOrNull() ?: return emptyList()
    val rawYear = numeric.groupValues[3].toIntOrNull() ?: return emptyList()
    val year = if (numeric.groupValues[3].length == 2) 2000 + rawYear else rawYear
    return validIsoDate(year, month, day)?.let(::listOf) ?: emptyList()
}

private val activitySearchMonthNames =
    listOf(
        listOf("january", "jan"),
        listOf("february", "feb"),
        listOf("march", "mar"),
        listOf("april", "apr"),
        listOf("may"),
        listOf("june", "jun"),
        listOf("july", "jul"),
        listOf("august", "aug"),
        listOf("september", "sep", "sept"),
        listOf("october", "oct"),
        listOf("november", "nov"),
        listOf("december", "dec"),
    )

private data class ActivitySearchDateEndpoint(
    val year: Int?,
    val month: Int,
    val day: Int?,
)

internal fun hasExplicitActivitySearchDatePrefix(value: String): Boolean = Regex("(?i)^date\\s*:").containsMatchIn(value.trim())

private fun stripActivitySearchDatePrefix(value: String): String = value.trim().replace(Regex("(?i)^date\\s*:\\s*"), "").trim()

internal fun parseActivitySearchDateQuery(value: String): ActivitySearchDateQuery? {
    var expression = stripActivitySearchDatePrefix(value)
    expression = expression.replace(Regex("(?i)^from\\s+"), "").replace(Regex("(?i)^between\\s+"), "")

    val rangeParts =
        Regex("(?i)\\s+(?:to|through|thru|until|and)\\s+|\\s+-\\s+|\\s*[–—]\\s*|\\s*\\.\\.\\s*")
            .split(expression, limit = 2)
            .map(String::trim)
    if (rangeParts.size == 2) {
        val first = parseActivitySearchDateEndpoint(rangeParts[0]) ?: return null
        val second = parseActivitySearchDateEndpoint(rangeParts[1]) ?: return null
        val year = first.year ?: second.year ?: return null
        val firstYear = first.year ?: year
        val secondYear = second.year ?: year
        val start = endpointStart(first, firstYear) ?: return null
        val end = endpointEnd(second, secondYear) ?: return null
        if (start > end) return null
        return ActivitySearchDateQuery(startInclusive = start, endInclusive = end, label = "Date: ${dateRangeLabel(start, end)}")
    }

    val endpoint = parseActivitySearchDateEndpoint(expression) ?: return null
    if (endpoint.year == null) {
        return when {
            endpoint.day == null ->
                ActivitySearchDateQuery(
                    monthOfYear = endpoint.month,
                    label = "Date: ${activitySearchMonthNames[endpoint.month - 1][0].replaceFirstChar(Char::uppercase)} (any year)",
                )
            else ->
                ActivitySearchDateQuery(
                    monthDay = "${endpoint.month.toString().padStart(2, '0')}-${endpoint.day.toString().padStart(2, '0')}",
                    label =
                        "Date: ${endpoint.day} ${activitySearchMonthNames[endpoint.month - 1][0].replaceFirstChar(
                            Char::uppercase,
                        )} (any year)",
                )
        }
    }

    val start = endpointStart(endpoint, endpoint.year) ?: return null
    val end = endpointEnd(endpoint, endpoint.year) ?: return null
    val label = if (endpoint.day == null) "Date: ${formatMonth(endpoint.year, endpoint.month)}" else "Date: ${dateRangeLabel(start, end)}"
    return ActivitySearchDateQuery(startInclusive = start, endInclusive = end, label = label)
}

private fun parseActivitySearchDateEndpoint(value: String): ActivitySearchDateEndpoint? {
    val trimmed = value.trim().trimEnd('.', ',')
    val iso = Regex("^(\\d{4})[-/.](\\d{1,2})(?:[-/.](\\d{1,2}))?$").matchEntire(trimmed)
    if (iso != null) {
        val year = iso.groupValues[1].toIntOrNull() ?: return null
        val month = iso.groupValues[2].toIntOrNull() ?: return null
        val day = iso.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        if (month !in 1..12) return null
        if (day != null && validIsoDate(year, month, day) == null) return null
        return ActivitySearchDateEndpoint(year, month, day)
    }

    val numericDate = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2}|\\d{4})$").matchEntire(trimmed)
    if (numericDate != null) {
        val first = numericDate.groupValues[1].toIntOrNull() ?: return null
        val second = numericDate.groupValues[2].toIntOrNull() ?: return null
        val rawYear = numericDate.groupValues[3].toIntOrNull() ?: return null
        val year = if (numericDate.groupValues[3].length == 2) 2000 + rawYear else rawYear
        val monthFirst = ActivitySearchDateEndpoint(year, first, second)
        val dayFirst = ActivitySearchDateEndpoint(year, second, first)
        if (trimmed.contains('.') && validIsoDate(year, second, first) != null) return dayFirst
        if (validIsoDate(year, first, second) != null) return monthFirst
        return dayFirst.takeIf { validIsoDate(year, second, first) != null }
    }

    val numericMonth = Regex("^(\\d{1,2})[/.\\-](\\d{4})$").matchEntire(trimmed)
    if (numericMonth != null) {
        val month = numericMonth.groupValues[1].toIntOrNull() ?: return null
        val year = numericMonth.groupValues[2].toIntOrNull() ?: return null
        return ActivitySearchDateEndpoint(year, month, null).takeIf { month in 1..12 && year > 0 }
    }

    val numericDayMonth = Regex("^(\\d{1,2})/(\\d{1,2})$").matchEntire(trimmed)
    if (numericDayMonth != null) {
        val month = numericDayMonth.groupValues[1].toIntOrNull() ?: return null
        val day = numericDayMonth.groupValues[2].toIntOrNull() ?: return null
        return ActivitySearchDateEndpoint(null, month, day).takeIf { month in 1..12 && day in 1..31 }
    }

    val monthYear = Regex("^([a-zA-Z]+)\\.?\\s+(\\d{4})$").matchEntire(trimmed)
    if (monthYear != null) {
        val month = parseMonthName(monthYear.groupValues[1]) ?: return null
        val year = monthYear.groupValues[2].toIntOrNull() ?: return null
        return ActivitySearchDateEndpoint(year, month, null).takeIf { year > 0 }
    }

    val monthDayYear = Regex("^([a-zA-Z]+)\\.?\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?$").matchEntire(trimmed)
    if (monthDayYear != null) {
        val month = parseMonthName(monthDayYear.groupValues[1]) ?: return null
        val day = monthDayYear.groupValues[2].toIntOrNull() ?: return null
        val year = monthDayYear.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        if (day !in 1..31 || (year != null && validIsoDate(year, month, day) == null)) return null
        return ActivitySearchDateEndpoint(year, month, day)
    }

    val dayMonthYear = Regex("^(\\d{1,2})\\s+([a-zA-Z]+)\\.?(?:,?\\s+(\\d{4}))?$").matchEntire(trimmed)
    if (dayMonthYear != null) {
        val day = dayMonthYear.groupValues[1].toIntOrNull() ?: return null
        val month = parseMonthName(dayMonthYear.groupValues[2]) ?: return null
        val year = dayMonthYear.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        if (day !in 1..31 || (year != null && validIsoDate(year, month, day) == null)) return null
        return ActivitySearchDateEndpoint(year, month, day)
    }

    val monthOnly = parseMonthName(trimmed) ?: return null
    return ActivitySearchDateEndpoint(null, monthOnly, null)
}

private fun parseMonthName(value: String): Int? =
    activitySearchMonthNames.indexOfFirst { value.trimEnd('.').lowercase() in it }.takeIf { it >= 0 }?.plus(1)

private fun endpointStart(
    endpoint: ActivitySearchDateEndpoint,
    year: Int,
): String? =
    if (endpoint.day == null) {
        validIsoDate(year, endpoint.month, 1)
    } else {
        validIsoDate(year, endpoint.month, endpoint.day)
    }

private fun endpointEnd(
    endpoint: ActivitySearchDateEndpoint,
    year: Int,
): String? =
    if (endpoint.day != null) {
        validIsoDate(year, endpoint.month, endpoint.day)
    } else {
        validIsoDate(year, endpoint.month, daysInSearchMonth(year, endpoint.month))
    }

private fun daysInSearchMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun dateRangeLabel(
    start: String,
    end: String,
): String {
    fun display(value: String): String {
        val (year, month, day) = value.split('-').map(String::toInt)
        return formatDate(CalendarDate(year, month, day))
    }
    return if (start == end) display(start) else "${display(start)} – ${display(end)}"
}

private fun searchDateMonthId(dateQuery: ActivitySearchDateQuery): String? {
    val start = dateQuery.startInclusive ?: return null
    val end = dateQuery.endInclusive ?: return null
    val monthId = start.take(7)
    val parts = monthId.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val lastDay = daysInSearchMonth(year, month)
    return monthId.takeIf { start == "$monthId-01" && end == "$monthId-${lastDay.toString().padStart(2, '0')}" }
}

private fun String.substringOrNull(
    startIndex: Int,
    endIndex: Int,
): String? = takeIf { length >= endIndex }?.substring(startIndex, endIndex)

private fun validIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String? {
    if (year < 1 || month !in 1..12) return null
    val daysInMonth =
        when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    if (day !in 1..daysInMonth) return null
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun matchesAnyText(
    record: ActivitySearchRecord,
    normalizedQuery: String,
    queryAmount: Long?,
    queryDatePrefixes: List<String> = emptyList(),
): Boolean {
    val amountMatches = queryAmount != null && kotlin.math.abs(record.amountMinorUnits) == queryAmount
    val dateMatches = queryDatePrefixes.any(record.dateIso::startsWith)
    return amountMatches || dateMatches || record.normalizedSearchText.contains(normalizedQuery)
}

private fun matchesFilter(
    record: ActivitySearchRecord,
    prepared: PreparedActivitySearchFilter,
): Boolean {
    val filter = prepared.filter
    val expectedAmount = prepared.expectedAmount
    val absoluteAmount = kotlin.math.abs(record.amountMinorUnits)
    return when (filter.field) {
        ActivitySearchField.ANY_CONTAINS ->
            matchesAnyText(
                record,
                filter.value.trim().lowercase(),
                prepared.textAmount,
                prepared.textDatePrefixes,
            )
        ActivitySearchField.PAYEE_CONTAINS ->
            record.payee.contains(filter.value, ignoreCase = true) || record.payeeId == filter.value
        ActivitySearchField.NOTES_CONTAIN -> record.notes.contains(filter.value, ignoreCase = true)
        ActivitySearchField.TAG -> record.tagNames.any { it.equals(filter.value, ignoreCase = true) }
        ActivitySearchField.ACCOUNT -> record.accountId == filter.value || record.account.equals(filter.value, ignoreCase = true)
        ActivitySearchField.CATEGORY -> record.categoryId == filter.value || record.category.equals(filter.value, ignoreCase = true)
        ActivitySearchField.DATE_MONTH -> record.dateIso.startsWith(filter.value)
        ActivitySearchField.DATE_ON -> record.dateIso.startsWith(filter.value)
        ActivitySearchField.DATE_RANGE -> {
            val range = filter.value.split("..", limit = 2)
            range.size == 2 && record.dateIso >= range[0] && record.dateIso <= range[1]
        }
        ActivitySearchField.DATE_MONTH_OF_YEAR ->
            record.dateIso.getOrNull(5)?.digitToIntOrNull() == filter.value.toIntOrNull()
        ActivitySearchField.DATE_MONTH_DAY -> record.dateIso.substringOrNull(5, 10) == filter.value
        ActivitySearchField.CLEARED -> record.cleared == true
        ActivitySearchField.UNCLEARED -> record.cleared == false
        ActivitySearchField.RECONCILED -> record.reconciled == true
        ActivitySearchField.UNRECONCILED -> record.reconciled == false
        ActivitySearchField.APPROVED -> record.approved == true
        ActivitySearchField.UNAPPROVED -> record.approved == false
        ActivitySearchField.MATCHED -> record.matched == true
        ActivitySearchField.UNMATCHED -> record.matched == false
        ActivitySearchField.TRANSFER -> record.transfer
        ActivitySearchField.OUTFLOW_IS -> record.amountMinorUnits < 0 && expectedAmount != null && absoluteAmount == expectedAmount
        ActivitySearchField.OUTFLOW_AT_LEAST -> record.amountMinorUnits < 0 && expectedAmount != null && absoluteAmount >= expectedAmount
        ActivitySearchField.OUTFLOW_AT_MOST -> record.amountMinorUnits < 0 && expectedAmount != null && absoluteAmount <= expectedAmount
        ActivitySearchField.INFLOW_IS -> record.amountMinorUnits > 0 && expectedAmount != null && absoluteAmount == expectedAmount
        ActivitySearchField.INFLOW_AT_LEAST -> record.amountMinorUnits > 0 && expectedAmount != null && absoluteAmount >= expectedAmount
        ActivitySearchField.INFLOW_AT_MOST -> record.amountMinorUnits > 0 && expectedAmount != null && absoluteAmount <= expectedAmount
    }
}

private fun parseExplicitActivitySearchTag(query: String): String? {
    val normalized = query.trim()
    if (!normalized.startsWith("tag:", ignoreCase = true)) return null
    return normalized.substringAfter(':').removePrefix("#").takeIf { it.isNotBlank() }
}

internal fun parseActivitySearchTags(notes: String): List<String> {
    val tags = linkedSetOf<String>()
    var index = 0
    while (index < notes.length) {
        if (notes[index] != '#' || (index > 0 && notes[index - 1] == '#')) {
            index++
            continue
        }
        val start = index + 1
        var end = start
        while (end < notes.length && notes[end] != '#' && !notes[end].isWhitespace()) end++
        if (end > start) tags += notes.substring(start, end)
        index = end.coerceAtLeast(index + 1)
    }
    return tags.toList()
}
