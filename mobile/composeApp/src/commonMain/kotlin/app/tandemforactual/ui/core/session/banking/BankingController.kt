package app.tandemforactual.ui.core.session.banking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.bankInstitutions
import app.tandemforactual.api.client.bankLinkStatus
import app.tandemforactual.api.client.bankProviderAccounts
import app.tandemforactual.api.client.budgetBankSyncInterval
import app.tandemforactual.api.client.budgetLinkedAccounts
import app.tandemforactual.api.client.claimDueBankSync
import app.tandemforactual.api.client.linkBudgetBankAccount
import app.tandemforactual.api.client.runBudgetBankSync
import app.tandemforactual.api.client.setBudgetBankSyncInterval
import app.tandemforactual.api.client.startBankLink
import app.tandemforactual.api.client.unlinkBudgetBankAccount
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.session.banking.BankingController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Bank links for the open budget: providers, browser sign-in, linking and bank sync through the Actual server. */
internal class BankingController(
    private val apiClient: TandemApiClient,
    private val nowEpochMillis: () -> Long,
    private val preferences: () -> AppPreferencesStore?,
    private val performBudgetAction: suspend (String, String?, String, suspend () -> Unit) -> Boolean,
) {
    suspend fun loadBankProviderAccounts(provider: BankProvider): List<BankProviderAccount>? = apiClient.bankProviderAccounts(provider)

    suspend fun loadBankInstitutions(
        provider: BrowserBankProvider,
        country: String,
    ): List<BankInstitution> = apiClient.bankInstitutions(provider, country)

    suspend fun startBankLink(
        provider: BrowserBankProvider,
        institution: BankInstitution,
    ): BrowserLink = apiClient.startBankLink(provider, institution)

    /** Asks the server until the bank sign-in finishes, fails, or ten minutes pass; a dropped request just asks again. */
    suspend fun awaitBankLink(
        provider: BrowserBankProvider,
        handle: String,
    ): BrowserLinkStatus {
        val deadline = nowEpochMillis() + BANK_LINK_TIMEOUT_MILLIS
        while (nowEpochMillis() < deadline) {
            val status =
                try {
                    apiClient.bankLinkStatus(provider, handle)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    BrowserLinkStatus.Pending
                }
            if (status != BrowserLinkStatus.Pending) return status
            delay(BANK_LINK_POLL_MILLIS)
        }
        return BrowserLinkStatus.Failed("timeout")
    }

    /** Bank links for the open budget, with when each last synced and whether its last sync failed. */
    var bankLinks by mutableStateOf<List<LinkedAccount>>(emptyList())

    /** Actual's Automatic syncing interval for the open budget in minutes, 0 when off, null until loaded. */
    var syncIntervalMinutes by mutableStateOf<Int?>(null)

    suspend fun loadSyncInterval(
        budgetId: String,
        encryptionPassword: String?,
    ) {
        syncIntervalMinutes = apiClient.budgetBankSyncInterval(budgetId, encryptionPassword)
    }

    suspend fun setSyncInterval(
        budgetId: String,
        minutes: Int,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t save the sync schedule. Try again.") {
            apiClient.setBudgetBankSyncInterval(budgetId, minutes, encryptionPassword)
            loadSyncInterval(budgetId, encryptionPassword)
        }

    /** Runs bank sync when Actual's schedule says it's due, once per interval at most even when a bank keeps failing. */
    suspend fun runAutomaticBankSyncIfDue(
        budgetId: String,
        encryptionPassword: String?,
    ): Boolean {
        val store = preferences() ?: return false
        if (!apiClient.claimDueBankSync(budgetId, encryptionPassword, store, nowEpochMillis())) return false
        return runBankSync(budgetId, encryptionPassword)
    }

    suspend fun loadLinkedAccounts(
        budgetId: String,
        encryptionPassword: String?,
    ): List<LinkedAccount> = apiClient.budgetLinkedAccounts(budgetId, encryptionPassword).also { bankLinks = it }

    suspend fun linkBankAccount(
        budgetId: String,
        accountId: String,
        provider: BankProvider,
        account: BankProviderAccount,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t link this account. Try again.") {
            apiClient.linkBudgetBankAccount(budgetId, accountId, provider, account, encryptionPassword)
        }

    suspend fun unlinkBankAccount(
        budgetId: String,
        accountId: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t unlink this account. Try again.") {
            apiClient.unlinkBudgetBankAccount(budgetId, accountId, encryptionPassword)
        }

    suspend fun runBankSync(
        budgetId: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(
            budgetId,
            encryptionPassword,
            "Couldn’t sync transactions from Actual. Check the bank connection and try again.",
        ) {
            try {
                apiClient.runBudgetBankSync(budgetId, encryptionPassword)
            } finally {
                runCatching { loadLinkedAccounts(budgetId, encryptionPassword) }
            }
        }

    private companion object {
        const val BANK_LINK_TIMEOUT_MILLIS = 10L * 60 * 1000
        const val BANK_LINK_POLL_MILLIS = 3_000L
    }
}
