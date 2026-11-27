package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ImportRow
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.actual.createLinkedAccount
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import app.tandemforactual.data.store.AppPreferencesStore
import kotlinx.coroutines.sync.withLock

suspend fun TandemApiClient.bankInstitutions(
    provider: BrowserBankProvider,
    country: String,
): List<BankInstitution> =
    sessionMutex.withLock {
        requireServer().let { client ->
            call { client.bankInstitutions(provider, country) }
        }
    }

suspend fun TandemApiClient.startBankLink(
    provider: BrowserBankProvider,
    institution: BankInstitution,
): BrowserLink = sessionMutex.withLock { requireServer().let { client -> call { client.startBankLink(provider, institution) } } }

/** Asked without the session lock, since Enable Banking holds the request open until the bank answers. */
suspend fun TandemApiClient.bankLinkStatus(
    provider: BrowserBankProvider,
    handle: String,
): BrowserLinkStatus = requireServer().let { client -> call { client.bankLinkStatus(provider, handle) } }

suspend fun TandemApiClient.budgetLinkedAccounts(
    budgetId: String,
    encryptionPassword: String?,
): List<LinkedAccount> = call { budget(budgetId, encryptionPassword).linkedAccounts(today()) }

suspend fun TandemApiClient.linkBudgetBankAccount(
    budgetId: String,
    accountId: String,
    provider: BankProvider,
    account: BankProviderAccount,
    encryptionPassword: String?,
) = edit(
    budgetId,
    encryptionPassword,
) { it.linkBankAccount(accountId, provider.source, account.id, account.bankId, account.institution) }

/** Minutes between automatic bank syncs, as Actual's Automatic syncing setting stores them; 0 is off. */
suspend fun TandemApiClient.budgetBankSyncInterval(
    budgetId: String,
    encryptionPassword: String?,
): Int = call { budget(budgetId, encryptionPassword).bankSyncInterval(today()) }

suspend fun TandemApiClient.setBudgetBankSyncInterval(
    budgetId: String,
    minutes: Int,
    encryptionPassword: String?,
) = edit(budgetId, encryptionPassword) { it.setBankSyncInterval(minutes) }

suspend fun TandemApiClient.automaticBankSyncDue(
    budgetId: String,
    encryptionPassword: String?,
    now: Long,
    lastAutomaticRun: Long?,
): Boolean = call { budget(budgetId, encryptionPassword).automaticBankSyncDue(today(), now, lastAutomaticRun) }

/**
 * Whether Actual's Automatic syncing says a bank sync is due, from the open app or a background task. A due attempt is
 * recorded before the caller syncs, so a bank that keeps failing waits a full interval wherever the next check runs.
 */
suspend fun TandemApiClient.claimDueBankSync(
    budgetId: String,
    encryptionPassword: String?,
    preferences: AppPreferencesStore,
    now: Long,
): Boolean {
    val key = "$LAST_AUTOMATIC_BANK_SYNC_KEY:$budgetId"
    val lastRun = preferences.read()[key]?.toLongOrNull()
    if (!automaticBankSyncDue(budgetId, encryptionPassword, now, lastRun)) return false
    preferences.putValue(key, now.toString())
    return true
}

private const val LAST_AUTOMATIC_BANK_SYNC_KEY = "internal:bank-sync-last-automatic-run"

suspend fun TandemApiClient.linkBudgetBankAccountToNewAccount(
    budgetId: String,
    offBudget: Boolean,
    provider: BankProvider,
    account: BankProviderAccount,
    encryptionPassword: String?,
): String =
    edit(budgetId, encryptionPassword) {
        it.createLinkedAccount(account.name, offBudget, provider.source, account.id, account.bankId, account.institution)
    }

suspend fun TandemApiClient.unlinkBudgetBankAccount(
    budgetId: String,
    accountId: String,
    encryptionPassword: String?,
) = edit(budgetId, encryptionPassword) { it.unlinkBankAccount(accountId) }

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.runBudgetBankSync(
    budgetId: String,
    encryptionPassword: String?,
): Unit = call { budget(budgetId, encryptionPassword).bankSync(today()) }

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.importBudgetAccountTransactions(
    budgetId: String,
    accountId: String,
    transactions: List<StatementImportItem>,
    encryptionPassword: String?,
    idempotencyKeyOverride: String? = null,
    reviewed: Boolean = false,
): StatementImportSummary {
    val result =
        edit(budgetId, encryptionPassword) { edits ->
            edits.importTransactions(
                accountId,
                transactions.map {
                    ImportRow(
                        it.date,
                        it.amountMinor,
                        it.payeeName,
                        it.notes,
                        it.importedId,
                        cleared = true.takeIf { reviewed },
                        matchId = it.matchId,
                    )
                },
                reviewed,
            )
        }
    return StatementImportSummary(result.added.size, result.updated.size, 0)
}

/** The account's transactions between two dates, read from this device's copy of the budget, for statement matching. */
suspend fun TandemApiClient.budgetAccountLedger(
    budgetId: String,
    accountId: String,
    from: String,
    to: String,
    encryptionPassword: String?,
): List<StatementLedgerTransaction> =
    call {
        budget(budgetId, encryptionPassword).ledger(accountId, from, to).map {
            StatementLedgerTransaction(
                it.id,
                it.date,
                it.amount,
                it.payeeName,
                it.importedPayee,
                it.notes,
                it.importedId,
                it.cleared,
                it.reconciled,
            )
        }
    }

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.previewBudgetAccountStatementImport(
    budgetId: String,
    accountId: String,
    importedIds: List<String>,
    encryptionPassword: String?,
): StatementImportPreview = StatementImportPreview(call { budget(budgetId, encryptionPassword).importedIds(accountId, importedIds) })
