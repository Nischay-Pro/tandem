@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards

/** Picks the unlinked budget account a bank account should sync into. */
@Composable
internal fun LinkBankAccountDialog(
    target: Pair<BankProvider, BankProviderAccount>?,
    liveAccounts: List<LiveAccountSummary>,
    linked: List<LinkedAccount>,
    onDismiss: () -> Unit,
    onPick: (LiveAccountSummary) -> Unit,
) {
    val palette = tandemPalette
    TandemChoiceDialog(visible = target != null, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader("Link ${target?.second?.name.orEmpty()}", onClose = onDismiss, leadingIcon = Lucide.Landmark)
                Text("Choose the budget account it should sync into.", color = palette.muted, fontSize = TandemTypography.compactBody)
                liveAccounts.filter { account -> !account.closed && linked.none { it.id == account.id } }.forEach { account ->
                    ListRow(
                        account.name,
                        if (account.offBudget) "Off budget" else "On budget",
                        leading =
                            RowLeading.Icon(
                                Lucide.WalletCards,
                            ),
                        onClick = { onPick(account) },
                    )
                }
            }
        }
    }
}
