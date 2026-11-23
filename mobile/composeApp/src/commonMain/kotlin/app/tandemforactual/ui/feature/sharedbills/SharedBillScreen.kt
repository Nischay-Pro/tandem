@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.ShareState
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBillMember
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.format.formatShortMonth
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.launch

/** One shared bill: each person's last six months, their streak, and the rules that tag their payments. */
@Composable
internal fun SharedBillScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    val bill = mobileSession.planning.sharedBills.firstOrNull { it.id == state.selectedSharedBillId }
    if (bill == null) {
        PageColumn {
            SurfaceCard {
                EmptyState("Shared bill unavailable", "It may have been removed on another device.", Modifier.padding(TandemSpacing.large))
            }
        }
        return
    }
    val payments = rememberSharedBillPayments(state, mobileSession, bill).orEmpty()
    val today = platformTodayIsoDate()
    val months = recentMonths(today)
    var message by remember { mutableStateOf<Pair<String, InlineMessageKind>?>(null) }
    var removing by remember { mutableStateOf<TaggedPayment?>(null) }
    val scope = rememberCoroutineScope()
    PageColumn(verticalSpacing = TandemSpacing.large) {
        message?.let { (text, kind) -> InlineMessage(text, kind, onDismiss = { message = null }) }
        SectionCard(bill.name, "Last ${months.size} months · tap someone to add a payment by hand") {
            bill.members.forEachIndexed { index, member ->
                if (index > 0) DividerLine()
                MemberHistory(bill, member, payments, months, today, { state.formatCurrency(it) }) {
                    state.selectedSharedBillMemberTag = member.tag
                    navigation.open(AppRoute.SHARED_BILL_PAYMENT)
                }
            }
        }
        SuggestedPayments(state, mobileSession, bill, liveTransactions)
        RecentPayments(payments.take(8), { state.formatCurrency(it) }) { if (!state.readOnlyMode) removing = it }
        TaggingRulesCard(state, mobileSession, bill, { message = it }) { navigation.open(AppRoute.SHARED_BILL_EDIT) }
    }
    removing?.let { payment ->
        DestructiveConfirmationDialog(
            title = "Remove from ${bill.name}?",
            message = "Its tags come off, so it no longer counts toward anyone’s share. The payment stays in your account.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            enabled = !mobileSession.isSavingTransaction,
            onDismissRequest = { removing = null },
        ) {
            scope.launch {
                val removed =
                    mobileSession.untagPayment(state, bill, payment, localPaymentId(payment, liveTransactions))
                if (!removed) {
                    message = (mobileSession.transactionWriteMessage ?: "Tandem couldn’t update this payment.") to InlineMessageKind.ERROR
                }
                removing = null
            }
        }
    }
}

@Composable
private fun MemberHistory(
    bill: SharedBill,
    member: SharedBillMember,
    payments: List<TaggedPayment>,
    months: List<String>,
    today: String,
    money: (Long) -> String,
    onClick: () -> Unit,
) {
    val streak = SharedBills.streak(bill, member, payments, months, today)
    val current = SharedBills.month(bill, payments, months.last(), today).first { it.member == member }
    val ahead = SharedBills.paidThrough(bill, member, payments, today)?.takeIf { it > today.take(7) }
    val summary =
        listOfNotNull(
            when {
                ahead != null -> "Paid through ${formatShortMonth(ahead.take(4).toInt(), ahead.drop(5).toInt())}"
                streak > 1 -> "Paid in full $streak months in a row"
                else -> "Share ${money(member.shareMinor)} a month"
            },
            member.everyMonths.takeIf { it > 1 }?.let { "pays every $it months" },
        ).joinToString(" · ")
    ListRow(
        title = member.name,
        leading = RowLeading.None,
        onClick = onClick,
        trailingContent = { ShareStateBadge(current, money) },
        subtitleContent = {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Text(
                    summary,
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    months.forEach { month ->
                        val state = SharedBills.month(bill, payments, month, today).first { it.member == member }.state
                        MonthDot(month, state)
                    }
                }
            }
        },
    )
}

@Composable
private fun MonthDot(
    month: String,
    state: ShareState,
) {
    val label = formatShortMonth(month.take(4).toInt(), month.drop(5).toInt()).substringBefore(' ')
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.semantics(
                mergeDescendants = true,
            ) { contentDescription = "$label ${state.name.lowercase().replace('_', ' ')}" },
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(shareStateColor(state)))
        Text(label, color = tandemPalette.muted, fontSize = TandemTypography.micro)
    }
}

@Composable
private fun RecentPayments(
    payments: List<TaggedPayment>,
    money: (Long) -> String,
    onRemove: (TaggedPayment) -> Unit,
) {
    SectionCard("Recent payments", if (payments.isEmpty()) "None tagged yet" else "Tap one that isn’t a share to remove it") {
        payments.forEachIndexed { index, payment ->
            if (index > 0) DividerLine()
            ListRow(
                leading = RowLeading.None,
                title = money(payment.amountMinor),
                subtitle = "${formatLiveDate(payment.date)} · ${payment.notes.trim()}",
                onClick = { onRemove(payment) },
            )
        }
    }
}
