@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.importing.StatementCandidate
import app.tandemforactual.ui.core.importing.StatementReview
import app.tandemforactual.ui.core.importing.StatementRowAction
import app.tandemforactual.ui.core.importing.StatementRowReview
import app.tandemforactual.ui.core.importing.StatementRowStatus
import app.tandemforactual.ui.core.importing.explanation
import app.tandemforactual.ui.core.importing.pairedRow
import app.tandemforactual.ui.core.importing.rowLabel
import app.tandemforactual.ui.core.importing.statementActionLabel
import app.tandemforactual.ui.core.importing.statementCount
import app.tandemforactual.ui.core.importing.statementDayDistance
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

private enum class StatementReviewFilter {
    REVIEW,
    NEW,
    MATCHED,
    SKIPPED,
    ALL,
}

private const val STATEMENT_ROWS_PER_PAGE = 50

private fun StatementRowReview.needsDecision(): Boolean =
    status == StatementRowStatus.CHOOSE ||
        status == StatementRowStatus.GROUP ||
        (status == StatementRowStatus.DUPLICATE && !provenDuplicate)

private fun StatementReviewFilter.includes(
    row: StatementRowReview,
    action: StatementRowAction,
): Boolean =
    when (this) {
        StatementReviewFilter.REVIEW -> row.needsDecision()
        StatementReviewFilter.NEW -> action == StatementRowAction.Create
        StatementReviewFilter.MATCHED -> action is StatementRowAction.Match
        StatementReviewFilter.SKIPPED -> action == StatementRowAction.Duplicate
        StatementReviewFilter.ALL -> true
    }

/** Banners for a repeated statement or reversed signs, filters, and each row with its choices. */
@Composable
internal fun StatementReviewSection(
    review: StatementReview,
    actions: Map<Int, StatementRowAction>,
    flipSigns: Boolean,
    settings: BudgetDisplaySettings?,
    enabled: Boolean,
    callbacks: StatementImportCallbacks,
) {
    StatementReviewNotices(review, flipSigns, enabled, callbacks.onFlipSigns)
    val toReview = review.rows.count { it.needsDecision() }
    var filter by remember(review) {
        mutableStateOf(if (toReview > 0) StatementReviewFilter.REVIEW else StatementReviewFilter.ALL)
    }
    var limit by remember(review, filter) { mutableStateOf(STATEMENT_ROWS_PER_PAGE) }
    var expanded by remember(review) { mutableStateOf<Int?>(null) }

    fun count(filter: StatementReviewFilter) = review.rows.count { filter.includes(it, actions.getValue(it.index)) }
    FilterPills(
        options =
            listOfNotNull(
                (StatementReviewFilter.REVIEW to "To review ${count(StatementReviewFilter.REVIEW)}").takeIf { toReview > 0 },
                StatementReviewFilter.ALL to "All ${review.rows.size}",
                StatementReviewFilter.MATCHED to "Matched ${count(StatementReviewFilter.MATCHED)}",
                StatementReviewFilter.NEW to "New ${count(StatementReviewFilter.NEW)}",
                StatementReviewFilter.SKIPPED to "Skipped ${count(StatementReviewFilter.SKIPPED)}",
            ),
        selected = filter,
        onSelect = { filter = it },
    )
    val shown = review.rows.filter { filter.includes(it, actions.getValue(it.index)) }
    if (shown.isEmpty()) {
        Text("No rows here.", color = tandemPalette.muted, fontSize = TandemTypography.compactBody)
    }
    shown.take(limit).forEach { row ->
        StatementReviewRow(
            review = review,
            row = row,
            actions = actions,
            expanded = expanded == row.index,
            settings = settings,
            enabled = enabled,
            onToggle = { expanded = if (expanded == row.index) null else row.index },
            onAction = { action -> callbacks.onAction(row.index, action) },
        )
    }
    if (shown.size > limit) {
        TandemActionButton(
            "Show ${minOf(STATEMENT_ROWS_PER_PAGE, shown.size - limit)} more",
            TandemActionButtonTone.NEUTRAL,
            modifier = Modifier.fillMaxWidth(),
            onClick = { limit += STATEMENT_ROWS_PER_PAGE },
        )
    }
}

@Composable
private fun StatementReviewNotices(
    review: StatementReview,
    flipSigns: Boolean,
    enabled: Boolean,
    onFlipSigns: () -> Unit,
) {
    when {
        review.repeatedStatement ->
            InlineMessage("You’ve imported this statement before. Rows already in Actual are skipped.", InlineMessageKind.INFO)
        review.alreadyImported > 0 ->
            InlineMessage(
                "${statementCount(review.alreadyImported, "row")} already in Actual will be skipped.",
                InlineMessageKind.INFO,
            )
    }
    if (flipSigns) {
        InlineMessage(
            "Signs reversed, so spending on this statement is money out.",
            InlineMessageKind.INFO,
            actionLabel = if (enabled) "Undo" else null,
            onAction = onFlipSigns,
        )
    } else if (review.spendingLooksPositive) {
        InlineMessage(
            "This statement seems to show spending as positive amounts, so its rows don’t match this account.",
            InlineMessageKind.WARNING,
            actionLabel = if (enabled) "Reverse signs" else null,
            onAction = onFlipSigns,
        )
    }
}

@Composable
private fun statusColor(action: StatementRowAction): Color =
    when (action) {
        is StatementRowAction.Match -> tandemPalette.positive
        StatementRowAction.Create -> tandemPalette.accent
        StatementRowAction.Duplicate -> tandemPalette.muted
        StatementRowAction.Later -> tandemPalette.warning
    }

@Composable
private fun StatementReviewRow(
    review: StatementReview,
    row: StatementRowReview,
    actions: Map<Int, StatementRowAction>,
    expanded: Boolean,
    settings: BudgetDisplaySettings?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onAction: (StatementRowAction) -> Unit,
) {
    val action = actions.getValue(row.index)
    Column {
        ListRow(
            title = row.row.payeeName,
            trailing = formatCurrency(row.row.amountMinor, settings),
            leading = RowLeading.Payee(row.row.payeeName),
            options = ListRowOptions(showChevron = false, selected = expanded, animateChanges = true),
            onClick = onToggle,
            subtitleContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Text(
                        "${displayTransactionDate(row.row.date)} · ${review.rowLabel(row.index)}",
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.caption,
                    )
                    Badge(statementActionLabel(row, action), statusColor(action))
                }
            },
        )
        AnimatedReveal(expanded) {
            StatementRowChoices(review, row, actions, settings, enabled, onAction)
        }
    }
}

@Composable
private fun StatementRowChoices(
    review: StatementReview,
    row: StatementRowReview,
    actions: Map<Int, StatementRowAction>,
    settings: BudgetDisplaySettings?,
    enabled: Boolean,
    onAction: (StatementRowAction) -> Unit,
) {
    val action = actions.getValue(row.index)
    Column(
        Modifier.fillMaxWidth().padding(bottom = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Text(review.explanation(row, settings), color = tandemPalette.muted, fontSize = TandemTypography.compactBody)
        row.existing?.let { existing ->
            Text(
                "In Actual: ${existing.payeeName ?: existing.importedPayee ?: "No payee"} on ${displayTransactionDate(existing.date)}",
                color = tandemPalette.text,
                fontSize = TandemTypography.compactBody,
            )
        }
        if (row.candidates.isNotEmpty()) {
            Text("Transactions in Actual", color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            row.candidates.forEach { candidate ->
                ChoiceRow(
                    title = "${candidateName(candidate)} · ${formatCurrency(candidate.transaction.amountMinor, settings)}",
                    subtitle = candidateDetail(review, candidate, actions.pairedRow(candidate.transaction.id, row.index)),
                    selected = action == StatementRowAction.Match(candidate.transaction.id),
                    onClick = { if (enabled) onAction(StatementRowAction.Match(candidate.transaction.id)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            listOf(
                StatementRowAction.Create to "New",
                StatementRowAction.Duplicate to if (row.status == StatementRowStatus.IMPORTED) "Skip" else "Duplicate",
                StatementRowAction.Later to "Later",
            ).forEach { (choice, label) ->
                TandemActionButton(
                    label,
                    if (action == choice) TandemActionButtonTone.PRIMARY else TandemActionButtonTone.NEUTRAL,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = { onAction(choice) },
                )
            }
        }
    }
}

private fun candidateName(candidate: StatementCandidate): String =
    candidate.transaction.payeeName?.takeIf(String::isNotBlank)
        ?: candidate.transaction.importedPayee?.takeIf(String::isNotBlank)
        ?: "No payee"

private fun candidateDetail(
    review: StatementReview,
    candidate: StatementCandidate,
    pairedRow: Int?,
): String =
    buildList {
        add("${displayTransactionDate(candidate.transaction.date)}, ${statementDayDistance(candidate.days)}")
        if (candidate.sharedWords > 0) add("similar name")
        if (candidate.transaction.reconciled) add("reconciled")
        pairedRow?.let { add("paired with ${review.rowLabel(it)}") }
    }.joinToString(" · ")
