@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.UriHandler
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.app.AuthReturnEvents
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.actualOpenIdReturn
import app.tandemforactual.ui.core.session.cancelActualOpenIdSignIn
import app.tandemforactual.ui.core.session.completeActualOpenIdSignIn
import app.tandemforactual.ui.core.session.hasCachedBudgetSnapshot
import app.tandemforactual.ui.core.session.loadBudgets
import app.tandemforactual.ui.core.session.reportSignInError
import app.tandemforactual.ui.core.session.signInWithPassword
import app.tandemforactual.ui.core.session.startActualOpenIdSignIn
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.BudgetSelectionSnapshot
import app.tandemforactual.ui.core.state.isTab
import app.tandemforactual.ui.feature.onboarding.nextSetupRoute
import app.tandemforactual.ui.feature.onboarding.rememberRecentServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sign-in, budget selection and budget unlock actions the setup screens call. */
internal class SignInActions(
    private val env: ShellEnvironment,
    private val state: AppState,
    private val shell: AppShellState,
    private val mobileSession: TandemMobileSession,
    private val navigation: AppNavigation,
    private val coroutineScope: CoroutineScope,
    private val uriHandler: UriHandler,
) {
    fun signIn() {
        if (!mobileSession.isSigningIn) {
            coroutineScope.launch {
                if (mobileSession.signInWithPassword(state.serverUrl, state.actualPassword, env.deviceName)) {
                    state.recentServerUrls = rememberRecentServerAddress(state.recentServerUrls, state.serverUrl)
                    state.actualPassword = ""
                    navigation.reset(AppRoute.BUDGET_CHOOSER)
                }
            }
        }
    }

    fun startOpenIdSignIn() {
        if (!mobileSession.isSigningIn) {
            coroutineScope.launch {
                val redirectUrl = mobileSession.startActualOpenIdSignIn(state.serverUrl, env.deviceName)
                if (redirectUrl != null) {
                    try {
                        env.openSignInPage?.invoke(redirectUrl) { mobileSession.cancelActualOpenIdSignIn() }
                            ?: uriHandler.openUri(redirectUrl)
                    } catch (_: Exception) {
                        mobileSession.cancelActualOpenIdSignIn()
                        mobileSession.reportSignInError("Couldn’t open the sign-in page. Try again.")
                    }
                }
            }
        }
    }

    private fun selectBudget(budget: BudgetCatalogItem) {
        if (state.selectedBudgetId != budget.budgetId) state.encryptedPassword = ""
        if (state.selectedBudgetId != budget.budgetId) state.budgetDisplaySettings = null
        state.selectedBudget = budget.name
        state.selectedBudgetId = budget.budgetId
        state.selectedBudgetEncrypted = budget.isEncrypted
    }

    fun refreshBudgets() {
        coroutineScope.launch {
            mobileSession.loadBudgets()
            if (mobileSession.budgets.none { it.budgetId == state.selectedBudgetId }) {
                mobileSession.budgets.firstOrNull()?.let(::selectBudget)
            }
        }
    }

    fun openSelectedBudget(selected: BudgetCatalogItem) {
        if (selected.budgetId.isBlank()) {
            navigation.reset(AppRoute.BUDGET_CHOOSER)
            return
        }
        val previousSelection =
            state.pendingBudgetSelectionOrigin ?: BudgetSelectionSnapshot(
                budgetId = state.selectedBudgetId,
                name = state.selectedBudget,
                isEncrypted = state.selectedBudgetEncrypted,
            )
        val switchingBudget = state.selectedBudgetId != selected.budgetId
        if (switchingBudget) state.budgetDisplaySettings = null
        state.selectedBudget = selected.name
        state.selectedBudgetId = selected.budgetId
        state.selectedBudgetEncrypted = selected.isEncrypted
        if (selected.isEncrypted && env.encryptedBudgetVault != null) {
            val alreadyUnlocked =
                mobileSession.boundEncryptedBudgetId == selected.budgetId && state.encryptedPassword.isNotBlank()
            if (!alreadyUnlocked) state.encryptedPassword = ""
            val setupIncomplete = !state.hasCompletedNotificationOnboarding || !state.hasCompletedSyncOnboarding
            state.pendingSetupAfterUnlock = !alreadyUnlocked && setupIncomplete
            state.pendingBudgetSelectionOrigin = null
            if (alreadyUnlocked && setupIncomplete) {
                navigation.open(nextSetupRoute(state, env.notificationPermissionEnabled))
            } else {
                navigation.reset(AppRoute.HOME)
            }
            return
        }
        coroutineScope.launch { openUnencryptedBudget(selected, switchingBudget, previousSelection) }
    }

    private suspend fun openUnencryptedBudget(
        selected: BudgetCatalogItem,
        switchingBudget: Boolean,
        previousSelection: BudgetSelectionSnapshot,
    ) {
        val isCached =
            !selected.isEncrypted &&
                (
                    mobileSession.cachedBudgets.any { it.budgetId == selected.budgetId } ||
                        mobileSession.hasCachedBudgetSnapshot(selected.budgetId)
                )
        when {
            isCached -> {
                if (switchingBudget) state.budgetSwitchSplashId = selected.budgetId
                state.pendingBudgetSelectionOrigin = null
                val previousRoute = shell.backStack.getOrNull(shell.backStack.lastIndex - 1)?.route
                if (previousRoute?.isTab() == true) {
                    navigation.back()
                } else {
                    navigation.reset(AppRoute.HOME)
                }
            }
            !selected.isEncrypted || state.encryptedPassword.isNotBlank() -> {
                state.pendingBudgetSelectionOrigin = previousSelection
                navigation.open(AppRoute.FIRST_SYNC)
            }
        }
    }

    fun prepareBudgetUnlock(selected: BudgetCatalogItem) {
        state.pendingBudgetSelectionOrigin =
            BudgetSelectionSnapshot(
                budgetId = state.selectedBudgetId,
                name = state.selectedBudget,
                isEncrypted = state.selectedBudgetEncrypted,
            )
        state.selectedBudget = selected.name
        state.selectedBudgetId = selected.budgetId
        state.selectedBudgetEncrypted = selected.isEncrypted
        state.encryptedPassword = ""
        navigation.open(AppRoute.BUDGET_UNLOCK)
    }
}

/** Finishes an OpenID sign-in when the browser hands the auth callback back to the app. */
@Composable
internal fun OpenIdReturnEffect(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    LaunchedEffect(mobileSession, state.serverUrl) {
        AuthReturnEvents.uri.collect { returnUri ->
            if (returnUri != null) {
                AuthReturnEvents.clear(returnUri)
                val callback = actualOpenIdReturn(returnUri)
                if (callback != null) {
                    if (mobileSession.completeActualOpenIdSignIn(state.serverUrl, callback.code, callback.state)) {
                        state.recentServerUrls = rememberRecentServerAddress(state.recentServerUrls, state.serverUrl)
                        navigation.reset(AppRoute.BUDGET_CHOOSER)
                    }
                }
            }
        }
    }
}
