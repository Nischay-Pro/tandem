package app.tandemforactual.ui.feature.banking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BankCredentialSource
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed interface BankProviderState {
    data object Idle : BankProviderState

    data object Loading : BankProviderState

    /** Not usable for the budget; [source] is set when the budget has its own credentials that aren't working. */
    data class NotSetUp(
        val source: BankCredentialSource? = null,
    ) : BankProviderState

    data class Failed(
        val message: String,
    ) : BankProviderState

    data class Ready(
        val accounts: List<BankProviderAccount>,
        val source: BankCredentialSource = BankCredentialSource.SERVER,
    ) : BankProviderState
}

/** Which link, relink or manage flow the bank connections screen is in. */
internal class BankConnectionsState {
    var reload by mutableIntStateOf(0)
    val providerStates = mutableStateMapOf<String, BankProviderState>()
    var linking by mutableStateOf<Pair<BankProvider, BankProviderAccount>?>(null)
    var managing by mutableStateOf<LinkedAccount?>(null)
    var unlinking by mutableStateOf<LinkedAccount?>(null)
    var relinkFor by mutableStateOf<LinkedAccount?>(null)
    var browserProvider by mutableStateOf<BrowserBankProvider?>(null)
    var simpleFinSetup by mutableStateOf<SimpleFinSetup?>(null)
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
                runCatching { mobileSession.banking.loadBankProviderAccounts(provider, state.selectedBudgetId) }.fold(
                    { access ->
                        access.accounts?.let { BankProviderState.Ready(it, access.source ?: BankCredentialSource.SERVER) }
                            ?: BankProviderState.NotSetUp(access.source)
                    },
                    { BankProviderState.Failed(providerFailure(provider, it)) },
                )
        }
    }

    fun openSimpleFinSetup(current: BankCredentialSource?) {
        ui.simpleFinSetup = SimpleFinSetup(current)
        scope.launch {
            val admin = runCatching { mobileSession.banking.canManageServerBankCredentials() }.getOrDefault(false)
            ui.simpleFinSetup = ui.simpleFinSetup?.copy(allowServer = admin)
        }
    }

    fun saveSimpleFinToken(
        token: String,
        target: BankCredentialSource,
    ) = updateSimpleFin { mobileSession.banking.saveSimpleFinToken(state.selectedBudgetId, token, target) }

    fun removeSimpleFinBudgetToken() = updateSimpleFin { mobileSession.banking.removeSimpleFinBudgetToken(state.selectedBudgetId) }

    private fun updateSimpleFin(change: suspend () -> Unit) {
        ui.simpleFinSetup = ui.simpleFinSetup?.copy(saving = true, error = null) ?: return
        scope.launch {
            runCatching { change() }.fold(
                {
                    ui.simpleFinSetup = null
                    loadAccounts(SimpleFin)
                },
                { ui.simpleFinSetup = ui.simpleFinSetup?.copy(saving = false, error = credentialFailure(it)) },
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

    fun confirmUnlink(account: LinkedAccount) {
        ui.managing = null
        ui.unlinking = account
    }

    fun unlink(account: LinkedAccount) {
        ui.managing = null
        ui.unlinking = null
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

    fun linkIntoNew(offBudget: Boolean) {
        val (provider, providerAccount) = ui.linking ?: return
        ui.linking = null
        scope.launch {
            if (mobileSession.banking.linkBankAccountToNewAccount(state.selectedBudgetId, offBudget, provider, providerAccount, password)) {
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

/** The SimpleFIN setup sheet: whose token the budget uses now, and whether this user may set the server's. */
internal data class SimpleFinSetup(
    val current: BankCredentialSource?,
    val allowServer: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

internal fun providerFailure(
    provider: BankProvider,
    error: Throwable,
): String =
    if (provider == SimpleFin && (error as? TandemApiException)?.errorCode == "bank_sync_failed") {
        "SimpleFIN didn’t accept the token. Get a new setup token and set it up again."
    } else {
        "Couldn’t reach ${provider.displayName} through your Actual server."
    }

internal fun credentialFailure(error: Throwable): String =
    when ((error as? TandemApiException)?.errorCode) {
        "not_admin" -> "Only an Actual server admin can set a token for every budget."
        "file_access_denied" -> "Only this budget’s owner or a server admin can change its bank credentials."
        else -> "Couldn’t reach your Actual server. Try again."
    }
