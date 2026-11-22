package app.tandemforactual.ui.feature.banking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed interface BankProviderState {
    data object Idle : BankProviderState

    data object Loading : BankProviderState

    data object NotSetUp : BankProviderState

    data class Failed(
        val message: String,
    ) : BankProviderState

    data class Ready(
        val accounts: List<BankProviderAccount>,
    ) : BankProviderState
}

/** Which link, relink or manage flow the bank connections screen is in. */
internal class BankConnectionsState {
    var reload by mutableIntStateOf(0)
    val providerStates = mutableStateMapOf<String, BankProviderState>()
    var linking by mutableStateOf<Pair<BankProvider, BankProviderAccount>?>(null)
    var managing by mutableStateOf<LinkedAccount?>(null)
    var relinkFor by mutableStateOf<LinkedAccount?>(null)
    var browserProvider by mutableStateOf<BrowserBankProvider?>(null)
}

/** Links, relinks, unlinks and syncs bank accounts for the bank connections screen. */
internal class BankConnectionsActions(
    private val ui: BankConnectionsState,
    private val scope: CoroutineScope,
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val password: String?,
) {
    fun loadAccounts(provider: BankProvider) {
        if (provider is BrowserBankProvider) {
            ui.browserProvider = provider
            return
        }
        ui.providerStates[provider.source] = BankProviderState.Loading
        scope.launch {
            ui.providerStates[provider.source] =
                runCatching { mobileSession.banking.loadBankProviderAccounts(provider) }.fold(
                    { accounts -> accounts?.let { BankProviderState.Ready(it) } ?: BankProviderState.NotSetUp },
                    { BankProviderState.Failed("Couldn’t reach ${provider.displayName} through your Actual server.") },
                )
        }
    }

    fun pickProviderAccount(
        provider: BankProvider,
        providerAccount: BankProviderAccount,
    ) {
        val target = ui.relinkFor
        if (target == null) {
            ui.linking = provider to providerAccount
        } else {
            ui.relinkFor = null
            linkTo(target, provider, providerAccount)
        }
    }

    fun relink(account: LinkedAccount) {
        ui.managing = null
        ui.relinkFor = account
        loadAccounts(BankProviders.forSource(account.source))
    }

    fun unlink(account: LinkedAccount) {
        ui.managing = null
        scope.launch {
            if (mobileSession.banking.unlinkBankAccount(state.selectedBudgetId, account.id, password)) ui.reload++
        }
    }

    fun linkInto(account: LiveAccountSummary) {
        val (provider, providerAccount) = ui.linking ?: return
        ui.linking = null
        scope.launch {
            if (mobileSession.banking.linkBankAccount(
                    state.selectedBudgetId,
                    account.id,
                    provider,
                    providerAccount,
                    password,
                )
            ) {
                ui.reload++
            }
        }
    }

    fun syncLinkedAccounts() {
        scope.launch { mobileSession.banking.runBankSync(state.selectedBudgetId, password) }
    }

    private fun linkTo(
        target: LinkedAccount,
        provider: BankProvider,
        providerAccount: BankProviderAccount,
    ) {
        scope.launch {
            if (mobileSession.banking.linkBankAccount(state.selectedBudgetId, target.id, provider, providerAccount, password)) ui.reload++
        }
    }
}
