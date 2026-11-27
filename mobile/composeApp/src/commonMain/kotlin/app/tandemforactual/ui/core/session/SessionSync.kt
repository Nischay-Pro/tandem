package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.countVisibleRecords
import app.tandemforactual.data.store.findCachedBudgetIds
import app.tandemforactual.data.store.hasCachedSnapshot
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.data.store.readSyncState
import app.tandemforactual.data.store.retryUncertainWrite
import app.tandemforactual.ui.core.session.budget.hasPendingTransactionDependency
import app.tandemforactual.ui.core.session.budget.restoreBudgetReorderQueue
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun TandemMobileSession.synchronizeSerialized(
    budgetId: String,
    encryptionPassword: String?,
    sendQueuedWrites: Boolean,
    reconcileUncertainOperationId: String?,
    retryQueuedBudgetMutations: Boolean,
    background: Boolean,
): BudgetSyncSummary? {
    if (isSigningOut) return null
    if (encryptedStorageMissing(budgetId)) return null
    val boundStorage = storage
    if (boundStorage == null) {
        presentSync(background, syncPresentation.failed("Tandem can’t save this budget on your device. Restart the app and try again."))
        return null
    }
    if (boundStorage.budgetId.let { it != null && it != budgetId }) {
        presentSync(background, syncPresentation.failed("Open this budget again before syncing it."))
        return null
    }

    if (!encryptionPassword.isNullOrBlank()) scheduleActionEncryptionPasswords[budgetId] = encryptionPassword
    if (!isCurrentIdentityCacheOwner()) {
        presentSync(
            background,
            syncPresentation.failed(
                "These changes belong to another server or account. Sign in there before syncing them.",
                IDENTITY_MISMATCH_ERROR_CODE,
            ),
        )
        return null
    }
    val budgetQueueRestored = budgetQueue.restoreBudgetReorderQueue()
    if (budgetQueueRestored && retryQueuedBudgetMutations && !encryptionPassword.isNullOrBlank()) {
        budgetQueue.retryQueuedBudgetReorders(budgetId, encryptionPassword)
    }
    if (!background) {
        syncComplete = false
        syncedTransactionCount = 0
        lastSyncSummary = null
    }
    val coordinator = boundStorage.coordinator
    val localRepository = boundStorage.repository

    // Keep an earlier error visible while retrying; clear it only when the retry succeeds.
    presentSync(background, syncPresentation.begin())
    try {
        val summary =
            apiClient.withExclusiveSession {
                val sendWrites = sendQueuedWrites && !budgetQueue.hasPendingTransactionDependency(budgetId)
                withContext(Dispatchers.Default) {
                    coordinator.synchronize(budgetId, encryptionPassword, sendWrites, reconcileUncertainOperationId)
                }
            }
        return finishSuccessfulSync(
            budgetId,
            encryptionPassword,
            summary,
            localRepository,
            budgetQueueRestored,
            retryQueuedBudgetMutations,
            background,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: BudgetSyncRemoteException) {
        reportRemoteSyncFailure(background, error)
        return null
    } catch (error: TandemApiException) {
        reportApiSyncFailure(background, error)
        return null
    } catch (error: BudgetSyncProtocolException) {
        isServerReachable = true
        presentSync(background, syncPresentation.failed("Tandem couldn’t read this budget. Try syncing again."))
        return null
    } catch (error: Exception) {
        reportUnexpectedSyncFailure(background)
        return null
    } finally {
        if (syncPresentation.isSyncing) syncPresentation = syncPresentation.stopped()
    }
}

private fun TandemMobileSession.presentSync(
    background: Boolean,
    next: SyncPresentationState,
) {
    if (!background || (next.error == null && !next.isSyncing && syncPresentation.error != null)) {
        syncPresentation = next
    }
}

private suspend fun TandemMobileSession.finishSuccessfulSync(
    budgetId: String,
    encryptionPassword: String?,
    summary: BudgetSyncSummary,
    localRepository: LocalBudgetRepository,
    budgetQueueRestored: Boolean,
    retryQueuedBudgetMutations: Boolean,
    background: Boolean,
): BudgetSyncSummary {
    isServerReachable = true
    refreshServerCapabilities()
    schedules.retryQueuedScheduleActions()
    lastSyncSummary = summary
    loadLocalRecords(budgetId)
    syncedTransactionCount =
        localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    syncComplete = true
    budgetPasswordRejection = null
    presentSync(background, syncPresentation.succeeded())
    if (budgetQueueRestored) {
        budgetQueue.budgetReorderQueue = budgetQueue.budgetReorderQueue.clearCompletedVisibilityOverrides(budgetId)
        if (retryQueuedBudgetMutations) budgetQueue.retryQueuedBudgetReorders(budgetId, encryptionPassword)
    }
    planning.sync(budgetId)
    return summary
}

private fun TandemMobileSession.reportRemoteSyncFailure(
    background: Boolean,
    error: BudgetSyncRemoteException,
) {
    isServerReachable = error.indicatesTandemReachable()
    budgetPasswordRejection = error.errorCode.takeIf { it in BUDGET_PASSWORD_ERROR_CODES }
    presentSync(
        background,
        syncPresentation.failed(
            apiFailureMessage(
                errorCode = error.errorCode,
                httpStatus = error.httpStatus,
                context = ApiFailureContext.SYNC,
                fallback = "Tandem couldn’t sync this budget. Try again shortly.",
            ),
            error.errorCode,
            error.httpStatus,
        ),
    )
}

private fun TandemMobileSession.reportApiSyncFailure(
    background: Boolean,
    error: TandemApiException,
) {
    isServerReachable = error.errorCode != "address_unreachable"
    budgetPasswordRejection = error.errorCode.takeIf { it in BUDGET_PASSWORD_ERROR_CODES }
    presentSync(
        background,
        syncPresentation.failed(
            apiFailureMessage(
                error.errorCode,
                error.httpStatus,
                ApiFailureContext.SYNC,
                "Tandem couldn’t sync this budget. Try again shortly.",
            ),
            error.errorCode,
            error.httpStatus,
        ),
    )
}

private fun TandemMobileSession.reportUnexpectedSyncFailure(background: Boolean) {
    isServerReachable = false
    if (apiClient.isUsingLocalServer) {
        apiClient.useExternalServerAfterLocalFailure()
        isUsingLocalServer = false
    }
    presentSync(
        background,
        syncPresentation.failed(
            apiFailureMessage(
                errorCode = "network-error",
                httpStatus = null,
                context = ApiFailureContext.SYNC,
                fallback = "Tandem couldn’t sync this budget. Your saved copy is still available.",
            ),
        ),
    )
}

/** Each budget's sync state on this device; an encrypted budget that isn't open keeps its state locked. */
internal suspend fun TandemMobileSession.budgetSyncOverview(openBudgetId: String): List<BudgetSyncOverview> {
    val main = mainStorage?.repository
    return budgets.map { budget ->
        val open = budget.budgetId == openBudgetId
        val writes = if (open) pendingWrites else main?.readPendingWrites(budget.budgetId).orEmpty()
        val sync = if (open) localSyncState else main?.readSyncState(budget.budgetId)
        BudgetSyncOverview(
            budgetId = budget.budgetId,
            name = budget.name,
            open = open,
            locked = budget.isEncrypted && !open,
            lastSyncedAtEpochMs = sync?.lastSyncedAtEpochMs,
            failed = sync?.lastErrorCode != null,
            waiting = writes.count { it.status == "queued" || it.status == "sending" },
            needsReview = writes.count { it.status == "uncertain" || it.status == "conflict" || it.status == "rejected" },
        )
    }
}

/** Sends unconfirmed edits again, then retries bank links that failed and planning sync that's waiting. */
internal suspend fun TandemMobileSession.retryFailedWork(
    budgetId: String,
    encryptionPassword: String?,
) {
    if (refuseWriteInReadOnlyMode()) return
    val localRepository = repository ?: return
    pendingWrites
        .filter { it.budgetId == budgetId && it.status == "uncertain" && it.recordType == "transaction" }
        .forEach { runCatching { localRepository.retryUncertainWrite(it.operationId) } }
    synchronize(budgetId, encryptionPassword)
    loadLocalRecords(budgetId)
    if (banking.bankLinks.any { it.failure != null }) banking.runBankSync(budgetId, encryptionPassword)
    if (planning.syncStatus.retryable) planning.sync(budgetId)
}

internal suspend fun TandemMobileSession.loadBudgets() {
    budgetError = null
    isLoadingBudgets = true
    try {
        budgets = apiClient.listBudgets()
        budgetCatalogLoaded = true
        budgetCatalogVersion += 1
        isServerReachable = true
        budgetQueue.retryQueuedBudgetReorders()
        schedules.retryQueuedScheduleActions()
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        isServerReachable = error.indicatesTandemReachable()
        budgetError = error.userFacingMessage("Tandem couldn’t load your budgets. Check the connection.")
        if (error.errorCode == "invalid_device_credential") {
            apiClient.forgetSession()
            authenticatedDevice = null
            budgets = emptyList()
            credentialStorageError = "Your saved sign-in is no longer active. Sign in again."
            clearStoredCredential()
        }
    } catch (error: Exception) {
        isServerReachable = false
        budgetError =
            apiFailureMessage(
                "network-error",
                null,
                ApiFailureContext.SIGN_IN,
                "Tandem couldn’t load your budgets. Check the server address.",
            )
    } finally {
        isLoadingBudgets = false
    }
}

internal suspend fun TandemMobileSession.loadCachedBudgets(): List<BudgetCatalogItem> {
    val localRepository = repository ?: return emptyList()
    if (isSigningIn || !isCurrentIdentityCacheOwner()) {
        cachedBudgets = emptyList()
        return emptyList()
    }
    val cached =
        localRepository.findCachedBudgetIds().map { budgetId ->
            val currencyCode = localRepository.readBudgetDisplaySettings(budgetId)?.currencyCode
            BudgetCatalogItem(
                budgetId = budgetId,
                name = currencyCode?.let { "Saved budget · $it" } ?: "Saved budget",
                isEncrypted = false,
            )
        }
    cachedBudgets = cached
    return cached
}

internal suspend fun TandemMobileSession.hasCachedBudgetSnapshot(budgetId: String): Boolean =
    budgetId.isNotBlank() &&
        budgetId == budgetId.trim() &&
        isCurrentIdentityCacheOwner() &&
        repository?.hasCachedSnapshot(budgetId) == true

internal suspend fun TandemMobileSession.refreshServerCapabilities(force: Boolean = false) {
    if (!force && nowEpochMillis() - capabilitiesCheckedAtEpochMs < CAPABILITIES_REFRESH_MILLIS) return
    val latest = apiClient.readServerCapabilities() ?: return
    capabilitiesCheckedAtEpochMs = nowEpochMillis()
    if (latest != serverCapabilities) {
        serverCapabilities = latest
        budgetQueue.budgetReorderRetrySignal += 1
    }
}

internal suspend fun TandemMobileSession.configureLocalServer(url: String?): Boolean {
    localServerError = null
    try {
        apiClient.setLocalServerUrl(url)
    } catch (_: TandemApiException) {
        localServerError = "Enter a local address like http://192.168.1.10:19753."
        isUsingLocalServer = false
        return false
    }
    isUsingLocalServer = !url.isNullOrBlank() && apiClient.refreshLocalServer()
    return isUsingLocalServer
}
