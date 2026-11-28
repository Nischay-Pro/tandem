@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.SectionCard

/** One closed card statement: what was charged and paid in the cycle, what was owed at closing, and what was paid on it since. */
internal data class CardStatement(
    val closedOn: String,
    val chargesMinor: Long,
    val paymentsMinor: Long,
    val owedMinor: Long,
    val paidSinceMinor: Long,
) {
    val paid: Boolean get() = owedMinor <= 0 || paidSinceMinor >= owedMinor
}

/** The day a statement closing on [closeDay] closes in [monthId], moved to the month's last day when it is shorter. */
internal fun statementCloseDate(
    monthId: String,
    closeDay: Int,
): String? {
    val days = daysInMonth(monthId) ?: return null
    return "$monthId-${closeDay.coerceAtMost(days).toString().padStart(2, '0')}"
}

/**
 * The last [count] statements closed by [today], newest first, from the card's dated amounts (charges negative) and
 * its balance now. A statement's balance is today's balance less everything dated after it closed.
 */
internal fun cardStatements(
    transactions: List<Pair<String, Long>>,
    balanceMinor: Long,
    closeDay: Int,
    today: String,
    count: Int,
): List<CardStatement> {
    val closes =
        generateSequence(today.take(7), ::monthBefore)
            .mapNotNull { statementCloseDate(it, closeDay) }
            .filter { it <= today }
            .take(count + 1)
            .toList()
    return closes.zipWithNext().take(count).mapIndexed { index, (closed, opened) ->
        val nextClose = closes.getOrNull(index - 1) ?: today
        val cycle = transactions.filter { (date, _) -> date > opened && date <= closed }
        val after = transactions.filter { (date, _) -> date > closed }
        val since = transactions.filter { (date, _) -> date > closed && date <= nextClose }
        CardStatement(
            closedOn = closed,
            chargesMinor = -cycle.filter { it.second < 0 }.sumOf { it.second },
            paymentsMinor = cycle.filter { it.second > 0 }.sumOf { it.second },
            owedMinor = -(balanceMinor - after.sumOf { it.second }),
            paidSinceMinor = since.filter { it.second > 0 }.sumOf { it.second },
        )
    }
}

private fun monthBefore(monthId: String): String? {
    val year = monthId.substringBefore('-').toIntOrNull() ?: return null
    val month = monthId.substringAfter('-', "").toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return if (month == 1) "${year - 1}-12" else "$year-${(month - 1).toString().padStart(2, '0')}"
}

/** A credit card's recent statements, shown once its closing day is known. */
@Composable
internal fun CardStatementsCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    account: LiveAccountSummary,
) {
    val terms = mobileSession.planning.debtTerms.firstOrNull { it.accountId == account.id }
    val closeDay = terms?.statementCloseDay ?: return
    val balance = account.balanceMinor ?: return
    val records = mobileSession.transactionRecords
    val statements =
        remember(records, account.id, closeDay, balance) {
            val dated =
                records
                    .filter { it.accountId == account.id && !it.isParent && !it.localDeleted && !it.remoteDeleted }
                    .mapNotNull { record -> record.recordDate?.let { date -> record.amountMinor?.let { date to it } } }
            cardStatements(dated, balance, closeDay, platformTodayIsoDate(), STATEMENTS_SHOWN)
        }
    if (statements.isEmpty()) return
    SectionCard("Statements", detail = "Closing on day $closeDay of each month") {
        statements.forEachIndexed { index, statement ->
            if (index > 0) DividerLine()
            val money = { minor: Long -> if (state.privacyModeEnabled) "•••" else state.formatCurrency(minor) }
            val status = if (statement.paid) "Paid" else "${money(statement.owedMinor - statement.paidSinceMinor)} still to pay"
            ListRow(
                reportMonthLabel(statement.closedOn.take(7)),
                "${money(statement.chargesMinor)} charged · ${money(statement.paymentsMinor)} paid · $status",
                trailing = money(statement.owedMinor.coerceAtLeast(0)),
                options = ListRowOptions(showChevron = false),
            )
        }
    }
}

private const val STATEMENTS_SHOWN = 6
