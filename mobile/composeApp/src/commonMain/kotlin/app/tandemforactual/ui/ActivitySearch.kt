package app.tandemforactual.ui

internal enum class ActivitySearchField {
    ANY_CONTAINS,
    PAYEE_CONTAINS,
    NOTES_CONTAIN,
    ACCOUNT,
    CATEGORY,
    DATE_MONTH,
    DATE_ON,
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

    val retained =
        if (selected.field == ActivitySearchField.DATE_MONTH || selected.field == ActivitySearchField.DATE_ON) {
            filters.filterNot {
                it.field == ActivitySearchField.DATE_MONTH || it.field == ActivitySearchField.DATE_ON
            }
        } else {
            filters
        }
    return retained + selected
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
    val queryAmount: Long?,
    val queryDatePrefixes: List<String>,
    val filters: List<PreparedActivitySearchFilter>,
)

internal fun prepareActivitySearch(
    query: String,
    filters: List<ActivitySearchFilter>,
    currencyCode: String? = null,
): PreparedActivitySearch =
    PreparedActivitySearch(
        normalizedQuery = query.trim().lowercase(),
        queryAmount = parseActivitySearchAmount(query, currencyCode),
        queryDatePrefixes = parseActivitySearchDatePrefixes(query),
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

    add(ActivitySearchField.ANY_CONTAINS, value, "Anything contains: $value")
    add(ActivitySearchField.PAYEE_CONTAINS, value, "Payee contains: $value")
    add(ActivitySearchField.NOTES_CONTAIN, value, "Notes contain: $value")

    records
        .asSequence()
        .map { it.account to it.accountId }
        .distinct()
        .filter { it.first.contains(value, ignoreCase = true) }
        .take(3)
        .forEach { (name, id) ->
            add(ActivitySearchField.ACCOUNT, id ?: name, "Account is: $name")
        }
    records
        .asSequence()
        .map { it.category to it.categoryId }
        .distinct()
        .filter { it.first.contains(value, ignoreCase = true) }
        .take(4)
        .forEach { (name, id) ->
            add(ActivitySearchField.CATEGORY, id ?: name, "Category is: $name")
        }
    records
        .asSequence()
        .map { it.payee to it.payeeId }
        .distinct()
        .filter { it.first.contains(value, ignoreCase = true) }
        .take(3)
        .forEach { (name, id) ->
            add(ActivitySearchField.PAYEE_CONTAINS, id ?: name, "Payee is: $name")
        }

    val month = value.takeIf(::isActivitySearchMonthId)
    val date = value.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    if (month != null) add(ActivitySearchField.DATE_MONTH, month, "Date is: $month")
    if (date != null) add(ActivitySearchField.DATE_ON, date, "Date is: $date")
    parseActivitySearchDatePrefixes(value)
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
                year != null && monthNumber != null && formatMonth(year, monthNumber).contains(value, ignoreCase = true)
            }.take(3)
            .forEach { monthId ->
                val (year, monthNumber) = monthId.split('-').map(String::toInt)
                add(ActivitySearchField.DATE_MONTH, monthId, "Date is: ${formatMonth(year, monthNumber)}")
            }
    }

    parseActivitySearchAmount(value, currencyCode)?.let { amount ->
        val amountText = formatAmount(amount)
        add(ActivitySearchField.OUTFLOW_IS, amount.toString(), "Spending is: $amountText")
        add(ActivitySearchField.OUTFLOW_AT_LEAST, amount.toString(), "Spending at least: $amountText")
        add(ActivitySearchField.OUTFLOW_AT_MOST, amount.toString(), "Spending at most: $amountText")
        add(ActivitySearchField.INFLOW_IS, amount.toString(), "Income is: $amountText")
        add(ActivitySearchField.INFLOW_AT_LEAST, amount.toString(), "Income at least: $amountText")
        add(ActivitySearchField.INFLOW_AT_MOST, amount.toString(), "Income at most: $amountText")
    }

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
    val trimmed = value.trim()
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
        ActivitySearchField.ACCOUNT -> record.accountId == filter.value || record.account.equals(filter.value, ignoreCase = true)
        ActivitySearchField.CATEGORY -> record.categoryId == filter.value || record.category.equals(filter.value, ignoreCase = true)
        ActivitySearchField.DATE_MONTH -> record.dateIso.startsWith(filter.value)
        ActivitySearchField.DATE_ON -> record.dateIso.startsWith(filter.value)
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
