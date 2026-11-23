@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import app.tandemforactual.data.planning.ShareState
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.ui.rememberSharedBillPayments
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import org.jetbrains.compose.resources.stringResource

/** Each shared bill with who still owes this month, opening the bill on tap. */
@Composable
internal fun SharedBillsHomeCard(
    module: HomeModule,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val bills = mobileSession.planning.sharedBills.sortedBy { it.name.lowercase() }
    SectionCard(
        stringResource(module.title()),
        stringResource(module.detail()),
        "Open",
        onClick = { navigation.open(AppRoute.SHARED_BILLS) },
    ) {
        bills.forEachIndexed { index, bill ->
            if (index > 0) DividerLine()
            key(bill.id) {
                SharedBillHomeRow(state, mobileSession, bill) {
                    state.selectedSharedBillId = bill.id
                    navigation.open(AppRoute.SHARED_BILL)
                }
            }
        }
    }
}

@Composable
private fun SharedBillHomeRow(
    state: AppState,
    mobileSession: TandemMobileSession,
    bill: SharedBill,
    onOpen: () -> Unit,
) {
    val payments = rememberSharedBillPayments(state, mobileSession, bill)
    val today = platformTodayIsoDate()
    val month = payments?.let { SharedBills.month(bill, it, today.take(7), today) }
    val waiting = month?.filter { it.state == ShareState.MISSING || it.state == ShareState.PARTLY_PAID }.orEmpty()
    ListRow(
        bill.name,
        when {
            month == null -> "Checking payments…"
            month.all { it.state == ShareState.PAID } -> "Everyone has paid"
            waiting.isEmpty() -> "Not due yet"
            else -> "Waiting on ${waiting.joinToString(", ") { it.member.name }}"
        },
        month?.let { "${it.count { m -> m.state == ShareState.PAID }} of ${it.size} paid" },
        leading = RowLeading.None,
        onClick = onOpen,
    )
}
