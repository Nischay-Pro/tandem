package app.tandemforactual.ui.feature.sharedbills

import app.tandemforactual.data.planning.PaymentCandidate
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.state.TransactionItem

private const val CANDIDATE_MONTHS = 2

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
