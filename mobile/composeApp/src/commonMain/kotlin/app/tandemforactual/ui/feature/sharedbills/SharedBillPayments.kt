package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.PaymentCandidate
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem

internal const val HISTORY_MONTHS = 6
private const val CANDIDATE_MONTHS = 2

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

/** Payments tagged for [bill] since it started, or over the recent months; null while loading or when the budget can't be read. */
@Composable
internal fun rememberSharedBillPayments(
    state: AppState,
    session: TandemMobileSession,
    bill: SharedBill,
): List<TaggedPayment>? {
    var stored by remember(state.selectedBudgetId, bill.tag) { mutableStateOf<List<TaggedPayment>?>(null) }
    val from = listOfNotNull(bill.startMonth, recentMonths().first()).min()
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

/** Recent money in that isn't a transfer, newest first, as candidates for someone's share. */
internal fun paymentCandidates(
    live: List<TransactionItem>,
    today: String = platformTodayIsoDate(),
): List<PaymentCandidate> {
    val since = SpendingInsights.shiftMonth(today.take(7), -CANDIDATE_MONTHS) + "-01"
    return live
        .filter { it.recordId != null && it.amountMinorUnits > 0 && !it.isTransfer && !it.isSplitParent && it.dateIso >= since }
        .filterNot { it.payee.equals("Starting Balance", ignoreCase = true) }
        .sortedByDescending { it.dateIso }
        .map { PaymentCandidate(it.recordId!!, it.dateIso, it.amountMinorUnits, it.payee, it.notes) }
}

/** The device's id for a payment read from the budget file, which can differ for transactions Tandem added. */
internal fun localPaymentId(
    payment: TaggedPayment,
    live: List<TransactionItem>,
): String =
    live.firstOrNull { it.recordId == payment.id }?.recordId
        ?: live
            .firstOrNull {
                it.dateIso == payment.date && it.amountMinorUnits == payment.amountMinor && it.notes.trim() == payment.notes.trim()
            }?.recordId
        ?: payment.id
