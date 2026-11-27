package app.tandemforactual.api.client

import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import kotlinx.coroutines.sync.withLock

/** Whose bank credentials a budget uses: its own, stored on the Actual server for that budget, or the server-wide ones. */
enum class BankCredentialSource { BUDGET, SERVER }

data class BankProviderStatus(
    val configured: Boolean,
    val source: BankCredentialSource?,
)

/** What a provider offers the budget: its accounts, or null when it isn't set up, and whose credentials it uses. */
data class BankProviderAccess(
    val source: BankCredentialSource?,
    val accounts: List<BankProviderAccount>?,
)

/** The provider's accounts as the budget sees them; Actual picks the budget's own credentials over the server's. */
suspend fun TandemApiClient.bankProviderAccounts(
    provider: BankProvider,
    budgetId: String,
): BankProviderAccess =
    sessionMutex.withLock {
        val client = requireServer()
        call {
            val fileId = client.budgetFile(budgetId).fileId
            val status = client.bankStatus(provider, fileId)
            BankProviderAccess(status.source, if (status.configured) client.bankAccounts(provider, fileId) else null)
        }
    }

/** Whether the user may set credentials every budget on the server shares. */
suspend fun TandemApiClient.canManageServerBankCredentials(): Boolean = sessionMutex.withLock { call { requireServer().isAdmin() } }

/**
 * Hands a SimpleFIN setup token to the Actual server as Actual's own setup does: the server claims it on the next
 * accounts request and keeps the access, so the device never stores either. Moving a budget to the server's token
 * drops the budget's own.
 */
suspend fun TandemApiClient.saveSimpleFinToken(
    budgetId: String,
    setupToken: String,
    scope: BankCredentialSource,
): Unit =
    sessionMutex.withLock {
        val client = requireServer()
        call {
            val fileId = client.budgetFile(budgetId).fileId
            val target = fileId.takeIf { scope == BankCredentialSource.BUDGET }
            val current = client.bankStatus(SimpleFin, fileId).source
            client.setSecret(SIMPLEFIN_TOKEN, setupToken.trim(), target)
            client.removeSecret(SIMPLEFIN_ACCESS_KEY, target)
            if (scope == BankCredentialSource.SERVER && current == BankCredentialSource.BUDGET) client.removeSimpleFin(fileId)
        }
    }

/** Removes the budget's own SimpleFIN token, so it falls back to the server's, if there is one. */
suspend fun TandemApiClient.removeSimpleFinBudgetToken(budgetId: String): Unit =
    sessionMutex.withLock {
        val client = requireServer()
        call { client.removeSimpleFin(client.budgetFile(budgetId).fileId) }
    }

private suspend fun ActualServerClient.removeSimpleFin(fileId: String) {
    removeSecret(SIMPLEFIN_TOKEN, fileId)
    removeSecret(SIMPLEFIN_ACCESS_KEY, fileId)
}

private const val SIMPLEFIN_TOKEN = "simplefin_token"
private const val SIMPLEFIN_ACCESS_KEY = "simplefin_accessKey"
