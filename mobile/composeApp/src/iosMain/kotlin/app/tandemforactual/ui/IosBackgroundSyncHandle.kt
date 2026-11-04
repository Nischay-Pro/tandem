package app.tandemforactual.ui

import app.tandemforactual.api.client.GeneratedBudgetSyncRemote
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Runs the selected cached budget's regular sync coordinator for an iOS background task. */
class IosBackgroundSyncHandle internal constructor(
    private val job: Job,
) {
    fun cancel() {
        job.cancel()
    }
}

/** Counts and preference-filtered alert decisions returned to the native notification center. */
private data class IosBackgroundSyncAlerts(
    val taskSucceeded: Boolean,
    val notifySyncFailure: Boolean = false,
    val conflictCount: Int = 0,
    val rejectedCount: Int = 0,
)

/** Swift's BGTaskScheduler host calls this and completes its task from the callback. */
fun startIosBackgroundBudgetSync(completion: (Boolean, Boolean, Int, Int) -> Unit): IosBackgroundSyncHandle {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val job =
        scope.launch {
            val alerts = runSelectedBudgetBackgroundSync()
            runCatching {
                completion(
                    alerts.taskSucceeded,
                    alerts.notifySyncFailure,
                    alerts.conflictCount,
                    alerts.rejectedCount,
                )
            }
            scope.cancel()
        }
    return IosBackgroundSyncHandle(job)
}

private suspend fun runSelectedBudgetBackgroundSync(): IosBackgroundSyncAlerts {
    val database =
        try {
            openTandemDatabase(tandemDatabaseBuilder())
        } catch (_: Exception) {
            return IosBackgroundSyncAlerts(taskSucceeded = false)
        }
    val credentialStore = IosDeviceCredentialStore()
    var apiClient: TandemApiClient? = null
    var syncRemindersEnabled = false
    var conflictAlertsEnabled = false
    var rejectedAlertsEnabled = false
    return try {
        val preferences = AppPreferencesStore(database).read()
        if (preferences["backgroundSyncEnabled"] != "true") {
            return IosBackgroundSyncAlerts(taskSucceeded = true)
        }
        syncRemindersEnabled = preferences["syncReminders"] != "false"
        conflictAlertsEnabled = preferences["conflictReviewAlerts"] != "false"
        rejectedAlertsEnabled = preferences["rejectedEditAlerts"] != "false"
        val budgetId =
            preferences["selectedBudgetId"]?.takeIf(String::isNotBlank)
                ?: return IosBackgroundSyncAlerts(taskSucceeded = true)
        val storedSession = credentialStore.read() ?: return IosBackgroundSyncAlerts(taskSucceeded = true)
        val sessionClient = TandemApiClient()
        apiClient = sessionClient
        val pairedDevice = sessionClient.restoreStoredSession(storedSession)
        if (budgetId !in pairedDevice.budgetIds) return IosBackgroundSyncAlerts(taskSucceeded = true)
        val budget =
            sessionClient.listBudgets().firstOrNull { it.budgetId == budgetId }
                ?: return IosBackgroundSyncAlerts(taskSucceeded = true)
        if (budget.isEncrypted) return IosBackgroundSyncAlerts(taskSucceeded = true)

        val repository = LocalBudgetRepository(database) { Clock.System.now().toEpochMilliseconds() }
        val summary =
            sessionClient.withExclusiveSession {
                BudgetSyncCoordinator(repository, GeneratedBudgetSyncRemote(sessionClient)).synchronize(budgetId)
            }
        summary.toAlerts(conflictAlertsEnabled, rejectedAlertsEnabled)
    } catch (error: CancellationException) {
        IosBackgroundSyncAlerts(taskSucceeded = false)
    } catch (error: TandemApiException) {
        if (error.errorCode == "invalid_device_credential") {
            apiClient?.let { client -> runCatching { client.forgetSession() } }
            runCatching { credentialStore.clear() }
        }
        IosBackgroundSyncAlerts(taskSucceeded = false, notifySyncFailure = syncRemindersEnabled)
    } catch (_: Exception) {
        IosBackgroundSyncAlerts(taskSucceeded = false, notifySyncFailure = syncRemindersEnabled)
    } finally {
        database.close()
    }
}

private fun BudgetSyncSummary.toAlerts(
    conflictAlertsEnabled: Boolean,
    rejectedAlertsEnabled: Boolean,
): IosBackgroundSyncAlerts =
    IosBackgroundSyncAlerts(
        taskSucceeded = true,
        conflictCount = if (conflictAlertsEnabled) writesConflicted else 0,
        rejectedCount = if (rejectedAlertsEnabled) writesRejected else 0,
    )
