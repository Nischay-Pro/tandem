package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.session.budget.restoreBudgetReorderQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal suspend fun TandemMobileSession.forgetDevice() {
    if (authenticatedDevice == null || isRevokingDevice) return
    deviceRevocationError = null
    isRevokingDevice = true
    try {
        try {
            apiClient.revokeCurrentDevice()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            if (error.httpStatus != 401 && error.httpStatus != 404) {
                deviceRevocationError = "Check your connection and try again."
                return
            }
        } catch (_: Exception) {
            deviceRevocationError = "Check your connection and try again."
            return
        }
        clearDeviceSession()
    } finally {
        isRevokingDevice = false
    }
}

internal suspend fun TandemMobileSession.forgetDeviceLocally() {
    if (authenticatedDevice == null || isRevokingDevice) return
    deviceRevocationError = null
    clearDeviceSession()
}

internal suspend fun TandemMobileSession.signOutAndClearLocalData(): Boolean {
    if (isSigningOut) return false
    isSigningOut = true
    signOutError = null
    signOutNotice = null
    var cacheTablesCleared = false
    try {
        signOutBlocker()?.let {
            signOutError = it
            return false
        }
        cacheTablesCleared = repository == null
        val store = credentialStore
        val storedCredential =
            try {
                store?.let { withContext(Dispatchers.Default) { it.read() } }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                signOutError = "Couldn’t check this device’s saved sign-in. Try again."
                return false
            }
        val deviceRevoked = revokeDeviceForSignOut()

        try {
            if (store != null) withContext(Dispatchers.Default) { store.clear() }
            isCredentialStored = false
            credentialStorageError = null
        } catch (error: CancellationException) {
            withContext(NonCancellable) { restoreSavedCredential(store, storedCredential) }
            throw error
        } catch (_: Exception) {
            val restored = restoreSavedCredential(store, storedCredential)
            signOutError =
                if (storedCredential != null && !restored) {
                    "Couldn’t remove or restore this device’s saved sign-in. Your cached budgets are still here."
                } else {
                    "Couldn’t remove this device’s saved access. Try again."
                }
            return false
        }

        try {
            budgetQueue.budgetQueuePersistenceMutex.withLock {
                repository?.clearAllLocalData()
                cacheTablesCleared = true
                preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
                preferencesStore?.putValue(BUDGET_CACHE_OWNER_KEY, "")
                budgetQueue.budgetReorderQueueState = BudgetReorderQueueState()
                budgetQueue.budgetQueueLoaded = true
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) { reportLocalPurgeFailure(store, storedCredential, cacheTablesCleared, deviceRevoked) }
            throw error
        } catch (_: Exception) {
            reportLocalPurgeFailure(store, storedCredential, cacheTablesCleared, deviceRevoked)
            return false
        }
        clearSignedOutState()
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        signOutError =
            if (cacheTablesCleared) {
                "Cached budgets were cleared, but sign-out couldn’t finish. Try again."
            } else {
                "Couldn’t clear this device’s cached budgets. Try again."
            }
        return false
    } finally {
        isSigningOut = false
    }
}

private suspend fun TandemMobileSession.signOutBlocker(): String? {
    if (isSyncing || isSavingTransaction || isPerformingBudgetAction || isLoadingBudgets) {
        return "Wait for the current sync or change to finish before signing out."
    }
    if (!budgetQueue.restoreBudgetReorderQueue()) {
        return "Couldn’t verify pending changes. Try again before signing out."
    }
    if (budgetQueue.budgetReorderQueue.moves.isNotEmpty()) {
        return "Sync or resolve pending changes before signing out."
    }
    if (repository?.hasUnresolvedWrites() == true) {
        return "Sync or resolve pending changes before signing out."
    }
    return null
}

private suspend fun TandemMobileSession.revokeDeviceForSignOut(): Boolean {
    var deviceRevoked = false
    if (apiClient.isAuthenticated) {
        try {
            revokeCurrentDeviceForSignOut()
            deviceRevoked = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            if (error.httpStatus == 401 || error.httpStatus == 404) {
                deviceRevoked = true
            } else {
                signOutNotice = signOutRevocationNotice(error.httpStatus)
            }
        } catch (_: Exception) {
            signOutNotice = signOutRevocationNotice(httpStatus = null)
        }
    }
    return deviceRevoked
}

private suspend fun TandemMobileSession.restoreSavedCredential(
    store: DeviceCredentialStore?,
    storedCredential: String?,
): Boolean {
    if (store == null || storedCredential == null) {
        isCredentialStored = false
        return true
    }
    return try {
        withContext(NonCancellable + Dispatchers.Default) {
            store.write(storedCredential)
        }
        isCredentialStored = true
        credentialStorageError = null
        true
    } catch (_: Exception) {
        isCredentialStored = false
        credentialStorageError = "Couldn’t restore this device’s saved sign-in."
        false
    }
}

private suspend fun TandemMobileSession.reportLocalPurgeFailure(
    store: DeviceCredentialStore?,
    storedCredential: String?,
    cacheTablesCleared: Boolean,
    deviceRevoked: Boolean,
) {
    val restored = restoreSavedCredential(store, storedCredential)
    signOutError =
        when {
            cacheTablesCleared && storedCredential != null && !restored ->
                "Cached budgets were cleared, but this device’s saved sign-in couldn’t be restored. Sign in again to continue."
            cacheTablesCleared ->
                "Cached budgets were cleared, but sign-out couldn’t finish. Try again."
            storedCredential != null && !restored ->
                "Couldn’t clear this device’s cached budgets or restore its saved sign-in. Your cached budgets are still here; sign in again to continue."
            deviceRevoked ->
                "Couldn’t clear this device’s cached budgets. They’re still here; the server sign-in was revoked, so sign in again to sync."
            storedCredential != null ->
                "Couldn’t clear this device’s cached budgets. They’re still here and saved sign-in was restored; try again."
            else ->
                "Couldn’t clear this device’s cached budgets. They’re still here; try again."
        }
}

private suspend fun TandemMobileSession.clearSignedOutState() {
    openIdLoginProofState.clear()
    apiClient.forgetSession()
    authenticatedDevice = null
    budgets = emptyList()
    cachedBudgets = emptyList()
    budgetCatalogLoaded = false
    budgetCatalogVersion += 1
    isServerReachable = false
    hasCachedSnapshot = false
    cachedSnapshotBudgetId = ""
    isCredentialStored = false
    credentialStorageError = null
    deviceRevocationError = null
    budgetError = null
    isLoadingBudgets = false
    localSyncState = null
    budgetDisplaySettings = null
    budgetDisplaySettingsBudgetId = null
    transactionRecords = emptyList()
    activity.reset()
    activitySearchRecords = emptyList()
    activitySearchRecordsBudgetId = ""
    payeeRecords = emptyList()
    categoryRecords = emptyList()
    categoryGroupRecords = emptyList()
    budgetMonthIds = emptyList()
    selectedBudgetMonthRecord = null
    isLoadingSelectedBudgetMonth = false
    selectedBudgetMonthObservationKey = ""
    accountRecords = emptyList()
    accountGroupRecords = emptyList()
    scheduleRecords = emptyList()
    ruleRecords = emptyList()
    pendingWrites = emptyList()
    conflictRecordsByRecordId = emptyMap()
    cachedTransactionCount = 0
    syncedTransactionCount = 0
    conflicts.reset()
    transactionWriteMessage = null
    syncComplete = false
    lastSyncSummary = null
    syncPresentation = SyncPresentationState()
}

internal suspend fun TandemMobileSession.clearDeviceSession() {
    openIdLoginProofState.clear()
    apiClient.forgetSession()
    authenticatedDevice = null
    budgets = emptyList()
    budgetCatalogLoaded = false
    isServerReachable = false
    budgetError = null
    clearStoredCredential()
}
