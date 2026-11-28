package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.AuthenticatedDevice
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.clearAllLocalData
import app.tandemforactual.data.store.hasAnyLocalBudgetData
import app.tandemforactual.data.store.hasUnresolvedWrites
import app.tandemforactual.ui.core.session.budget.restoreBudgetReorderQueue
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal suspend fun TandemMobileSession.restoreStoredSession(): String? {
    val store = credentialStore ?: return null
    if (isRestoringStoredSession) return null
    isRestoringStoredSession = true
    try {
        credentialStorageError = null
        val storedSession =
            try {
                withContext(Dispatchers.Default) { store.read() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                credentialStorageError = "Couldn’t access this device’s saved sign-in. Try again or sign in again."
                return null
            } ?: return null

        val restoredDevice =
            try {
                apiClient.restoreStoredSession(storedSession)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                apiClient.forgetSession()
                authenticatedDevice = null
                budgets = emptyList()
                credentialStorageError = "This device’s saved access no longer works. Sign in again."
                clearStoredCredential()
                return null
            }
        if (!acceptAuthenticatedIdentity(restoredDevice, previousSession = null, restoringSavedSession = true)) {
            credentialStorageError = signInError
            return null
        }
        isCredentialStored = true
        loadCachedBudgets()
        return apiClient.currentServerUrl.takeIf { apiClient.isAuthenticated }
    } finally {
        isRestoringStoredSession = false
    }
}

/** Reads the server's sign-in options, first switching to [headers] when given, as typed on the server address step. */
internal suspend fun TandemMobileSession.loadActualLoginMethods(
    serverUrl: String,
    headers: Map<String, String>? = null,
) {
    headers?.let { apiClient.customHeaders = it }
    actualLoginMethods = null
    isLoadingLoginMethods = true
    signInError = null
    try {
        actualLoginMethods = apiClient.readActualLoginMethods(serverUrl)
        isServerReachable = true
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        isServerReachable = error.indicatesTandemReachable()
        signInError =
            error.userFacingMessage(
                "Couldn’t check sign-in options. Check the address and try again.",
                ApiFailureContext.SIGN_IN,
            )
    } catch (_: Exception) {
        isServerReachable = false
        signInError =
            apiFailureMessage(
                "network-error",
                null,
                ApiFailureContext.SIGN_IN,
                "Couldn’t check sign-in options. Check the address and try again.",
            )
    } finally {
        isLoadingLoginMethods = false
    }
}

internal fun TandemMobileSession.clearActualLoginMethodState() {
    actualLoginMethods = null
    signInError = null
    isLoadingLoginMethods = false
    credentialStorageError = null
}

internal suspend fun TandemMobileSession.signInWithPassword(
    serverUrl: String,
    password: String,
    deviceName: String,
): Boolean {
    signInError = null
    signOutNotice = null
    deviceRevocationError = null
    isSigningIn = true
    cachedBudgets = emptyList()
    openIdLoginProofState.clear()
    val previousSession = apiClient.exportStoredSession()
    return try {
        val signedInDevice = apiClient.signInWithPassword(serverUrl, password, deviceName)
        if (!acceptAuthenticatedIdentity(signedInDevice, previousSession)) return false
        isServerReachable = true
        persistCurrentSession()
        loadBudgets()
        budgetQueue.retryQueuedBudgetReorders()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        isServerReachable = error.indicatesTandemReachable()
        signInError =
            error.userFacingMessage(
                "Couldn’t sign in. Check the address and try again.",
                ApiFailureContext.SIGN_IN,
            )
        restorePreviousSessionAfterSignIn(previousSession)
        false
    } catch (_: Exception) {
        isServerReachable = false
        signInError =
            apiFailureMessage(
                "network-error",
                null,
                ApiFailureContext.SIGN_IN,
                "Couldn’t sign in. Check the address and try again.",
            )
        restorePreviousSessionAfterSignIn(previousSession)
        false
    } finally {
        isSigningIn = false
        if (apiClient.isAuthenticated) loadCachedBudgets()
    }
}

internal suspend fun TandemMobileSession.startActualOpenIdSignIn(
    serverUrl: String,
    deviceName: String,
): String? {
    signInError = null
    signOutNotice = null
    isSigningIn = true
    openIdLoginProofState.clear()
    var redirectReady = false
    try {
        val requestProof = openIdLoginProofState.begin(serverUrl)
        scheduleOpenIdProofExpiry()
        val redirect =
            apiClient.startActualOpenIdSignIn(
                serverUrl,
                deviceName,
                requestProof.codeChallenge,
                requestProof.callbackState,
            )
        redirectReady = true
        return redirect
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        isServerReachable = error.indicatesTandemReachable()
        signInError = error.userFacingMessage("Tandem couldn’t start sign-in. Check the server address.", ApiFailureContext.SIGN_IN)
    } catch (_: Exception) {
        isServerReachable = false
        signInError =
            apiFailureMessage(
                "network-error",
                null,
                ApiFailureContext.SIGN_IN,
                "Tandem couldn’t start sign-in. Check the server address.",
            )
    } finally {
        isSigningIn = false
        if (!redirectReady) openIdLoginProofState.clear()
    }
    return null
}

internal fun TandemMobileSession.reportSignInError(message: String) {
    signInError = message
}

internal fun TandemMobileSession.cancelActualOpenIdSignIn() {
    openIdLoginProofState.clear()
}

internal fun TandemMobileSession.scheduleOpenIdProofExpiry() {
    val remainingTtlMillis = openIdLoginProofState.remainingTtlMillis()
    if (remainingTtlMillis > 0) {
        sessionScope.launch {
            delay(remainingTtlMillis)
            openIdLoginProofState.expireIfNeeded()
        }
    }
}

internal suspend fun TandemMobileSession.completeActualOpenIdSignIn(
    serverUrl: String,
    code: String,
    callbackState: String,
): Boolean {
    signInError = null
    deviceRevocationError = null
    isSigningIn = true
    val previousSession = apiClient.exportStoredSession()
    val codeVerifier = openIdLoginProofState.takeVerifierForCallback(serverUrl, callbackState)
    if (codeVerifier == null) {
        signInError = "This sign-in is no longer active. Start sign-in again."
        isSigningIn = false
        return false
    }
    cachedBudgets = emptyList()
    return try {
        val signedInDevice = apiClient.completeActualOpenIdSignIn(serverUrl, code, codeVerifier)
        openIdLoginProofState.clear()
        if (!acceptAuthenticatedIdentity(signedInDevice, previousSession)) return false
        isServerReachable = true
        persistCurrentSession()
        loadBudgets()
        budgetQueue.retryQueuedBudgetReorders()
        true
    } catch (error: CancellationException) {
        openIdLoginProofState.clear()
        throw error
    } catch (error: TandemApiException) {
        isServerReachable = error.indicatesTandemReachable()
        signInError =
            error.userFacingMessage(
                "Tandem couldn’t finish sign-in. Check the server address.",
                ApiFailureContext.SIGN_IN,
            )
        restorePreviousSessionAfterSignIn(previousSession)
        false
    } catch (_: Exception) {
        isServerReachable = false
        signInError =
            apiFailureMessage(
                "network-error",
                null,
                ApiFailureContext.SIGN_IN,
                "Tandem couldn’t finish sign-in. Check the server address.",
            )
        restorePreviousSessionAfterSignIn(previousSession)
        false
    } finally {
        isSigningIn = false
        openIdLoginProofState.clear()
        if (apiClient.isAuthenticated) loadCachedBudgets()
    }
}

/** Saves local cache ownership before any sync can expose another account's records. */
internal suspend fun TandemMobileSession.acceptAuthenticatedIdentity(
    device: AuthenticatedDevice,
    previousSession: String?,
    restoringSavedSession: Boolean = false,
): Boolean {
    authenticatedDevice = device
    val store = preferencesStore ?: return true
    val serverUrl = apiClient.currentServerUrl
    if (serverUrl.isNullOrBlank() || !apiClient.isAuthenticated) {
        restorePreviousSessionAfterSignIn(previousSession)
        signInError = "Couldn’t verify this sign-in. Try again."
        return false
    }
    if (!budgetQueue.restoreBudgetReorderQueue()) {
        restorePreviousSessionAfterSignIn(previousSession)
        signInError = "Couldn’t check pending changes. Try again before switching accounts."
        return false
    }

    val owner = budgetCacheOwner(serverUrl, device.principalId)
    val savedPreferences = store.read()
    val savedOwner = savedPreferences[BUDGET_CACHE_OWNER_KEY]?.takeIf(String::isNotBlank)
    val hasAnyLocalBudgetData = repository?.hasAnyLocalBudgetData() == true
    val hasPendingBudgetChanges = budgetQueue.budgetReorderQueue.moves.isNotEmpty()
    val hasUnresolvedWrites = repository?.hasUnresolvedWrites() == true
    val changingOwner = savedOwner != null && savedOwner != owner
    val ownerUnknownWithLocalData =
        !restoringSavedSession &&
            savedOwner == null &&
            (hasAnyLocalBudgetData || hasPendingBudgetChanges || hasUnresolvedWrites)

    if ((changingOwner || ownerUnknownWithLocalData) && (hasPendingBudgetChanges || hasUnresolvedWrites)) {
        restorePreviousSessionAfterSignIn(previousSession)
        signInError =
            "Pending changes belong to another server or account. Sign in there and sync or resolve them before switching."
        return false
    }

    if (changingOwner || ownerUnknownWithLocalData) {
        budgetQueue.budgetQueuePersistenceMutex.withLock {
            budgetQueue.budgetReorderQueueState = BudgetReorderQueueState()
            budgetQueue.budgetQueueLoaded = true
            repository?.clearAllLocalData()
            store.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
        }
        clearCachedBudgetPresentation()
    }
    store.putValue(BUDGET_CACHE_OWNER_KEY, owner)
    return true
}

internal suspend fun TandemMobileSession.restorePreviousSessionAfterSignIn(previousSession: String?) {
    if (previousSession.isNullOrBlank()) {
        apiClient.forgetSession()
        authenticatedDevice = null
        return
    }
    try {
        authenticatedDevice = apiClient.restoreStoredSession(previousSession)
    } catch (_: Exception) {
        apiClient.forgetSession()
        authenticatedDevice = null
    }
}

internal suspend fun TandemMobileSession.persistCurrentSession() {
    val store = credentialStore ?: return
    val storedSession = apiClient.exportStoredSession() ?: return
    credentialStorageError = null
    try {
        withContext(Dispatchers.Default) { store.write(storedSession) }
        isCredentialStored = true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        isCredentialStored = false
        credentialStorageError = "This sign-in will end when the app closes because saved access is unavailable."
    }
}

internal suspend fun TandemMobileSession.clearStoredCredential() {
    val store = credentialStore ?: return
    try {
        withContext(Dispatchers.Default) { store.clear() }
        isCredentialStored = false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        credentialStorageError = "Couldn’t remove this device’s saved access."
    }
}
