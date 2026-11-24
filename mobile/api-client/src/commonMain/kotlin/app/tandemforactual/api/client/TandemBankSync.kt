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
import kotlinx.coroutines.sync.withLock

/** The provider's accounts, or null when the provider isn't set up on the Actual server. */
suspend fun TandemApiClient.bankProviderAccounts(provider: BankProvider): List<BankProviderAccount>? =
    sessionMutex.withLock {
        val client = requireServer()
        call { if (client.bankConfigured(provider)) client.bankAccounts(provider) else null }
    }

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
): StatementImportSummary {
    val result =
        edit(budgetId, encryptionPassword) { edits ->
            edits.importTransactions(
                accountId,
                transactions.map { ImportRow(it.date, it.amountMinor, it.payeeName, it.notes, it.importedId) },
            )
        }
    return StatementImportSummary(result.added.size, result.updated.size, 0)
}

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.previewBudgetAccountStatementImport(
    budgetId: String,
    accountId: String,
    importedIds: List<String>,
    encryptionPassword: String?,
): StatementImportPreview = StatementImportPreview(call { budget(budgetId, encryptionPassword).importedIds(accountId, importedIds) })
