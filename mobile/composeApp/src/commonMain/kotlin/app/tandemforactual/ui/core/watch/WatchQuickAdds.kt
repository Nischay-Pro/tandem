package app.tandemforactual.ui.core.watch

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxFile
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.WatchSettings
import app.tandemforactual.ui.core.automation.AutomatedTransaction
import app.tandemforactual.ui.core.automation.AutomationAmountFormat
import app.tandemforactual.ui.core.automation.AutomationException
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.automation.AutomationTransactionInput
import app.tandemforactual.ui.core.automation.addAutomatedTransaction
import app.tandemforactual.ui.core.automation.automationDecimal
import app.tandemforactual.ui.core.automation.sealAutomatedTransaction
import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddResult
import app.tandemforactual.watch.WatchQuickAddStatus

/** How Change History and the automation log name transactions added on a watch. */
const val WATCH_SOURCE = "Watch"

const val WATCH_LOCKED_MESSAGE = "Open Tandem to unlock"

const val WATCH_SEALED_MESSAGE = "Saved · added when you unlock"

class WatchQuickAddOutcome(
    val result: WatchQuickAddResult,
    val target: AutomationTarget? = null,
    val added: AutomatedTransaction? = null,
)

/**
 * Adds a watch entry through the outbox the way automation apps do, dated the day it was entered. The entry id keeps
 * a resent entry from being added twice. A locked encrypted budget answers [WatchQuickAddStatus.LOCKED] so the watch
 * holds the entry until the budget opens; [whenLocked] lets a platform take it in some other way instead.
 */
suspend fun addFromWatch(
    mainDatabase: TandemDatabase,
    preferences: Map<String, String>,
    entry: WatchQuickAdd,
    entryDateIso: String,
    nowEpochMs: Long,
    openBudget: suspend (AutomationTarget) -> TandemDatabase,
    whenLocked: suspend (AutomationTarget, WatchQuickAdd) -> WatchQuickAddOutcome? = { _, _ -> null },
): WatchQuickAddOutcome {
    fun refused(message: String) = WatchQuickAddOutcome(WatchQuickAddResult(entry.entryId, WatchQuickAddStatus.REFUSED, message))
    if (!WatchSettings.from(preferences).quickAddEnabled) return refused("Quick add is off. Turn it on in Tandem under Settings › Watch.")
    val budgetId =
        preferences["selectedBudgetId"]?.takeIf(String::isNotBlank)
            ?: return refused("No budget is open. Open a budget in Tandem first.")
    if (preferences["readOnlyMode"] == "true") return refused("Lock editing is on in Tandem. Turn it off to add from your watch.")
    val target = AutomationTarget(budgetId, preferences["selectedBudgetEncrypted"] == "true")
    val database =
        try {
            openBudget(target)
        } catch (_: AutomationException) {
            return whenLocked(target, entry)
                ?: WatchQuickAddOutcome(WatchQuickAddResult(entry.entryId, WatchQuickAddStatus.LOCKED, WATCH_LOCKED_MESSAGE), target)
        }
    return try {
        val account = watchAccount(LocalBudgetRepository(database) { nowEpochMs }, budgetId, preferences)?.first
        val added = addAutomatedTransaction(database, budgetId, entry.input(account, entryDateIso), WATCH_SOURCE, entryDateIso, nowEpochMs)
        val status = if (added.repeated) WatchQuickAddStatus.REPEATED else WatchQuickAddStatus.ADDED
        WatchQuickAddOutcome(WatchQuickAddResult(entry.entryId, status, added.summary.takeUnless { added.repeated }), target, added)
    } catch (error: AutomationException) {
        refused(error.message ?: "Tandem couldn’t add it. Open Tandem and try again.")
    } finally {
        if (database !== mainDatabase) database.close()
    }
}

/**
 * Seals a watch entry for a locked encrypted budget so it's added when the budget is next unlocked, to the account
 * used last. Null when it can't be sealed yet, such as before the budget's first unlock, so the watch holds it.
 */
suspend fun sealFromWatch(
    mainDatabase: TandemDatabase,
    inbox: InboxFile,
    preferences: Map<String, String>,
    target: AutomationTarget,
    entry: WatchQuickAdd,
    entryDateIso: String,
    nowEpochMs: Long,
): WatchQuickAddOutcome? {
    val input = entry.input(preferences["lastTransactionAccountId"], entryDateIso)
    val sealed =
        try {
            sealAutomatedTransaction(mainDatabase, inbox, target.budgetId, input, WATCH_SOURCE, entryDateIso, nowEpochMs)
        } catch (_: AutomationException) {
            return null
        }
    return WatchQuickAddOutcome(WatchQuickAddResult(entry.entryId, WatchQuickAddStatus.ADDED, WATCH_SEALED_MESSAGE), target, sealed)
}

private fun WatchQuickAdd.input(
    account: String?,
    entryDateIso: String,
) = AutomationTransactionInput(
    amount = (if (amountMinor < 0) "" else "+") + automationDecimal(amountMinor, 2),
    payee = payee,
    category = categoryId,
    account = account,
    date = entryDateIso,
    externalId = "watch:$entryId",
    amountFormat = AutomationAmountFormat.PLAIN,
)
