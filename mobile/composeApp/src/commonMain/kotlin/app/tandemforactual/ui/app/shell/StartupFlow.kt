@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.OpenIdLoginProofState
import app.tandemforactual.ui.core.session.PlanningKeyring
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.loadCachedBudgets
import app.tandemforactual.ui.core.session.restoreStoredSession
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppStartupDestination
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.cachedBudgetName
import app.tandemforactual.ui.core.state.cachedBudgetsWithLocalSnapshots
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.core.state.resolveAppStartupDestination
import app.tandemforactual.ui.feature.onboarding.cleanServerAddress

/** Creates the API client and the mobile session the whole app shares. */
@Composable
internal fun rememberShellSession(env: ShellEnvironment): TandemMobileSession {
    val apiClient =
        remember(env.allowInsecurePrivateNetwork, env.actualBudgetFiles) {
            TandemApiClient(env.allowInsecurePrivateNetwork, budgetFiles = env.actualBudgetFiles, today = ::platformTodayIsoDate)
        }
    val openIdLoginProofState =
        rememberSaveable(saver = OpenIdLoginProofState.saver(env.nowEpochMillis)) {
            OpenIdLoginProofState(env.nowEpochMillis)
        }
    return remember(apiClient, env.database, env.deviceCredentialStore, openIdLoginProofState) {
        TandemMobileSession(
            apiClient,
            env.database,
            env.nowEpochMillis,
            env.deviceCredentialStore,
            openIdLoginProofState = openIdLoginProofState,
        ).apply { planning.keyring = PlanningKeyring(env.planningKeyStore) }
    }
}

/** Loads cached budgets and the stored sign-in, then leaves the start-up route once both are known. */
@Composable
internal fun StartupEffects(
    env: ShellEnvironment,
    state: AppState,
    shell: AppShellState,
    navigator: ShellNavigator,
    mobileSession: TandemMobileSession,
) {
    LaunchedEffect(mobileSession) {
        try {
            mobileSession.loadCachedBudgets()
        } finally {
            shell.cacheDiscoveryComplete = true
        }
    }
    LaunchedEffect(mobileSession) {
        try {
            val storedServerUrl = mobileSession.restoreStoredSession()
            if (storedServerUrl != null) state.serverUrl = cleanServerAddress(storedServerUrl)
        } finally {
            shell.sessionRestoreComplete = true
        }
    }
    LaunchedEffect(state.selectedBudgetId, state.selectedBudgetEncrypted) {
        if (state.selectedBudgetEncrypted) state.backgroundSyncEnabled = false
    }
    LaunchedEffect(
        shell.cacheDiscoveryComplete,
        mobileSession.cachedBudgets,
        state.settingsStorageReady,
        state.settingsStorageError,
        state.selectedBudgetId,
        state.cachedBudgetCatalogJson,
        shell.sessionRestoreComplete,
        mobileSession.isAuthenticated,
    ) {
        if (shell.backStack.lastOrNull()?.route != AppRoute.STARTUP) return@LaunchedEffect
        leaveStartupRoute(env, state, shell, navigator, mobileSession)
    }
}

private fun leaveStartupRoute(
    env: ShellEnvironment,
    state: AppState,
    shell: AppShellState,
    navigator: ShellNavigator,
    mobileSession: TandemMobileSession,
) {
    val backStack = shell.backStack
    val hasEncryptedStore =
        state.selectedBudgetId.isNotBlank() && env.encryptedBudgetVault?.hasStore(state.selectedBudgetId) == true
    val destination =
        resolveAppStartupDestination(
            cacheDiscoveryComplete = shell.cacheDiscoveryComplete,
            hasCachedBudget = mobileSession.cachedBudgets.isNotEmpty() || hasEncryptedStore,
            preferencesReady = state.settingsStorageReady,
            preferencesFailed = state.settingsStorageError != null,
            sessionRestoreComplete = shell.sessionRestoreComplete,
            isAuthenticated = mobileSession.isAuthenticated,
        )
    when (destination) {
        AppStartupDestination.WAIT -> Unit
        AppStartupDestination.CACHED_BUDGET -> {
            val localCachedBudgets = mobileSession.cachedBudgets
            val availableCachedBudgets =
                cachedBudgetsWithLocalSnapshots(
                    decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                    localCachedBudgets,
                )
            val selectedCachedBudget = availableCachedBudgets.firstOrNull { it.budgetId == state.selectedBudgetId }
            val firstCachedBudget = selectedCachedBudget ?: availableCachedBudgets.firstOrNull()
            if (hasEncryptedStore) {
                state.selectedBudgetEncrypted = true
                backStack.clear()
                backStack.add(navigator.newRouteEntry(AppRoute.HOME))
            } else if (firstCachedBudget != null) {
                state.selectedBudget = cachedBudgetName(state.selectedBudgetId, state.selectedBudget, firstCachedBudget)
                state.selectedBudgetId = firstCachedBudget.budgetId
                state.selectedBudgetEncrypted = firstCachedBudget.isEncrypted
                backStack.clear()
                backStack.add(navigator.newRouteEntry(AppRoute.HOME))
            }
        }
        AppStartupDestination.BUDGET_CHOOSER -> {
            backStack.clear()
            backStack.add(navigator.newRouteEntry(AppRoute.BUDGET_CHOOSER))
        }
        AppStartupDestination.WELCOME -> {
            backStack.clear()
            backStack.add(navigator.newRouteEntry(AppRoute.WELCOME))
        }
    }
}
