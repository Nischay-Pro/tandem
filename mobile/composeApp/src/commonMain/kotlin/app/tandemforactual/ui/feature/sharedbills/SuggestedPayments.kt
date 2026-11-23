@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.PaymentSuggestion
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBillMember
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import kotlinx.coroutines.launch

private const val MAX_SUGGESTIONS = 5

/** Untagged money in that matches someone's share, offered for a one-tap tag; nothing is tagged without asking. */
@Composable
internal fun SuggestedPayments(
    state: AppState,
    mobileSession: TandemMobileSession,
    bill: SharedBill,
    liveTransactions: List<TransactionItem>,
) {
    val dismissed = remember(bill.id) { mutableStateListOf<String>() }
    val suggestions =
        SharedBills
            .suggestions(bill, paymentCandidates(liveTransactions))
            .filterNot { it.candidate.id in dismissed }
            .take(MAX_SUGGESTIONS)
    if (suggestions.isEmpty() || state.readOnlyMode) return
    val scope = rememberCoroutineScope()
    SectionCard("Might be a share", "Money in that matches what someone owes") {
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) DividerLine()
            SuggestionRow(state, suggestion, { dismissed += suggestion.candidate.id }) { member ->
                scope.launch {
                    mobileSession.tagPayment(state, bill, member, suggestion.candidate)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    state: AppState,
    suggestion: PaymentSuggestion,
    onDismiss: () -> Unit,
    onTag: (SharedBillMember) -> Unit,
) {
    val payment = suggestion.candidate
    ListRow(
        title = payment.payee.ifBlank { "No payee" },
        subtitle = formatLiveDate(payment.date),
        trailing = state.formatCurrency(payment.amountMinor),
        leading = RowLeading.None,
        options = ListRowOptions(showChevron = false),
    )
    Row(
        Modifier.padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = TandemSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        suggestion.members.take(2).forEach { member ->
            TandemActionButton("${member.name}’s", TandemActionButtonTone.PRIMARY, Modifier.weight(1f)) { onTag(member) }
        }
        TandemActionButton("Not a share", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), onClick = onDismiss)
    }
}
