package app.tandemforactual.ui.core.search

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
        if (monthOfYear != null) return monthOf(dateIso) == monthOfYear
        if (monthDay != null) return dateIso.substringOrNull(5, 10) == monthDay
        return (startInclusive == null || dateIso >= startInclusive) && (endInclusive == null || dateIso <= endInclusive)
    }
}

internal fun prepareActivitySearch(
    query: String,
    filters: List<ActivitySearchFilter>,
    currencyCode: String? = null,
    numberFormat: String? = null,
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
        queryAmount = parseActivitySearchAmount(query, currencyCode, numberFormat),
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
                            parseActivitySearchAmount(filter.value, currencyCode, numberFormat)
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

/** A typed amount in the hundredths Actual stores for every currency, reading the budget's own decimal mark first. */
internal fun parseActivitySearchAmount(
    value: String,
    currencyCode: String? = null,
    numberFormat: String? = null,
): Long? {
    val code = currencyCode?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{3}")) }
    val codePattern =
        code?.let {
            Regex("(?i)^(?:${Regex.escape(it)}\\s*)|(?:\\s*${Regex.escape(it)})$")
        }
    val ungrouped =
        (codePattern?.replace(value.trim(), "") ?: value.trim())
            .replace(Regex("R\\$|[$€£¥₹₩]"), "")
            .replace(Regex("[\\s'’]"), "")
            .trim()
    val normalized =
        when (activitySearchDecimalMark(ungrouped, budgetDecimalMark(numberFormat))) {
            '.' -> ungrouped.replace(",", "")
            ',' -> ungrouped.replace(".", "").replace(',', '.')
            else -> ungrouped.replace(",", "").replace(".", "")
        }
    if (!normalized.matches(Regex("[+-]?(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})"))) return null
    val whole = normalized.substringBefore('.').trimStart('+', '-').ifEmpty { "0" }.toLongOrNull() ?: return null
    val cents = normalized.substringAfter('.', "").padEnd(2, '0').toLong()
    if (whole > (Long.MAX_VALUE - cents) / 100) return null
    return whole * 100 + cents
}

private fun budgetDecimalMark(numberFormat: String?): Char? =
    when (numberFormat) {
        "dot-comma", "space-comma" -> ','
        "comma-dot", "apostrophe-dot", "comma-dot-in" -> '.'
        else -> null
    }

/** Which mark is the decimal one: the last of two different marks, or a lone mark not followed by a group of three digits. */
private fun activitySearchDecimalMark(
    text: String,
    budgetMark: Char?,
): Char? {
    val lastDot = text.lastIndexOf('.')
    val lastComma = text.lastIndexOf(',')
    if (lastDot < 0 && lastComma < 0) return null
    val mark = if (lastDot > lastComma) '.' else ','
    if (lastDot >= 0 && lastComma >= 0) return mark
    if (text.count { it == mark } > 1) return null
    val groupOfThree = text.length - text.lastIndexOf(mark) - 1 == 3
    return if (groupOfThree && mark != budgetMark) null else mark
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
            monthOf(record.dateIso) == filter.value.toIntOrNull()
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

/** The month of an ISO date, from both of its digits. */
private fun monthOf(dateIso: String): Int? = dateIso.substringOrNull(5, 7)?.toIntOrNull()
