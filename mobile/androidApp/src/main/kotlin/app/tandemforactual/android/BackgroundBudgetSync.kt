package app.tandemforactual.android

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import app.tandemforactual.data.store.AlertKind
import app.tandemforactual.data.store.NotificationPolicy
import app.tandemforactual.data.store.largeTransactionAlert
import app.tandemforactual.data.store.upcomingBillsAlert
import app.tandemforactual.data.store.overspendingAlert
import app.tandemforactual.ui.SyncNetwork
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
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.LocalBudgetRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

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
                    )
                    .build(),
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
        return try {
            val preferences = AppPreferencesStore(database).read()
            notificationPreferences = preferences
            if (preferences["backgroundSyncEnabled"] != "true") return Result.success()
            val budgetId = preferences["selectedBudgetId"]?.takeIf { it.isNotBlank() } ?: return Result.success()
            val storedSession = credentialStore.read() ?: return Result.success()
            val sessionClient = TandemApiClient(allowInsecurePrivateNetwork = true, budgetFiles = app.tandemforactual.data.database.AndroidActualBudgetFiles(applicationContext))
            apiClient = sessionClient
            val authenticatedDevice = sessionClient.restoreStoredSession(storedSession)
            if (budgetId !in authenticatedDevice.budgetIds) return Result.success()
            val budget = sessionClient.listBudgets().firstOrNull { it.budgetId == budgetId } ?: return Result.success()
            if (budget.isEncrypted) return Result.success()

            val repository = LocalBudgetRepository(database) { System.currentTimeMillis() }
            val summary = sessionClient.withExclusiveSession {
                BudgetSyncCoordinator(repository, DirectBudgetSyncRemote(sessionClient)).synchronize(budgetId)
            }
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
            BudgetWidget.refresh(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
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
            database.close()
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
