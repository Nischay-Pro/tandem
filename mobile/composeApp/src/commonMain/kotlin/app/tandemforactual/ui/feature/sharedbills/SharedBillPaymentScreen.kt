@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.PaymentCandidate
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBillMember
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.launch

private enum class PaymentSource { ACCOUNTS, CASH }

/** Tags a payment for one person by hand: one already in an account, or cash and other money that never reached a bank. */
@Composable
internal fun SharedBillPaymentScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    liveAccounts: List<LiveAccountSummary>,
) {
    val bill = mobileSession.planning.sharedBills.firstOrNull { it.id == state.selectedSharedBillId }
    val member = bill?.members?.firstOrNull { it.tag == state.selectedSharedBillMemberTag }
    if (bill == null || member == null) {
        PageColumn {
            SurfaceCard {
                EmptyState("Person unavailable", "They may have been removed from the bill.", Modifier.padding(TandemSpacing.large))
            }
        }
        return
    }
    var source by rememberSaveable { mutableStateOf(PaymentSource.ACCOUNTS) }
    val error = mobileSession.transactionWriteMessage
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Text(
            "Use this when ${member.name}’s payment wasn’t tagged by a rule, or they paid in cash.",
            color = tandemPalette.muted,
            fontSize = TandemTypography.compactBody,
        )
        FilterPills(listOf(PaymentSource.ACCOUNTS to "In an account", PaymentSource.CASH to "Cash or other"), source, { source = it })
        error?.let { InlineMessage(it, InlineMessageKind.ERROR, onDismiss = { mobileSession.reportTransactionWriteMessage(null) }) }
        when (source) {
            PaymentSource.ACCOUNTS -> PickPayment(state, mobileSession, navigation, bill, member, liveTransactions)
            PaymentSource.CASH -> RecordPayment(state, mobileSession, navigation, bill, member, liveAccounts)
        }
    }
}

@Composable
private fun PickPayment(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    bill: SharedBill,
    member: SharedBillMember,
    liveTransactions: List<TransactionItem>,
) {
    val scope = rememberCoroutineScope()
    val untagged =
        paymentCandidates(liveTransactions).filterNot { TaggedPayment(it.id, it.date, it.amountMinor, it.notes).hasTag(bill.tag) }
    val (likely, others) = untagged.partition { SharedBills.monthsCovered(member, it.amountMinor) != null }
    val rows = likely + others
    SectionCard("Money in, last two months", "Tap the one from ${member.name}") {
        if (rows.isEmpty()) {
            EmptyState("Nothing to tag", "No untagged money in over the last two months.", Modifier.padding(TandemSpacing.large))
        }
        rows.forEachIndexed { index, payment ->
            if (index > 0) DividerLine()
            ListRow(
                title = payment.payee.ifBlank { "No payee" },
                subtitle = candidateSubtitle(payment, member),
                trailing = state.formatCurrency(payment.amountMinor),
                leading = RowLeading.None,
                onClick = {
                    if (!state.readOnlyMode) {
                        scope.launch {
                            if (mobileSession.tagPayment(state, bill, member, payment)) navigation.back()
                        }
                    }
                },
            )
        }
    }
}

private fun candidateSubtitle(
    payment: PaymentCandidate,
    member: SharedBillMember,
): String {
    val months = SharedBills.monthsCovered(member, payment.amountMinor)
    val fit =
        when (months) {
            null -> null
            1 -> "matches their share"
            else -> "$months months of their share"
        }
    return listOfNotNull(formatLiveDate(payment.date), fit, payment.notes.trim().ifBlank { null }).joinToString(" · ")
}

@Composable
private fun RecordPayment(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    bill: SharedBill,
    member: SharedBillMember,
    liveAccounts: List<LiveAccountSummary>,
) {
    val settings = state.budgetDisplaySettings
    val accounts = liveAccounts.filter { !it.closed && !it.offBudget }
    var amount by remember(member) { mutableStateOf(formatAmount(member.shareMinor, settings)) }
    var date by remember { mutableStateOf(platformTodayIsoDate()) }
    var how by rememberSaveable { mutableStateOf("Cash") }
    var accountId by remember {
        mutableStateOf(accounts.firstOrNull { it.id == state.lastTransactionAccountId }?.id ?: accounts.firstOrNull()?.id.orEmpty())
    }
    val scope = rememberCoroutineScope()
    SectionCard("Payment", "Added to the account you keep it in, tagged for ${member.name}") {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            FilterPills(listOf("Cash", "Check", "Other").map { it to it }, how, { how = it })
            TextFieldCard(
                "Amount",
                amount,
                { amount = it },
                TextFieldOptions(
                    placeholder = "0",
                    currencySymbol = settings?.currencySymbol ?: "$",
                    currencySymbolAfter = settings?.currencySymbolPosition == "after",
                    amountFractionDigits = settings?.currencyFractionDigits ?: 2,
                ),
            )
            TextFieldCard("Date", date, { date = it.take(10) }, TextFieldOptions(placeholder = "YYYY-MM-DD"))
            if (accounts.isEmpty()) {
                Text("Add an on-budget account first.", color = tandemPalette.muted, fontSize = TandemTypography.label)
            } else {
                Text("Account", color = tandemPalette.muted, fontSize = TandemTypography.label)
                FilterPills(accounts.map { it.id to it.name }, accountId, { accountId = it })
            }
        }
    }
    TandemActionButton(
        "Add payment",
        TandemActionButtonTone.PRIMARY,
        Modifier.fillMaxWidth(),
        enabled = accountId.isNotBlank() && amount.isNotBlank() && !state.readOnlyMode && !mobileSession.isSavingTransaction,
    ) {
        scope.launch {
            if (mobileSession.recordPayment(state, bill, member, accountId, amount, date, how)) navigation.back()
        }
    }
}
