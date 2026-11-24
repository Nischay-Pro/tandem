package app.tandemforactual.ui.core.platform

import app.tandemforactual.api.client.DirectBudgetSyncRemote
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AlertKind
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.NotificationPolicy
import app.tandemforactual.data.store.largeTransactionAlert
import app.tandemforactual.data.store.overspendingAlert
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.upcomingBillsAlert
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformMinuteOfDay
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    val overspendingMessage: String? = null,
)

/** Swift's BGTaskScheduler host calls this and completes its task from the callback. */
fun startIosBackgroundBudgetSync(completion: (Boolean, Boolean, Int, Int, String?) -> Unit): IosBackgroundSyncHandle {
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
                    alerts.overspendingMessage,
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
    var overspendingAlertsEnabled = false
    return try {
        val preferences = AppPreferencesStore(database).read()
        if (preferences["backgroundSyncEnabled"] != "true") {
            return IosBackgroundSyncAlerts(taskSucceeded = true)
        }
        syncRemindersEnabled = NotificationPolicy.read(preferences).allows(AlertKind.SYNC_FAILURE, platformMinuteOfDay())
        conflictAlertsEnabled = preferences["conflictReviewAlerts"] != "false"
        rejectedAlertsEnabled = preferences["rejectedEditAlerts"] != "false"
        overspendingAlertsEnabled = preferences["overspendingAlerts"] != "false"
        val budgetId =
            preferences["selectedBudgetId"]?.takeIf(String::isNotBlank)
                ?: return IosBackgroundSyncAlerts(taskSucceeded = true)
        val storedSession = credentialStore.read() ?: return IosBackgroundSyncAlerts(taskSucceeded = true)
        val sessionClient =
            TandemApiClient(
                budgetFiles =
                    app.tandemforactual.data.database
                        .IosActualBudgetFiles(),
                today = ::platformTodayIsoDate,
            )
        apiClient = sessionClient
        val authenticatedDevice = sessionClient.restoreStoredSession(storedSession)
        if (budgetId !in authenticatedDevice.budgetIds) return IosBackgroundSyncAlerts(taskSucceeded = true)
        val budget =
            sessionClient.listBudgets().firstOrNull { it.budgetId == budgetId }
                ?: return IosBackgroundSyncAlerts(taskSucceeded = true)
        if (budget.isEncrypted) return IosBackgroundSyncAlerts(taskSucceeded = true)

        val repository = LocalBudgetRepository(database) { Clock.System.now().toEpochMilliseconds() }
        val summary =
            sessionClient.withExclusiveSession {
                BudgetSyncCoordinator(repository, DirectBudgetSyncRemote(sessionClient)).synchronize(budgetId)
            }
        postSyncAlerts(
            database,
            repository,
            preferences,
            budgetId,
            summary,
            overspendingAlertsEnabled,
            conflictAlertsEnabled,
            rejectedAlertsEnabled,
        )
    } catch (error: CancellationException) {
        IosBackgroundSyncAlerts(taskSucceeded = false)
    } catch (_: IosCredentialUnavailableBeforeFirstUnlockException) {
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

private suspend fun postSyncAlerts(
    database: TandemDatabase,
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    budgetId: String,
    summary: BudgetSyncSummary,
    overspendingAlertsEnabled: Boolean,
    conflictAlertsEnabled: Boolean,
    rejectedAlertsEnabled: Boolean,
): IosBackgroundSyncAlerts {
    val overspending =
        if (overspendingAlertsEnabled) {
            overspendingAlert(repository, AppPreferencesStore(database), budgetId, platformTodayIsoDate().take(7))
        } else {
            null
        }
    val policy = NotificationPolicy.read(preferences)
    val minute = platformMinuteOfDay()
    val today = platformTodayIsoDate()
    val settings = repository.readBudgetDisplaySettings(budgetId)
    val format = { minor: Long -> formatCurrency(minor, settings) }
    val store = AppPreferencesStore(database)
    if (policy.allows(AlertKind.UPCOMING_BILLS, minute)) {
        upcomingBillsAlert(repository, store, budgetId, today, policy, format)?.let(::postIosLocalAlert)
    }
    if (policy.allows(AlertKind.LARGE_TRANSACTION, minute)) {
        largeTransactionAlert(repository, store, budgetId, today, policy, format)?.let(::postIosLocalAlert)
    }
    return if (policy.quietHours?.contains(minute) == true) {
        IosBackgroundSyncAlerts(taskSucceeded = true)
    } else {
        summary.toAlerts(conflictAlertsEnabled, rejectedAlertsEnabled).copy(overspendingMessage = overspending)
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
