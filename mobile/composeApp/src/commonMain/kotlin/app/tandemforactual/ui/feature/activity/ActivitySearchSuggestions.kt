package app.tandemforactual.ui.feature.activity

import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.search.ActivitySearchDateQuery
import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.search.ActivitySearchRecord
import app.tandemforactual.ui.core.search.hasExplicitActivitySearchDatePrefix
import app.tandemforactual.ui.core.search.isActivitySearchMonthId
import app.tandemforactual.ui.core.search.parseActivitySearchAmount
import app.tandemforactual.ui.core.search.parseActivitySearchDatePrefixes
import app.tandemforactual.ui.core.search.parseActivitySearchDateQuery
import app.tandemforactual.ui.core.search.searchDateMonthId
import app.tandemforactual.ui.core.search.stripActivitySearchDatePrefix

internal fun activitySearchSuggestions(
    query: String,
    records: List<ActivitySearchRecord>,
    currencyCode: String? = null,
    numberFormat: String? = null,
    formatAmount: (Long) -> String = { formatCurrency(it) },
): List<ActivitySearchFilter> {
    val value = query.trim()
    if (value.isEmpty()) return emptyList()
    if (value.startsWith("tag:", ignoreCase = true)) return tagSuggestions(value, records)
    val explicitDatePrefix = hasExplicitActivitySearchDatePrefix(value)
    val dateQuery = parseActivitySearchDateQuery(value)
    val rawDateLabel = stripActivitySearchDatePrefix(value)
    val bareMonthQuery = dateQuery?.monthOfYear != null && !explicitDatePrefix
    val dateLabel = if (bareMonthQuery) rawDateLabel else dateQuery?.label ?: rawDateLabel
    if (explicitDatePrefix && dateLabel.isBlank()) return emptyList()

    val suggestions = ActivitySearchSuggestionList()
    if (dateQuery != null && !bareMonthQuery) {
        suggestions.addDateQuery(dateQuery)
        return suggestions.items
    }
    if (!explicitDatePrefix) {
        suggestions.add(ActivitySearchField.ANY_CONTAINS, value, "Anything contains: $value")
        suggestions.add(ActivitySearchField.PAYEE_CONTAINS, value, "Payee contains: $value")
        suggestions.add(ActivitySearchField.NOTES_CONTAIN, value, "Notes contain: $value")
    }
    suggestions.addRecordMatches(records, dateLabel)
    suggestions.addDateMatches(records, dateLabel)
    if (!explicitDatePrefix) suggestions.addAmounts(value, currencyCode, numberFormat, formatAmount)
    if (explicitDatePrefix) return suggestions.items
    suggestions.addStatus(value, records)
    return suggestions.items
}

private class ActivitySearchSuggestionList {
    val items = mutableListOf<ActivitySearchFilter>()

    fun add(
        field: ActivitySearchField,
        filterValue: String,
        label: String,
    ) {
        if (items.none { it.field == field && it.value == filterValue }) {
            items += ActivitySearchFilter(field, filterValue, label)
        }
    }
}

private fun tagSuggestions(
    value: String,
    records: List<ActivitySearchRecord>,
): List<ActivitySearchFilter> {
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

private fun ActivitySearchSuggestionList.addDateQuery(dateQuery: ActivitySearchDateQuery) {
    when {
        dateQuery.monthOfYear != null -> {
            add(ActivitySearchField.DATE_MONTH_OF_YEAR, dateQuery.monthOfYear.toString(), dateQuery.label)
        }

        dateQuery.monthDay != null -> {
            add(ActivitySearchField.DATE_MONTH_DAY, dateQuery.monthDay, dateQuery.label)
        }

        dateQuery.startInclusive == dateQuery.endInclusive && dateQuery.startInclusive != null -> {
            add(ActivitySearchField.DATE_ON, dateQuery.startInclusive, dateQuery.label)
        }

        dateQuery.startInclusive != null && dateQuery.endInclusive != null -> {
            searchDateMonthId(dateQuery)?.let { monthId ->
                add(ActivitySearchField.DATE_MONTH, monthId, dateQuery.label)
            } ?: add(
                ActivitySearchField.DATE_RANGE,
                "${dateQuery.startInclusive}..${dateQuery.endInclusive}",
                dateQuery.label,
            )
        }
    }
}

private fun ActivitySearchSuggestionList.addRecordMatches(
    records: List<ActivitySearchRecord>,
    dateLabel: String,
) {
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
}

private fun ActivitySearchSuggestionList.addDateMatches(
    records: List<ActivitySearchRecord>,
    dateLabel: String,
) {
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
}

private fun ActivitySearchSuggestionList.addAmounts(
    value: String,
    currencyCode: String?,
    numberFormat: String?,
    formatAmount: (Long) -> String,
) {
    parseActivitySearchAmount(value, currencyCode, numberFormat)?.let { amount ->
        val amountText = formatAmount(amount)
        add(ActivitySearchField.OUTFLOW_IS, amount.toString(), "Spending is: $amountText")
        add(ActivitySearchField.OUTFLOW_AT_LEAST, amount.toString(), "Spending at least: $amountText")
        add(ActivitySearchField.OUTFLOW_AT_MOST, amount.toString(), "Spending at most: $amountText")
        add(ActivitySearchField.INFLOW_IS, amount.toString(), "Income is: $amountText")
        add(ActivitySearchField.INFLOW_AT_LEAST, amount.toString(), "Income at least: $amountText")
        add(ActivitySearchField.INFLOW_AT_MOST, amount.toString(), "Income at most: $amountText")
    }
}

private fun ActivitySearchSuggestionList.addStatus(
    value: String,
    records: List<ActivitySearchRecord>,
) {
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
}
