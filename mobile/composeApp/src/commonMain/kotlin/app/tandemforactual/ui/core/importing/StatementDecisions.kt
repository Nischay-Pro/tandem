package app.tandemforactual.ui.core.importing

import app.tandemforactual.api.client.StatementImportItem

internal sealed interface StatementRowAction {
    data class Match(
        val transactionId: String,
    ) : StatementRowAction

    data object Create : StatementRowAction

    data object Duplicate : StatementRowAction

    data object Later : StatementRowAction
}

internal fun StatementRowReview.defaultAction(): StatementRowAction =
    when (status) {
        StatementRowStatus.MATCHED -> StatementRowAction.Match(candidates.single().transaction.id)
        StatementRowStatus.NEW -> StatementRowAction.Create
        StatementRowStatus.IMPORTED -> StatementRowAction.Duplicate
        StatementRowStatus.DUPLICATE -> if (provenDuplicate) StatementRowAction.Duplicate else StatementRowAction.Later
        StatementRowStatus.CHOOSE, StatementRowStatus.GROUP -> StatementRowAction.Later
    }

internal fun StatementReview.defaultActions(): Map<Int, StatementRowAction> = rows.associate { it.index to it.defaultAction() }

/** Sets one row's action; a transaction pairs with one row, so any other row that had it goes back to later. */
internal fun Map<Int, StatementRowAction>.choose(
    index: Int,
    action: StatementRowAction,
): Map<Int, StatementRowAction> {
    val freed = if (action is StatementRowAction.Match) filter { (other, current) -> other != index && current == action }.keys else emptySet()
    return this + (index to action) + freed.associateWith { StatementRowAction.Later }
}

/** The row another row has already paired with [transactionId], if any. */
internal fun Map<Int, StatementRowAction>.pairedRow(
    transactionId: String,
    except: Int,
): Int? = entries.firstOrNull { (index, action) -> index != except && action == StatementRowAction.Match(transactionId) }?.key

internal data class StatementApplySummary(
    val matched: Int,
    val created: Int,
    val skipped: Int,
    val later: Int,
) {
    val changes: Int get() = matched + created
}

internal fun StatementReview.summary(actions: Map<Int, StatementRowAction>): StatementApplySummary {
    val chosen = rows.map { actions[it.index] ?: it.defaultAction() }
    return StatementApplySummary(
        matched = chosen.count { it is StatementRowAction.Match },
        created = chosen.count { it == StatementRowAction.Create },
        skipped = chosen.count { it == StatementRowAction.Duplicate },
        later = chosen.count { it == StatementRowAction.Later },
    )
}

/** What Apply sends: pairs and new rows only. Duplicates and rows left for later write nothing. */
internal fun StatementReview.applyItems(actions: Map<Int, StatementRowAction>): List<StatementImportItem> =
    rows.mapNotNull { review ->
        val row = review.row
        when (val action = actions[review.index] ?: review.defaultAction()) {
            is StatementRowAction.Match -> StatementImportItem(row.date, row.amountMinor, row.payeeName, row.notes, row.importedId, action.transactionId)
            StatementRowAction.Create -> StatementImportItem(row.date, row.amountMinor, row.payeeName, row.notes, row.importedId)
            StatementRowAction.Duplicate, StatementRowAction.Later -> null
        }
    }
