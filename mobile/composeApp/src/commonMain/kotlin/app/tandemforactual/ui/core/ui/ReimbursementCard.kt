@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.Reimbursement
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/** The reimbursement row on a purchase's detail screen, with a dialog to say who owes what. */
@Composable
internal fun ReimbursementCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val amount = state.selectedTransactionAmountMinor ?: return
    if (amount >= 0 || state.selectedTransactionIsTransfer) return
    val scope = rememberCoroutineScope()
    val existing = mobileSession.planning.reimbursements.firstOrNull { it.transactionId == state.selectedTransactionRecordId }
    var editing by remember { mutableStateOf(false) }
    SectionCard(title = "") {
        ListRow(
            when {
                existing == null -> "Expect a reimbursement"
                existing.received -> "Reimbursement received"
                else -> "Reimbursement pending"
            },
            existing?.let { listOfNotNull(it.from?.let { from -> "From $from" }, state.formatCurrency(it.amountMinor)).joinToString(" · ") }
                ?: "Track money someone will pay back",
            leading = RowLeading.Icon(if (existing?.received == true) Lucide.CircleCheck else Lucide.HandCoins),
            onClick = { if (existing == null) editing = true else navigation.open(AppRoute.REIMBURSEMENTS) },
        )
    }
    if (editing) {
        var from by remember { mutableStateOf("") }
        var owed by remember { mutableStateOf(formatAmount(-amount, state.budgetDisplaySettings)) }
        TandemChoiceDialog(visible = true, onDismissRequest = { editing = false }) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
                Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    TandemModalHeader("Expect a reimbursement", onClose = { editing = false }, leadingIcon = Lucide.HandCoins)
                    TextFieldCard("Who pays you back", from, { from = it }, TextFieldOptions(placeholder = "e.g. Sam, work"))
                    TextFieldCard("Amount owed", owed, { owed = it }, TextFieldOptions(placeholder = "0.00"))
                    Text(
                        "It stays on this budget’s encrypted planning data, not in Actual.",
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.label,
                    )
                    TandemActionButton("Track it", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                        val minor = state.parseAmountMinorUnits(owed)?.let { kotlin.math.abs(it) } ?: -amount
                        editing = false
                        scope.launch {
                            mobileSession.planning.saveReimbursement(
                                state.selectedBudgetId,
                                Reimbursement(
                                    state.selectedTransactionRecordId,
                                    minor,
                                    from.trim().ifBlank { null },
                                    state.transactionPayee.ifBlank { null },
                                    state.transactionDate.takeIf { it.length == 10 && it[4] == '-' },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
