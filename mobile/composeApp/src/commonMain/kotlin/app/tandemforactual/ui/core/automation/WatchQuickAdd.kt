package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.watchQuickAddRefusal

/** A transaction typed on the watch; [id] is made on the watch, so a resend of the same entry adds nothing new. */
data class WatchQuickAddEntry(
    val id: String,
    val amount: String,
    val categoryId: String? = null,
    val payee: String? = null,
    val accountId: String? = null,
)

/** What the phone tells the watch about one entry; a refused entry carries the reason to show. */
data class WatchQuickAddReply(
    val entryId: String,
    val added: Boolean,
    val message: String,
)

const val WATCH_SOURCE = "Watch"

private val ENTRY_ID = Regex("^[A-Za-z0-9-]{8,64}$")

/**
 * Adds a watch entry to the open budget through the outbox, like any other automation app, so Change History and the
 * automation log name the watch. Settings › Watch decides whether quick add is on, not the automation opt-in.
 */
suspend fun addWatchQuickAdd(
    database: TandemDatabase,
    preferences: Map<String, String>,
    entry: WatchQuickAddEntry,
    todayIso: String,
    nowEpochMs: Long,
): WatchQuickAddReply {
    if (!ENTRY_ID.matches(entry.id)) return WatchQuickAddReply(entry.id, added = false, message = "Try adding it again.")
    val refusal = watchQuickAddRefusal(preferences)
    if (refusal != null) return WatchQuickAddReply(entry.id, added = false, message = refusal)
    return try {
        val added =
            addAutomatedTransaction(
                database = database,
                budgetId = preferences["selectedBudgetId"].orEmpty(),
                input =
                    AutomationTransactionInput(
                        amount = entry.amount,
                        payee = entry.payee,
                        category = entry.categoryId,
                        account = entry.accountId?.takeIf(String::isNotBlank) ?: preferences["lastTransactionAccountId"],
                        externalId = "watch:${entry.id}",
                        amountFormat = AutomationAmountFormat.PLAIN,
                    ),
                source = WATCH_SOURCE,
                todayIso = todayIso,
                nowEpochMs = nowEpochMs,
            )
        WatchQuickAddReply(entry.id, added = true, message = if (added.repeated) "Already added." else added.summary)
    } catch (error: AutomationException) {
        WatchQuickAddReply(entry.id, added = false, message = error.message.orEmpty())
    }
}
