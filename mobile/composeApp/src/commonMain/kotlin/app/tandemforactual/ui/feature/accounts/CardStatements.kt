@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.navigation.openAccountActivity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.SectionCard

/**
 * One closed card statement: the days it covers, when it's due, what was charged and paid in it, what was owed at
 * closing, and what was paid on it since.
 */
internal data class CardStatement(
    val openedOn: String,
    val closedOn: String,
    val dueOn: String?,
    val chargesMinor: Long,
    val paymentsMinor: Long,
    val owedMinor: Long,
    val paidSinceMinor: Long,
) {
    val paid: Boolean get() = owedMinor <= 0 || paidSinceMinor >= owedMinor
    val stillOwedMinor: Long get() = (owedMinor - paidSinceMinor).coerceAtLeast(0)
}

/** The cycle still open: the days it runs and what has been charged in it so far. */
internal data class CardCycle(
    val openedOn: String,
    val closesOn: String,
    val spentMinor: Long,
)

/** The day a statement closing on [closeDay] closes in [monthId], moved to the month's last day when it is shorter. */
internal fun statementCloseDate(
    monthId: String,
    closeDay: Int,
): String? {
    val days = daysInMonth(monthId) ?: return null
    return "$monthId-${closeDay.coerceAtMost(days).toString().padStart(2, '0')}"
}

/** The first [dueDay] after a statement closes on [closedOn]. */
internal fun statementDueDate(
    closedOn: String,
    dueDay: Int,
): String {
    val sameMonth = statementCloseDate(closedOn.take(7), dueDay)
    return sameMonth?.takeIf { it > closedOn } ?: statementCloseDate(nextMonth(closedOn.take(7)), dueDay).orEmpty()
}

internal fun daysBetween(
    from: String,
    to: String,
): Long = ActualDates.dayNumber(ActualDates.toInt(to)) - ActualDates.dayNumber(ActualDates.toInt(from))

private fun nextMonth(monthId: String): String = ActualDates.addDays("$monthId-28", 7).take(7)

private fun monthBefore(monthId: String): String? {
    val year = monthId.substringBefore('-').toIntOrNull() ?: return null
    val month = monthId.substringAfter('-', "").toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return if (month == 1) "${year - 1}-12" else "$year-${(month - 1).toString().padStart(2, '0')}"
}

private fun closesUpTo(
    today: String,
    closeDay: Int,
    count: Int,
): List<String> =
    generateSequence(today.take(7), ::monthBefore)
        .mapNotNull { statementCloseDate(it, closeDay) }
        .filter { it <= today }
        .take(count)
        .toList()

/**
 * The last [count] statements closed by [today], newest first, from the card's dated amounts (charges negative) and
 * its balance now. A statement's balance is today's balance less everything dated after it closed.
 */
internal fun cardStatements(
    transactions: List<Pair<String, Long>>,
    balanceMinor: Long,
    closeDay: Int,
    dueDay: Int?,
    today: String,
    count: Int,
): List<CardStatement> {
    val closes = closesUpTo(today, closeDay, count + 1)
    return closes.zipWithNext().take(count).mapIndexed { index, (closed, previous) ->
        val nextClose = closes.getOrNull(index - 1) ?: today
        val cycle = transactions.filter { (date, _) -> date > previous && date <= closed }
        val after = transactions.filter { (date, _) -> date > closed }
        val since = transactions.filter { (date, _) -> date > closed && date <= nextClose }
        CardStatement(
            openedOn = ActualDates.addDays(previous, 1),
            closedOn = closed,
            dueOn = dueDay?.let { statementDueDate(closed, it) },
            chargesMinor = -cycle.filter { it.second < 0 }.sumOf { it.second },
            paymentsMinor = cycle.filter { it.second > 0 }.sumOf { it.second },
            owedMinor = -(balanceMinor - after.sumOf { it.second }),
            paidSinceMinor = since.filter { it.second > 0 }.sumOf { it.second },
        )
    }
}

/** The cycle open on [today]: from the day after the last close to the next close, with its charges so far. */
internal fun currentCycle(
    transactions: List<Pair<String, Long>>,
    closeDay: Int,
    today: String,
): CardCycle? {
    val lastClose = closesUpTo(today, closeDay, 1).firstOrNull() ?: return null
    val thisMonth = statementCloseDate(today.take(7), closeDay) ?: return null
    val closes = if (thisMonth > today) thisMonth else statementCloseDate(nextMonth(today.take(7)), closeDay) ?: return null
    val spent = -transactions.filter { (date, amount) -> date > lastClose && date <= today && amount < 0 }.sumOf { it.second }
    return CardCycle(ActualDates.addDays(lastClose, 1), closes, spent)
}

/** The next payment to make: the latest statement while it's unpaid and not past due, otherwise the open cycle's. */
internal fun nextCardDue(
    latest: CardStatement?,
    cycle: CardCycle?,
    dueDay: Int?,
    today: String,
): String? {
    val owing = latest?.dueOn?.takeIf { !latest.paid && it >= today }
    return owing ?: dueDay?.let { day -> cycle?.closesOn?.let { statementDueDate(it, day) } }
}

private fun dateRange(
    from: String,
    to: String,
): String = "${displayTransactionDate(from)} – ${displayTransactionDate(to)}"

/** A credit card's billing cycle and recent statements, shown once its closing day is known. */
@Composable
internal fun CardStatementsCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    account: LiveAccountSummary,
) {
    val terms = mobileSession.planning.debtTerms.firstOrNull { it.accountId == account.id }
    val closeDay = terms?.statementCloseDay ?: return
    val balance = account.balanceMinor ?: return
    val records = mobileSession.transactionRecords
    val today = platformTodayIsoDate()
    val dated =
        remember(records, account.id) {
            records
                .filter { it.accountId == account.id && !it.isParent && !it.localDeleted && !it.remoteDeleted }
                .mapNotNull { record -> record.recordDate?.let { date -> record.amountMinor?.let { date to it } } }
        }
    val statements =
        remember(dated, closeDay, terms.dueDay, balance, today) {
            cardStatements(dated, balance, closeDay, terms.dueDay, today, STATEMENTS_SHOWN)
        }
    val cycle = remember(dated, closeDay, today) { currentCycle(dated, closeDay, today) }
    val money = { minor: Long -> if (state.privacyModeEnabled) "•••" else state.formatCurrency(minor) }
    val due = nextCardDue(statements.firstOrNull(), cycle, terms.dueDay, today)
    val heading = due?.let { "Due ${displayTransactionDate(it)} (${daysBetween(today, it)}d)" } ?: "Closing on day $closeDay"
    SectionCard("Billing cycle", detail = heading) {
        cycle?.let {
            CycleRow("Current cycle", dateRange(it.openedOn, it.closesOn))
            DividerLine()
            CycleRow("Statement due", money(statements.firstOrNull()?.stillOwedMinor ?: 0L))
            DividerLine()
            CycleRow("Cycle spend", money(it.spentMinor))
        }
        statements.forEach { statement ->
            DividerLine()
            val status = if (statement.paid) "Paid" else statement.dueOn?.let { "Due ${displayTransactionDate(it)}" } ?: "Unpaid"
            ListRow(
                dateRange(statement.openedOn, statement.closedOn),
                "$status · ${money(statement.chargesMinor)} charged · ${money(statement.paymentsMinor)} paid",
                trailing = money(statement.owedMinor.coerceAtLeast(0)),
                onClick = { openAccountActivity(state, navigation, account.id, account.name, statement.openedOn to statement.closedOn) },
            )
        }
    }
}

@Composable
private fun CycleRow(
    title: String,
    value: String,
) {
    ListRow(title, trailing = value, options = ListRowOptions(showChevron = false))
}

private const val STATEMENTS_SHOWN = 6
