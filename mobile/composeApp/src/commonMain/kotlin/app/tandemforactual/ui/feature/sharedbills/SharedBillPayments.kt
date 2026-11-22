package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState

internal const val HISTORY_MONTHS = 6

/** The last [HISTORY_MONTHS] months, oldest first, as yyyy-MM. */
internal fun recentMonths(today: String = platformTodayIsoDate()): List<String> =
    (HISTORY_MONTHS - 1 downTo 0).map { back -> SpendingInsights.shiftMonth(today.take(7), -back) }

/** Payments tagged for [bill] over the recent months; null while loading or when the budget can't be read. */
@Composable
internal fun rememberSharedBillPayments(
    state: AppState,
    session: TandemMobileSession,
    bill: SharedBill,
): List<TaggedPayment>? {
    var payments by remember(state.selectedBudgetId, bill.tag) { mutableStateOf<List<TaggedPayment>?>(null) }
    LaunchedEffect(state.selectedBudgetId, bill.tag, session.localSyncState?.lastSyncedAtEpochMs) {
        payments =
            session.insights.loadTaggedPayments(
                state.selectedBudgetId,
                bill.tag,
                "${recentMonths().first()}-01",
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
    }
    return payments
}
