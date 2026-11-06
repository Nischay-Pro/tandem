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
    val dateLabel: String,
    val amountMinorUnits: Long,
    val amountText: String,
    val clearedState: String? = null,
    val approved: Boolean? = null,
    val matched: Boolean? = null,
    val transfer: Boolean = false,
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
            append('\u0000')
            append(dateLabel.lowercase())
            append('\u0000')
            append(amountText.lowercase())
        }
}

internal data class PreparedActivitySearchFilter(
    val filter: ActivitySearchFilter,
    val expectedAmount: Long?,
    val textAmount: Long?,
)

internal data class PreparedActivitySearch(
    val normalizedQuery: String,
    val queryAmount: Long?,
    val filters: List<PreparedActivitySearchFilter>,
)

internal fun prepareActivitySearch(
    query: String,
    filters: List<ActivitySearchFilter>,
): PreparedActivitySearch =
    PreparedActivitySearch(
        normalizedQuery = query.trim().lowercase(),
        queryAmount = parseActivitySearchAmount(query),
        filters =
            filters.map { filter ->
                PreparedActivitySearchFilter(
                    filter = filter,
                    expectedAmount = filter.value.toLongOrNull(),
                    textAmount = if (filter.field == ActivitySearchField.ANY_CONTAINS) parseActivitySearchAmount(filter.value) else null,
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
    if (prepared.normalizedQuery.isNotEmpty() && !matchesAnyText(record, prepared.normalizedQuery, prepared.queryAmount)) return false
    return prepared.filters.all { filter -> matchesFilter(record, filter) }
}

internal fun activitySearchSuggestions(
    query: String,
    records: List<ActivitySearchRecord>,
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
    add(ActivitySearchField.NOTES_CONTAIN, value, "Memo contains: $value")

    records
        .map { it.account to it.accountId }
        .distinct()
        .filter { it.first.contains(value, ignoreCase = true) }
        .take(3)
        .forEach { (name, id) ->
            add(ActivitySearchField.ACCOUNT, id ?: name, "Account is: $name")
        }
    records
        .map { it.category to it.categoryId }
        .distinct()
        .filter { it.first.contains(value, ignoreCase = true) }
        .take(4)
        .forEach { (name, id) ->
            add(ActivitySearchField.CATEGORY, id ?: name, "Category is: $name")
        }
    records
        .map { it.payee to it.payeeId }
        .distinct()
        .filter { it.first.contains(value, ignoreCase = true) }
        .take(3)
        .forEach { (name, id) ->
            add(ActivitySearchField.PAYEE_CONTAINS, id ?: name, "Payee is: $name")
        }

    val month = value.takeIf { it.matches(Regex("\\d{4}-\\d{2}")) }
    val date = value.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    if (month != null) add(ActivitySearchField.DATE_MONTH, month, "Date is: $month")
    if (date != null) add(ActivitySearchField.DATE_ON, date, "Date is: $date")
    if (month == null && date == null) {
        records
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

    parseActivitySearchAmount(value)?.let { amount ->
        val amountText = formatAmount(amount)
        add(ActivitySearchField.OUTFLOW_IS, amount.toString(), "Outflow is: $amountText")
        add(ActivitySearchField.OUTFLOW_AT_LEAST, amount.toString(), "Outflow ≥ $amountText")
        add(ActivitySearchField.OUTFLOW_AT_MOST, amount.toString(), "Outflow ≤ $amountText")
        add(ActivitySearchField.INFLOW_IS, amount.toString(), "Inflow is: $amountText")
        add(ActivitySearchField.INFLOW_AT_LEAST, amount.toString(), "Inflow ≥ $amountText")
        add(ActivitySearchField.INFLOW_AT_MOST, amount.toString(), "Inflow ≤ $amountText")
    }

    when (value.lowercase()) {
        "cleared" -> add(ActivitySearchField.CLEARED, "", "Cleared")
        "uncleared" -> add(ActivitySearchField.UNCLEARED, "", "Uncleared")
        "reconciled" -> add(ActivitySearchField.RECONCILED, "", "Reconciled")
        "unreconciled" -> add(ActivitySearchField.UNRECONCILED, "", "Unreconciled")
        "approved" -> add(ActivitySearchField.APPROVED, "", "Approved")
        "unapproved" -> add(ActivitySearchField.UNAPPROVED, "", "Unapproved")
        "matched" -> add(ActivitySearchField.MATCHED, "", "Matched")
        "unmatched" -> add(ActivitySearchField.UNMATCHED, "", "Unmatched")
        "transfer", "transfers" -> add(ActivitySearchField.TRANSFER, "", "Transfer")
    }

    return suggestions
}

internal fun parseActivitySearchAmount(value: String): Long? {
    val ungrouped =
        value
            .trim()
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("¥", "")
            .replace(Regex("[A-Za-z]{3}"), "")
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

private fun matchesAnyText(
    record: ActivitySearchRecord,
    normalizedQuery: String,
    queryAmount: Long?,
): Boolean {
    val amountMatches = queryAmount != null && kotlin.math.abs(record.amountMinorUnits) == queryAmount
    return amountMatches || record.normalizedSearchText.contains(normalizedQuery)
}

private fun matchesFilter(
    record: ActivitySearchRecord,
    prepared: PreparedActivitySearchFilter,
): Boolean {
    val filter = prepared.filter
    val expectedAmount = prepared.expectedAmount
    val absoluteAmount = kotlin.math.abs(record.amountMinorUnits)
    return when (filter.field) {
        ActivitySearchField.ANY_CONTAINS -> matchesAnyText(record, filter.value.trim().lowercase(), prepared.textAmount)
        ActivitySearchField.PAYEE_CONTAINS ->
            record.payee.contains(filter.value, ignoreCase = true) || record.payeeId == filter.value
        ActivitySearchField.NOTES_CONTAIN -> record.notes.contains(filter.value, ignoreCase = true)
        ActivitySearchField.ACCOUNT -> record.accountId == filter.value || record.account.equals(filter.value, ignoreCase = true)
        ActivitySearchField.CATEGORY -> record.categoryId == filter.value || record.category.equals(filter.value, ignoreCase = true)
        ActivitySearchField.DATE_MONTH -> record.dateIso.startsWith(filter.value)
        ActivitySearchField.DATE_ON -> record.dateIso.startsWith(filter.value)
        ActivitySearchField.CLEARED -> record.clearedState == "cleared"
        ActivitySearchField.UNCLEARED -> record.clearedState == "uncleared"
        ActivitySearchField.RECONCILED -> record.clearedState == "reconciled"
        ActivitySearchField.UNRECONCILED -> record.clearedState != null && record.clearedState != "reconciled"
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
