package app.tandemforactual.ui.core.session

import app.tandemforactual.data.inbox.InboxPublicKeys
import app.tandemforactual.ui.core.automation.openSealedInbox
import kotlinx.coroutines.CancellationException

/**
 * Adds what automation sealed while [budgetId] was locked to its bound vault's outbox, and returns how many went in.
 * Nothing happens unless that vault is open and saved, so entries never land in a vault that might be thrown away.
 */
internal suspend fun TandemMobileSession.applySealedInbox(
    budgetId: String,
    password: String?,
): Int {
    val vaultDatabase = budgetStorage?.takeIf { it.budgetId == budgetId }?.database ?: return 0
    val inbox = sealedInboxSource?.takeIf { it.first == budgetId }?.second?.inboxFile(budgetId) ?: return 0
    val mainDatabase = mainStorage?.database ?: return 0
    if (password.isNullOrBlank()) return 0
    val result =
        try {
            openSealedInbox(vaultDatabase, mainDatabase, inbox, budgetId, password, currentDateIso())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return 0
        }
    result.message?.let(::reportBudgetActionMessage)
    if (result.added > 0) loadLocalRecords(budgetId)
    return result.added
}

/** Forgets every budget's inbox public key, so nothing more is sealed for vaults that are gone. */
internal suspend fun TandemMobileSession.forgetSealedInboxKeys(budgetId: String? = null) {
    val database = mainStorage?.database ?: return
    if (budgetId != null) {
        InboxPublicKeys.forget(database, budgetId)
    } else {
        InboxPublicKeys.forgetAll(database)
    }
}
