package app.tandemforactual.ui.core.session

import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.PlanningCipher
import app.tandemforactual.data.planning.PlanningStore
import app.tandemforactual.data.store.AlertKind
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalAlert
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.NotificationPolicy
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import kotlinx.coroutines.flow.first

/** A credit card's next payment: [owedMinor] is what the card owes now. */
internal data class CardDue(
    val accountId: String,
    val name: String,
    val date: String,
    val owedMinor: Long,
) {
    val key: String get() = "$accountId@$date"
}

/** The next date on or after [today] that falls on [dueDay], moved to the month's last day when it is shorter. */
internal fun nextDueDate(
    dueDay: Int,
    today: String,
): String {
    fun on(monthId: String) = "$monthId-${dueDay.coerceAtMost(daysInMonth(monthId) ?: dueDay).toString().padStart(2, '0')}"
    val thisMonth = on(today.take(7))
    return if (thisMonth >= today) thisMonth else on(ActualDates.addDays("${today.take(7)}-28", 7).take(7))
}

/** Cards with a due day and a balance owed whose next payment falls within [leadDays] of [today], soonest first. */
internal fun cardsDue(
    terms: List<DebtTerms>,
    accounts: Map<String, Pair<String, Long?>>,
    today: String,
    leadDays: Int,
): List<CardDue> {
    val through = ActualDates.addDays(today, leadDays)
    return terms
        .filter { it.kind == DebtKind.CARD }
        .mapNotNull { term ->
            val dueDay = term.dueDay ?: return@mapNotNull null
            val (name, balance) = accounts[term.accountId] ?: return@mapNotNull null
            val owed = -(balance ?: 0L)
            val date = nextDueDate(dueDay, today)
            CardDue(term.accountId, name, date, owed).takeIf { owed > 0 && date <= through }
        }.sortedBy { it.date }
}

/** Reminders for card payments coming up, for background sync on either platform. */
object CardDueReminders {
    /** The cards due soon that haven't been announced for that date yet, or null when there are none. */
    suspend fun alert(
        database: TandemDatabase,
        planningKeys: DeviceCredentialStore?,
        repository: LocalBudgetRepository,
        budgetId: String,
        today: String,
        policy: NotificationPolicy,
        formatAmount: (Long) -> String,
    ): LocalAlert? {
        val key = PlanningKeyring(planningKeys).existingKey(budgetId) ?: return null
        val terms = PlanningStore(database.planningRecordDao(), budgetId, { PlanningCipher(key) }, { 0L }).debtTerms().first()
        val accounts =
            repository
                .findRecords(budgetId, "account")
                .associate { it.recordId to ((it.recordName() ?: "A card") to it.recordBody()?.longValue("balance_current")) }
        val due = cardsDue(terms, accounts, today, policy.billLeadDays)
        val store = AppPreferencesStore(database)
        val announcedKey = "internal:announced-card-due:$budgetId"
        val announced =
            store
                .read()[announcedKey]
                .orEmpty()
                .split(',')
                .toSet()
        store.putValue(announcedKey, due.joinToString(",") { it.key })
        val fresh = due.filterNot { it.key in announced }
        return cardDueMessage(fresh, policy.hideAmounts, formatAmount)?.let { LocalAlert(AlertKind.CARD_DUE, "Card payment due", it) }
    }
}

internal fun cardDueMessage(
    fresh: List<CardDue>,
    hideAmounts: Boolean,
    formatAmount: (Long) -> String,
): String? {
    val first = fresh.firstOrNull() ?: return null
    if (fresh.size > 1) return "${first.name} and ${fresh.size - 1} more cards are due soon."
    val owed = if (hideAmounts) "" else " ${formatAmount(first.owedMinor)} owed,"
    return "${first.name}:$owed due ${displayTransactionDate(first.date)}."
}
