@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.HealthIssue
import app.tandemforactual.data.actual.HealthIssueKind
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2

/** Findings the checkup can act on, rather than only open. */
internal fun HealthIssueKind.hasActions() = this == HealthIssueKind.UNUSED_PAYEES || this == HealthIssueKind.POSSIBLE_DUPLICATES

/** Rows of a duplicate group: the same date, amount, account and payee. */
internal fun duplicatesOf(
    issue: HealthIssue,
    row: HealthRow,
): List<HealthRow> =
    if (issue.kind == HealthIssueKind.POSSIBLE_DUPLICATES) {
        issue.rows.filter { it.date == row.date && it.amount == row.amount && it.account == row.account && it.name == row.name }
    } else {
        listOf(row)
    }

/**
 * The device's transaction for a checkup row. Rows come from the budget file, where a transaction Tandem added has a
 * different id than on the device, so it falls back to the date, amount, payee and account.
 */
internal fun transactionFor(
    row: HealthRow,
    live: List<TransactionItem>,
): TransactionItem? =
    live.firstOrNull { it.recordId == row.id }
        ?: live.firstOrNull {
            it.recordId != null &&
                it.dateIso == row.date &&
                it.amountMinorUnits == row.amount &&
                it.payee == (row.name ?: it.payee) &&
                it.account == (row.account ?: it.account)
        }

internal val duplicateOrder =
    compareByDescending<HealthRow> { it.date }.thenBy { it.account }.thenBy { it.amount }.thenBy { it.name }

/** What to do with one unused payee or one copy of a possible duplicate: delete it, open it, or mark it as fine. */
@Composable
internal fun CheckupActionDialog(
    target: Pair<HealthIssue, HealthRow>?,
    icon: ImageVector?,
    canOpen: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onDelete: (group: List<HealthRow>) -> Unit,
    onIgnore: (group: List<HealthRow>) -> Unit,
) {
    TandemChoiceDialog(visible = target != null, onDismissRequest = onDismiss) {
        val (issue, row) = target ?: return@TandemChoiceDialog
        val payee = issue.kind == HealthIssueKind.UNUSED_PAYEES
        val group = duplicatesOf(issue, row)
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader(row.name ?: "No payee", onClose = onDismiss, leadingIcon = icon)
                Text(
                    if (payee) {
                        "No transactions or rules use this payee."
                    } else {
                        "${group.size} transactions share this date, amount and payee."
                    },
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                if (!payee && canOpen) {
                    ListRow("Open transaction", leading = RowLeading.Icon(Lucide.ExternalLink), onClick = onOpen)
                }
                ListRow(
                    if (payee) "Delete payee" else "Delete this copy",
                    when {
                        payee -> "Removes it from your payee list"
                        group.size == 2 -> "Keeps the other copy"
                        else -> "Keeps the other ${group.size - 1}"
                    },
                    leading = RowLeading.Icon(Lucide.Trash2),
                    options = ListRowOptions(showChevron = false),
                    onClick = { onDelete(group) },
                )
                ListRow(
                    if (payee) "Ignore" else "Not a duplicate",
                    if (payee) "Stop listing this payee here" else "Stop listing these here",
                    leading = RowLeading.Icon(Lucide.EyeOff),
                    options = ListRowOptions(showChevron = false),
                    onClick = { onIgnore(group) },
                )
            }
        }
    }
}
