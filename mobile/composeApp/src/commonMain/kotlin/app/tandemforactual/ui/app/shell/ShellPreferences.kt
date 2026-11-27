@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.automation.AutomationAccess
import app.tandemforactual.ui.core.format.currentMonthId
import app.tandemforactual.ui.core.format.currentMonthLabel
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.encodeCachedBudgetCatalog
import app.tandemforactual.ui.core.watch.WatchPairing
import app.tandemforactual.ui.core.watch.WatchSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores and saves app settings, and reports background sync and budget catalog changes to the host. */
@Composable
internal fun AppSettingsEffects(
    env: ShellEnvironment,
    state: AppState,
    mobileSession: TandemMobileSession,
    appPreferencesStore: AppPreferencesStore?,
) {
    LaunchedEffect(appPreferencesStore) {
        val store = appPreferencesStore
        if (store == null) {
            state.settingsStorageReady = true
            return@LaunchedEffect
        }
        persistAppSettings(store, state)
    }
    LaunchedEffect(
        state.settingsStorageReady,
        state.backgroundSyncEnabled,
        state.syncNetwork,
        state.selectedBudgetId,
        env.onBackgroundSyncSettingsChanged,
    ) {
        if (state.settingsStorageReady) {
            env.onBackgroundSyncSettingsChanged?.invoke(
                state.backgroundSyncEnabled,
                state.syncNetwork,
                state.selectedBudgetId,
            )
        }
    }
    LaunchedEffect(state.settingsStorageReady, state.automaticBackup.network, env.onAutomaticBackupNetworkChanged) {
        if (state.settingsStorageReady) env.onAutomaticBackupNetworkChanged?.invoke(state.automaticBackup.network)
    }
    LaunchedEffect(mobileSession.budgetCatalogVersion) {
        if (mobileSession.budgetCatalogVersion > 0) {
            state.cachedBudgetCatalogJson = encodeCachedBudgetCatalog(mobileSession.budgets)
        }
    }
}

private suspend fun CoroutineScope.persistAppSettings(
    store: AppPreferencesStore,
    state: AppState,
) {
    val initialSettings = state.persistentSettings()
    val localChanges = mutableMapOf<String, String>()
    val localSettingsObserver =
        launch {
            snapshotFlow { state.persistentSettings() }
                .collect { currentSettings ->
                    currentSettings
                        .filter { (name, value) -> initialSettings[name] != value }
                        .forEach { (name, value) -> localChanges[name] = value }
                }
        }
    var storedPreferences: Map<String, String>? = null
    while (storedPreferences == null) {
        val retryAtRead = state.settingsStorageRetry
        try {
            storedPreferences = store.read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            state.settingsStorageError = "Couldn’t load app settings. Try again."
            snapshotFlow { state.settingsStorageRetry }.first { it != retryAtRead }
        }
    }
    state
        .persistentSettings()
        .filter { (name, value) -> initialSettings[name] != value }
        .forEach { (name, value) -> localChanges[name] = value }
    localSettingsObserver.cancel()
    state.restorePersistentSettings(storedPreferences + localChanges)
    state.selectedMonthId = currentMonthId()
    state.selectedMonth = currentMonthLabel()
    state.reportsMonthId = state.selectedMonthId
    state.settingsStorageReady = true
    saveAppSettingsChanges(store, state, if (localChanges.isEmpty()) state.persistentSettings() else emptyMap())
}

/** Settings that change what a paired watch may show, so a change sends it a fresh summary or clears it. */
private val WATCH_KEYS =
    listOf(
        "selectedBudgetId",
        "selectedBudget",
        "selectedBudgetEncrypted",
        "privacyModeEnabled",
        "readOnlyMode",
        "pinnedCategoryIds",
        AutomationAccess.READS,
    ) + WatchSettings().toPreferences().keys

private suspend fun saveAppSettingsChanges(
    store: AppPreferencesStore,
    state: AppState,
    savedPreferences: Map<String, String>,
) {
    var lastSavedPreferences = savedPreferences
    var lastAttemptedRetry = state.settingsStorageRetry
    snapshotFlow { state.persistentSettings() to state.settingsStorageRetry }
        .collect { (updatedPreferences, retryCount) ->
            if (updatedPreferences == lastSavedPreferences && retryCount == lastAttemptedRetry) return@collect
            val retryRequested = retryCount != lastAttemptedRetry
            lastAttemptedRetry = retryCount
            try {
                store.save(updatedPreferences)
                if (WATCH_KEYS.any { updatedPreferences[it] != lastSavedPreferences[it] }) WatchPairing.refresh()
                lastSavedPreferences = updatedPreferences
                state.settingsStorageError = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.settingsStorageError =
                    if (retryRequested) {
                        "App settings still couldn’t be saved. Check local storage and try again."
                    } else {
                        "Couldn’t save app settings. Try again."
                    }
            }
        }
}
