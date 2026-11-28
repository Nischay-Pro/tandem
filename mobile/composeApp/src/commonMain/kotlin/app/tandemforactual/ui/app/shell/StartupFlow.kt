@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.app.navigationRoutes
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.LocalLanAccess
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
import app.tandemforactual.ui.core.state.startPage
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
    val lanAccess = LocalLanAccess.current
    LaunchedEffect(shell.sessionRestoreComplete, mobileSession.isAuthenticated, state.localServerUrl, state.automaticBackup.webDavUrl) {
        if (lanAccess == null || !shell.sessionRestoreComplete || !mobileSession.isAuthenticated) return@LaunchedEffect
        listOfNotNull(state.serverUrl, state.localServerUrl, state.automaticBackup.webDavUrl)
            .filter(String::isNotBlank)
            .forEach { lanAccess.request(it) }
    }
    LaunchedEffect(state.selectedBudgetId, state.selectedBudgetEncrypted) {
        val keyFor = BackgroundBudgetKey.budgetIn(env.backgroundBudgetKeyStore)
        if (state.selectedBudgetEncrypted && keyFor != state.selectedBudgetId) state.backgroundSyncEnabled = false
    }
    LaunchedEffect(state.selectedBudgetId, state.encryptedPassword) {
        if (keptPasswordChanged(env.backgroundBudgetKeyStore, state.selectedBudgetId, state.encryptedPassword)) {
            state.backgroundSyncEnabled = false
        }
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
                backStack.add(navigator.newRouteEntry(startPage(state.startPageName, navigationRoutes(state.interfaceMode))))
            } else if (firstCachedBudget != null) {
                state.selectedBudget = cachedBudgetName(state.selectedBudgetId, state.selectedBudget, firstCachedBudget)
                state.selectedBudgetId = firstCachedBudget.budgetId
                state.selectedBudgetEncrypted = firstCachedBudget.isEncrypted
                backStack.clear()
                backStack.add(navigator.newRouteEntry(startPage(state.startPageName, navigationRoutes(state.interfaceMode))))
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

/** Forgets a kept budget password that no longer matches the one just used to unlock, because it changed in Actual. */
internal fun keptPasswordChanged(
    store: DeviceCredentialStore?,
    budgetId: String,
    password: String,
): Boolean {
    if (store == null || password.isBlank()) return false
    val kept = BackgroundBudgetKey.decode(runCatching { store.read() }.getOrNull()) ?: return false
    if (kept.first != budgetId || kept.second == password) return false
    return runCatching { store.clear() }.isSuccess
}
