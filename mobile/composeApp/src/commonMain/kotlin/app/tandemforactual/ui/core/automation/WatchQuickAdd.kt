package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxFile
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
 * automation log name the watch. Settings › Watch decides whether quick add is on, not the automation opt-in. An
 * encrypted budget's entry is sealed into [inbox] and added at its next unlock.
 */
suspend fun addWatchQuickAdd(
    database: TandemDatabase,
    preferences: Map<String, String>,
    entry: WatchQuickAddEntry,
    todayIso: String,
    nowEpochMs: Long,
    inbox: InboxFile,
): WatchQuickAddReply {
    if (!ENTRY_ID.matches(entry.id)) return WatchQuickAddReply(entry.id, added = false, message = "Try adding it again.")
    val refusal = watchQuickAddRefusal(preferences)
    if (refusal != null) return WatchQuickAddReply(entry.id, added = false, message = refusal)
    val budgetId = preferences["selectedBudgetId"].orEmpty()
    val input =
        AutomationTransactionInput(
            amount = entry.amount,
            payee = entry.payee,
            category = entry.categoryId,
            account = entry.accountId?.takeIf(String::isNotBlank) ?: preferences["lastTransactionAccountId"],
            externalId = "watch:${entry.id}",
            amountFormat = AutomationAmountFormat.PLAIN,
        )
    return try {
        val added =
            if (preferences["selectedBudgetEncrypted"] == "true") {
                sealAutomatedTransaction(database, inbox, budgetId, input, WATCH_SOURCE, todayIso, nowEpochMs)
            } else {
                addAutomatedTransaction(database, budgetId, input, WATCH_SOURCE, todayIso, nowEpochMs)
            }
        WatchQuickAddReply(entry.id, added = true, message = if (added.repeated) "Already added." else added.summary)
    } catch (error: AutomationException) {
        WatchQuickAddReply(entry.id, added = false, message = error.message.orEmpty())
    }
}
