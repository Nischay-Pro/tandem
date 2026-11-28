package app.tandemforactual.ui.core.importing

import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.format.formatCurrency

internal fun StatementReview.rowLabel(index: Int): String {
    val row = rows.getOrNull(index)?.row ?: return "row ${index + 1}"
    return "row ${if (row.rowNumber > 0) row.rowNumber else index + 1}"
}

internal fun statementActionLabel(
    review: StatementRowReview,
    action: StatementRowAction,
): String =
    when (action) {
        is StatementRowAction.Match -> "Matched"
        StatementRowAction.Create -> "New"
        StatementRowAction.Duplicate -> if (review.status == StatementRowStatus.IMPORTED) "Already in Actual" else "Duplicate"
        StatementRowAction.Later -> "Later"
    }

internal fun statementDayDistance(days: Int): String =
    when {
        days == 0 -> "same day"
        days == 1 -> "1 day later"
        days == -1 -> "1 day earlier"
        days > 0 -> "$days days later"
        else -> "${-days} days earlier"
    }

/** Why a row has the status it has, in words the review shows under it. */
internal fun StatementReview.explanation(
    review: StatementRowReview,
    settings: BudgetDisplaySettings?,
): String =
    when (review.status) {
        StatementRowStatus.MATCHED -> {
            val candidate = review.candidates.single()
            "Same amount in Actual, ${statementDayDistance(candidate.days)}."
        }

        StatementRowStatus.CHOOSE -> {
            "${review.candidates.size} transactions in Actual have this amount. Choose the right one."
        }

        StatementRowStatus.GROUP -> {
            "Several rows from this merchant on this day have the same amount. Pair each one with its transaction."
        }

        StatementRowStatus.IMPORTED -> {
            "Imported before, so it’s skipped."
        }

        StatementRowStatus.DUPLICATE -> {
            duplicateExplanation(review)
        }

        StatementRowStatus.NEW -> {
            review.reason?.let { noMatchExplanation(it, settings) } ?: "Not in Actual yet."
        }
    }

private fun StatementReview.duplicateExplanation(review: StatementRowReview): String {
    val earlier = review.duplicateOf?.let(::rowLabel) ?: "an earlier row"
    return if (review.provenDuplicate) {
        "Has the same bank reference as $earlier."
    } else {
        "Looks the same as $earlier. Import it only if you made both."
    }
}

internal fun noMatchExplanation(
    reason: StatementNoMatchReason,
    settings: BudgetDisplaySettings?,
): String =
    when (reason) {
        is StatementNoMatchReason.OppositeSign -> {
            "Actual has ${formatCurrency(reason.transaction.amountMinor, settings)} on " +
                "${displayTransactionDate(reason.transaction.date)}. This statement may show spending as positive amounts."
        }

        is StatementNoMatchReason.TooFarApart -> {
            "The closest ${formatCurrency(reason.transaction.amountMinor, settings)} in Actual is " +
                "${statementDayDistance(reason.days)}, more than $MATCH_WINDOW_DAYS days away."
        }

        is StatementNoMatchReason.AlreadyImported -> {
            "The ${formatCurrency(reason.transaction.amountMinor, settings)} on " +
                "${displayTransactionDate(reason.transaction.date)} already came from another statement row."
        }

        StatementNoMatchReason.NothingNearby -> {
            "This account has no transactions within $MATCH_WINDOW_DAYS days of this date."
        }

        StatementNoMatchReason.NoSameAmount -> {
            "Nothing in Actual has this exact amount within $MATCH_WINDOW_DAYS days."
        }
    }

internal fun StatementApplySummary.description(): String =
    buildList {
        if (matched > 0) add("$matched matched")
        if (created > 0) add("$created new")
        if (skipped > 0) add("$skipped skipped")
        if (later > 0) add("$later for later")
    }.joinToString(" · ").ifBlank { "Nothing to apply" }

internal fun statementApplyLabel(
    summary: StatementApplySummary,
    busy: Boolean,
): String =
    when {
        busy -> "Applying…"
        summary.changes == 0 -> "Nothing to apply"
        else -> "Apply ${statementCount(summary.changes, "change")}"
    }
