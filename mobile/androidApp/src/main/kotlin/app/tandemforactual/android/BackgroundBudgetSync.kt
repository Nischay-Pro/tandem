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
import app.tandemforactual.api.client.GeneratedBudgetSyncRemote
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
        unmeteredOnly: Boolean,
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
            periodicRequest(unmeteredOnly),
        )
    }

    internal fun periodicRequest(unmeteredOnly: Boolean): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<BackgroundBudgetSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
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
            val sessionClient = TandemApiClient(allowInsecurePrivateNetwork = true)
            apiClient = sessionClient
            val pairedDevice = sessionClient.restoreStoredSession(storedSession)
            if (budgetId !in pairedDevice.budgetIds) return Result.success()
            val budget = sessionClient.listBudgets().firstOrNull { it.budgetId == budgetId } ?: return Result.success()
            if (budget.isEncrypted) return Result.success()

            val repository = LocalBudgetRepository(database) { System.currentTimeMillis() }
            val summary = sessionClient.withExclusiveSession {
                BudgetSyncCoordinator(repository, GeneratedBudgetSyncRemote(sessionClient)).synchronize(budgetId)
            }
            if (backgroundAlertEnabled(preferences["conflictReviewAlerts"])) {
                BudgetSystemNotifications.conflicts(applicationContext, summary.writesConflicted)
            }
            if (backgroundAlertEnabled(preferences["rejectedEditAlerts"])) {
                BudgetSystemNotifications.rejected(applicationContext, summary.writesRejected)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            when (error.backgroundSyncFailureAction()) {
                BackgroundSyncFailureAction.PAIR_AGAIN -> {
                    apiClient?.let { client -> runCatching { client.forgetSession() } }
                    runCatching { credentialStore.clear() }
                    if (backgroundAlertEnabled(notificationPreferences?.get("syncReminders"))) {
                        BudgetSystemNotifications.pairingRequired(applicationContext)
                    }
                    Result.success()
                }
                BackgroundSyncFailureAction.RETRY -> {
                    if (backgroundAlertEnabled(notificationPreferences?.get("syncReminders"))) {
                        BudgetSystemNotifications.syncFailed(applicationContext)
                    }
                    Result.retry()
                }
                BackgroundSyncFailureAction.FINISH -> {
                    if (backgroundAlertEnabled(notificationPreferences?.get("syncReminders"))) {
                        BudgetSystemNotifications.syncFailed(applicationContext)
                    }
                    Result.success()
                }
            }
        } catch (error: BudgetSyncRemoteException) {
            if (backgroundAlertEnabled(notificationPreferences?.get("syncReminders"))) {
                BudgetSystemNotifications.syncFailed(applicationContext)
            }
            error.httpStatus.toWorkResult()
        } catch (_: Exception) {
            if (backgroundAlertEnabled(notificationPreferences?.get("syncReminders"))) {
                BudgetSystemNotifications.syncFailed(applicationContext)
            }
            Result.retry()
        } finally {
            database.close()
        }
    }
}

internal enum class BackgroundSyncFailureAction {
    PAIR_AGAIN,
    RETRY,
    FINISH,
}

internal fun backgroundAlertEnabled(preference: String?): Boolean = preference != "false"

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
