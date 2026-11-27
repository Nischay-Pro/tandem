@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.ui.accountTypeIcon
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Picks the unlinked budget account a bank account should sync into. */
@Composable
internal fun LinkBankAccountDialog(
    target: Pair<BankProvider, BankProviderAccount>?,
    liveAccounts: List<LiveAccountSummary>,
    linked: List<LinkedAccount>,
    onDismiss: () -> Unit,
    onPick: (LiveAccountSummary) -> Unit,
    onCreate: (offBudget: Boolean) -> Unit,
) {
    val palette = tandemPalette
    TandemChoiceDialog(visible = target != null, onDismissRequest = onDismiss) {
        SheetCard(
            scrollBody = false,
            maxWidth = 480.dp,
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader("Link ${target?.second?.name.orEmpty()}", onClose = onDismiss, leadingIcon = Lucide.Landmark)
            },
        ) {
            Text(
                "Choose the budget account it should sync into, or create one.",
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
            val candidates =
                remember(target, liveAccounts, linked) {
                    val open = liveAccounts.filter { account -> !account.closed && linked.none { it.id == account.id } }
                    target?.let { rankLinkCandidates(it.second, open) }.orEmpty()
                }
            Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                candidates.forEach { candidate ->
                    val account = candidate.account
                    ListRow(
                        account.name,
                        listOfNotNull(
                            "Best match".takeIf { isBestMatch(candidates, candidate) },
                            if (account.offBudget) "Off budget" else "On budget",
                        ).joinToString(" · "),
                        leading = RowLeading.Account(account.name, fallback = accountTypeIcon(account.name, account.kind)),
                        onClick = { onPick(account) },
                    )
                }
            }
            DividerLine()
            NewLinkedAccountRows(target?.second?.name.orEmpty(), onCreate)
        }
    }
}

/** Creates the budget account for a bank or card account that doesn't have one yet; the first sync sets its balance. */
@Composable
private fun NewLinkedAccountRows(
    name: String,
    onCreate: (offBudget: Boolean) -> Unit,
) {
    ListRow(
        "New on-budget account",
        name.ifBlank { null },
        leading = RowLeading.Icon(Lucide.Plus),
        onClick = { onCreate(false) },
    )
    ListRow(
        "New off-budget account",
        "For accounts you track but don’t budget, such as investments",
        leading = RowLeading.Icon(Lucide.Plus),
        onClick = { onCreate(true) },
    )
}
