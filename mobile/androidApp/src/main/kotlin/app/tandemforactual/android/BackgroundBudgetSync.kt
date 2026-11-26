package app.tandemforactual.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tandemforactual.api.client.DirectBudgetSyncRemote
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.api.client.claimDueBankSync
import app.tandemforactual.api.client.runBudgetBankSync
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AlertKind
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.NotificationPolicy
import app.tandemforactual.data.store.largeTransactionAlert
import app.tandemforactual.data.store.overspendingAlert
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.uncategorizedAlert
import app.tandemforactual.data.store.upcomingBillsAlert
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import app.tandemforactual.ui.core.session.SyncNetwork
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal const val BACKGROUND_KEY_STORE = "background_budget_key"

internal object BackgroundBudgetSyncScheduler {
    const val UNIQUE_WORK_NAME = "tandem-selected-budget-sync"
    private const val INTERVAL_HOURS = 6L

    fun update(
        context: Context,
        enabled: Boolean,
        network: SyncNetwork,
        budgetId: String,
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled || budgetId.isBlank()) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest(network),
        )
    }

    internal fun periodicRequest(network: SyncNetwork): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<BackgroundBudgetSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(
                        when (network) {
                            SyncNetwork.WIFI -> NetworkType.UNMETERED
                            SyncNetwork.MOBILE_DATA -> NetworkType.METERED
                            SyncNetwork.ANY -> NetworkType.CONNECTED
                        },
                    ).build(),
            ).build()
}

class BackgroundBudgetSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database =
            try {
                openTandemDatabase(tandemDatabaseBuilder(applicationContext))
            } catch (_: Exception) {
                return Result.retry()
            }
        val credentialStore = AndroidDeviceCredentialStore(applicationContext)
        var apiClient: TandemApiClient? = null
        var notificationPreferences: Map<String, String>? = null
        val progress = BackgroundWorkProgress(applicationContext, BackgroundWork.SYNC)
        return try {
            val preferences = AppPreferencesStore(database).read()
            notificationPreferences = preferences
            if (preferences["backgroundSyncEnabled"] != "true") return Result.success()
            val budgetId = preferences["selectedBudgetId"]?.takeIf { it.isNotBlank() } ?: return Result.success()
            val storedSession = credentialStore.read() ?: return Result.success()
            val sessionClient =
                TandemApiClient(
                    allowInsecurePrivateNetwork = true,
                    budgetFiles =
                        app.tandemforactual.data.database
                            .AndroidActualBudgetFiles(applicationContext),
                )
            apiClient = sessionClient
            val authenticatedDevice = sessionClient.restoreStoredSession(storedSession)
            if (budgetId !in authenticatedDevice.budgetIds) return Result.success()
            val budget = sessionClient.listBudgets().firstOrNull { it.budgetId == budgetId } ?: return Result.success()
            val password = if (budget.isEncrypted) backgroundPassword(budgetId) ?: return Result.success() else null
            val budgetDatabase =
                if (password == null) database else AndroidEncryptedBudgetVault(applicationContext).open(budgetId, password)
            if (budgetDatabase == null) {
                AndroidDeviceCredentialStore(applicationContext, BACKGROUND_KEY_STORE).clear()
                return Result.success()
            }
            try {
                progress.show("Syncing your budget")
                val repository = LocalBudgetRepository(budgetDatabase) { System.currentTimeMillis() }
                val coordinator = BudgetSyncCoordinator(repository, DirectBudgetSyncRemote(sessionClient))
                var summary = sessionClient.withExclusiveSession { coordinator.synchronize(budgetId, password) }
                val banksSynced =
                    syncBanksIfDue(sessionClient, budgetId, password, AppPreferencesStore(database)) {
                        progress.show("Syncing your banks")
                    }
                if (banksSynced) {
                    summary = sessionClient.withExclusiveSession { coordinator.synchronize(budgetId, password) }
                }
                postAlerts(database, repository, budgetId, preferences, summary)
            } finally {
                if (budgetDatabase !== database) budgetDatabase.close()
            }
            BudgetWidget.refresh(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            apiFailureResult(error, apiClient, credentialStore, notificationPreferences)
        } catch (error: BudgetSyncRemoteException) {
            if (syncFailureAlertAllowed(notificationPreferences)) {
                BudgetSystemNotifications.syncFailed(applicationContext)
            }
            error.httpStatus.toWorkResult()
        } catch (_: Exception) {
            if (syncFailureAlertAllowed(notificationPreferences)) {
                BudgetSystemNotifications.syncFailed(applicationContext)
            }
            Result.retry()
        } finally {
            apiClient?.close()
            progress.clear()
            database.close()
        }
    }

    private fun backgroundPassword(budgetId: String): String? =
        BackgroundBudgetKey
            .decode(
                runCatching { AndroidDeviceCredentialStore(applicationContext, BACKGROUND_KEY_STORE).read() }.getOrNull(),
            )?.takeIf { it.first == budgetId }
            ?.second

    private suspend fun postAlerts(
        database: TandemDatabase,
        repository: LocalBudgetRepository,
        budgetId: String,
        preferences: Map<String, String>,
        summary: BudgetSyncSummary,
    ) {
        val policy = NotificationPolicy.read(preferences)
        val minute = LocalTime.now().let { it.hour * 60 + it.minute }
        if (policy.allows(AlertKind.NEEDS_REVIEW, minute)) {
            BudgetSystemNotifications.conflicts(applicationContext, summary.writesConflicted)
        }
        if (policy.allows(AlertKind.REJECTED_EDIT, minute)) {
            BudgetSystemNotifications.rejected(applicationContext, summary.writesRejected)
        }
        if (policy.allows(AlertKind.OVERSPENDING, minute)) {
            BudgetSystemNotifications.overspending(
                applicationContext,
                overspendingAlert(repository, AppPreferencesStore(database), budgetId, YearMonth.now().toString()),
            )
        }
        val store = AppPreferencesStore(database)
        val today = LocalDate.now().toString()
        val format = currencyFormatter(repository, budgetId)
        if (policy.allows(AlertKind.UPCOMING_BILLS, minute)) {
            upcomingBillsAlert(repository, store, budgetId, today, policy, format)?.let {
                BudgetSystemNotifications.post(applicationContext, it)
            }
        }
        if (policy.allows(AlertKind.LARGE_TRANSACTION, minute)) {
            largeTransactionAlert(repository, store, budgetId, today, policy, format)?.let {
                BudgetSystemNotifications.post(applicationContext, it)
            }
        }
        uncategorizedAlert(repository, store, budgetId, today, policy, minute, format)?.let {
            BudgetSystemNotifications.post(applicationContext, it)
        }
    }

    private suspend fun apiFailureResult(
        error: TandemApiException,
        apiClient: TandemApiClient?,
        credentialStore: AndroidDeviceCredentialStore,
        notificationPreferences: Map<String, String>?,
    ): Result =
        when (error.backgroundSyncFailureAction()) {
            BackgroundSyncFailureAction.PAIR_AGAIN -> {
                apiClient?.let { client -> runCatching { client.forgetSession() } }
                runCatching { credentialStore.clear() }
                if (syncFailureAlertAllowed(notificationPreferences)) {
                    BudgetSystemNotifications.signInRequired(applicationContext)
                }
                Result.success()
            }
            BackgroundSyncFailureAction.RETRY -> {
                if (syncFailureAlertAllowed(notificationPreferences)) {
                    BudgetSystemNotifications.syncFailed(applicationContext)
                }
                Result.retry()
            }
            BackgroundSyncFailureAction.FINISH -> {
                if (syncFailureAlertAllowed(notificationPreferences)) {
                    BudgetSystemNotifications.syncFailed(applicationContext)
                }
                Result.success()
            }
        }
}

private suspend fun currencyFormatter(
    repository: LocalBudgetRepository,
    budgetId: String,
): (Long) -> String {
    val settings = repository.readBudgetDisplaySettings(budgetId)
    val format =
        java.text.NumberFormat.getCurrencyInstance().apply {
            settings?.currencyCode?.let { code -> runCatching { currency = java.util.Currency.getInstance(code) } }
        }
    val digits = settings?.currencyFractionDigits ?: 2
    return { minor -> format.format(java.math.BigDecimal.valueOf(minor, digits)) }
}

internal enum class BackgroundSyncFailureAction {
    PAIR_AGAIN,
    RETRY,
    FINISH,
}

internal fun backgroundAlertEnabled(preference: String?): Boolean = preference != "false"

private fun syncFailureAlertAllowed(preferences: Map<String, String>?): Boolean =
    NotificationPolicy.read(preferences.orEmpty()).allows(AlertKind.SYNC_FAILURE, LocalTime.now().let { it.hour * 60 + it.minute })

internal fun TandemApiException.backgroundSyncFailureAction(): BackgroundSyncFailureAction {
    if (errorCode == "invalid_device_credential") return BackgroundSyncFailureAction.PAIR_AGAIN

    val status = httpStatus
    return when {
        status == null ||
            status == 401 ||
            status == 408 ||
            status == 429 ||
            status >= 500 ->
            BackgroundSyncFailureAction.RETRY
        status in 400..499 -> BackgroundSyncFailureAction.FINISH
        else -> BackgroundSyncFailureAction.RETRY
    }
}

private fun Int?.toWorkResult(): ListenableWorker.Result =
    when {
        this == null || this == 401 || this == 408 || this == 429 || this >= 500 -> ListenableWorker.Result.retry()
        this in 400..499 -> ListenableWorker.Result.success()
        else -> ListenableWorker.Result.retry()
    }

/** Runs bank sync on Actual's Automatic syncing schedule even while Tandem is closed; a failing bank doesn't stop the rest. */
internal suspend fun syncBanksIfDue(
    client: TandemApiClient,
    budgetId: String,
    password: String?,
    preferences: AppPreferencesStore,
    onStart: () -> Unit,
): Boolean {
    if (!client.claimDueBankSync(budgetId, password, preferences, System.currentTimeMillis())) return false
    onStart()
    try {
        client.runBudgetBankSync(budgetId, password)
    } catch (_: TandemApiException) {
    }
    return true
}
