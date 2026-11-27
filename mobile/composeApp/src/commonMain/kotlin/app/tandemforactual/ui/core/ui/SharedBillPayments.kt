package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

private const val EARLIEST_MONTH = "2000-01"

/** The last [HISTORY_MONTHS] months, oldest first, as yyyy-MM. */
internal fun recentMonths(today: String = platformTodayIsoDate()): List<String> =
    (HISTORY_MONTHS - 1 downTo 0).map { back -> SpendingInsights.shiftMonth(today.take(7), -back) }

/** Tags changed on this device that the budget file hasn't caught up with yet. */
internal object PendingBillTags {
    val added = mutableStateListOf<TaggedPayment>()
    val removed = mutableStateListOf<String>()

    fun reconcile(loaded: List<TaggedPayment>) {
        removed.removeAll { id -> loaded.none { it.id == id } }
        added.removeAll { pending -> loaded.any { it.sameAs(pending) } }
    }
}

private fun TaggedPayment.sameAs(other: TaggedPayment) =
    date == other.date && amountMinor == other.amountMinor && notes.trim() == other.notes.trim()

/**
 * Payments tagged for [bill] from the month before it started, so an early first payment counts, or all of them for a bill
 * saved without a start month, whose credits count from the earliest one; null while loading or when the budget can't be read.
 */
@Composable
internal fun rememberSharedBillPayments(
    state: AppState,
    session: TandemMobileSession,
    bill: SharedBill,
): List<TaggedPayment>? {
    var stored by remember(state.selectedBudgetId, bill.tag) { mutableStateOf<List<TaggedPayment>?>(null) }
    val from = listOf(bill.startMonth?.let { SpendingInsights.shiftMonth(it, -1) } ?: EARLIEST_MONTH, recentMonths().first()).min()
    LaunchedEffect(state.selectedBudgetId, bill.tag, from, session.localSyncState?.lastSyncedAtEpochMs) {
        val loaded =
            session.insights.loadTaggedPayments(
                state.selectedBudgetId,
                bill.tag,
                "$from-01",
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        loaded?.let(PendingBillTags::reconcile)
        stored = loaded
    }
    val loaded = stored ?: return null
    val added = PendingBillTags.added.filter { pending -> pending.hasTag(bill.tag) && loaded.none { it.sameAs(pending) } }
    return (loaded.filterNot { it.id in PendingBillTags.removed } + added).sortedByDescending { it.date }
}
