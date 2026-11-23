@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.ShareState
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Every shared bill with who has paid their share this month. */
@Composable
internal fun SharedBillsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val bills = mobileSession.planning.sharedBills
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (bills.isEmpty()) {
            SurfaceCard {
                EmptyState(
                    "Track who pays you back",
                    "For bills you pay in full, like a family phone plan. Tandem tags each person’s Zelle, Venmo or Cash App " +
                        "payment and shows who has paid each month.",
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                )
            }
        }
        bills.sortedBy { it.name.lowercase() }.forEach { bill ->
            SharedBillSummary(state, mobileSession, bill) {
                state.selectedSharedBillId = bill.id
                navigation.open(AppRoute.SHARED_BILL)
            }
        }
        TandemActionButton("Add shared bill", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Plus) {
            state.selectedSharedBillId = ""
            navigation.open(AppRoute.SHARED_BILL_EDIT)
        }
    }
}

@Composable
private fun SharedBillSummary(
    state: AppState,
    session: TandemMobileSession,
    bill: SharedBill,
    onOpen: () -> Unit,
) {
    val payments = rememberSharedBillPayments(state, session, bill)
    val today = platformTodayIsoDate()
    val month = payments?.let { SharedBills.month(bill, it, today.take(7), today) }
    val paid = month?.count { it.state == ShareState.PAID }
    SectionCard(
        bill.name,
        paid?.let { "$it of ${bill.members.size} paid this month" } ?: "Checking payments…",
        action = "Open",
        onClick = onOpen,
    ) {
        if (month == null) {
            SkeletonRows(count = bill.members.size.coerceIn(1, 4))
        } else {
            month.forEachIndexed { index, entry ->
                if (index > 0) DividerLine()
                ListRow(
                    title = entry.member.name,
                    subtitle = "Share ${state.formatCurrency(entry.member.shareMinor)}",
                    trailingContent = { ShareStateBadge(entry) { state.formatCurrency(it) } },
                    onClick = onOpen,
                )
            }
        }
    }
}
