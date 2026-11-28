package app.tandemforactual.ui.core.platform

import app.tandemforactual.api.client.DirectBudgetSyncRemote
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.api.client.claimDueBankSync
import app.tandemforactual.api.client.runBudgetBankSync
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AlertKind
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalAlert
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.NotificationPolicy
import app.tandemforactual.data.store.forBudget
import app.tandemforactual.data.store.largeTransactionAlert
import app.tandemforactual.data.store.overspendingAlert
import app.tandemforactual.data.store.overspendingForBudget
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.upcomingBillsAlert
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformMinuteOfDay
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.CardDueReminders
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

/**
 * Swift's BGTaskScheduler host calls this and completes its task from the callback; [afterAutomation] syncs once even
 * with scheduled sync off, after Shortcuts or Siri added a transaction.
 */
fun startIosBackgroundBudgetSync(
    afterAutomation: Boolean,
    completion: (Boolean, Boolean, Int, Int, String?) -> Unit,
): IosBackgroundSyncHandle {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val job =
        scope.launch {
            val alerts = runSelectedBudgetBackgroundSync(afterAutomation)
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

private suspend fun runSelectedBudgetBackgroundSync(afterAutomation: Boolean): IosBackgroundSyncAlerts {
    val database =
        try {
            iosTandemDatabase
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
        if (preferences["backgroundSyncEnabled"] != "true" && !afterAutomation) {
            return IosBackgroundSyncAlerts(taskSucceeded = true)
        }
        val policy = NotificationPolicy.read(preferences)
        syncRemindersEnabled = policy.allows(AlertKind.SYNC_FAILURE, platformMinuteOfDay())
        conflictAlertsEnabled = AlertKind.NEEDS_REVIEW in policy.enabled
        rejectedAlertsEnabled = AlertKind.REJECTED_EDIT in policy.enabled
        overspendingAlertsEnabled = AlertKind.OVERSPENDING in policy.enabled
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
        val password = if (budget.isEncrypted) iosReadablePassword(budgetId) ?: return skipped() else null
        val budgetDatabase =
            if (password == null) {
                database
            } else {
                IosEncryptedBudgetVault().open(budgetId, password) ?: return forgetReadablePassword()
            }
        try {
            val repository = LocalBudgetRepository(budgetDatabase) { Clock.System.now().toEpochMilliseconds() }
            val summary = syncWithBanks(sessionClient, database, repository, budgetId, password)
            val alerts = SyncAlertChoices(overspendingAlertsEnabled, conflictAlertsEnabled, rejectedAlertsEnabled, password != null)
            postSyncAlerts(database, repository, preferences, budgetId, summary, alerts)
        } finally {
            if (budgetDatabase !== database) budgetDatabase.close()
        }
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
        apiClient?.close()
    }
}

private fun skipped() = IosBackgroundSyncAlerts(taskSucceeded = true)

/** The kept password no longer opens the budget, so its password changed; reads wait until it's allowed again. */
private fun forgetReadablePassword(): IosBackgroundSyncAlerts {
    runCatching { iosBackgroundBudgetKeyStore().clear() }
    return IosBackgroundSyncAlerts(taskSucceeded = true)
}

private suspend fun syncWithBanks(
    client: TandemApiClient,
    database: TandemDatabase,
    repository: LocalBudgetRepository,
    budgetId: String,
    password: String?,
): BudgetSyncSummary {
    val coordinator = BudgetSyncCoordinator(repository, DirectBudgetSyncRemote(client))
    val summary = client.withExclusiveSession { coordinator.synchronize(budgetId, password) }
    if (!client.claimDueBankSync(budgetId, password, AppPreferencesStore(database), Clock.System.now().toEpochMilliseconds())) {
        return summary
    }
    try {
        client.runBudgetBankSync(budgetId, password)
    } catch (_: TandemApiException) {
    }
    return client.withExclusiveSession { coordinator.synchronize(budgetId, password) }
}

private class SyncAlertChoices(
    val overspending: Boolean,
    val conflicts: Boolean,
    val rejected: Boolean,
    val encrypted: Boolean,
)

private suspend fun postSyncAlerts(
    database: TandemDatabase,
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    budgetId: String,
    summary: BudgetSyncSummary,
    choices: SyncAlertChoices,
): IosBackgroundSyncAlerts {
    val policy = NotificationPolicy.read(preferences)
    val overspending =
        if (choices.overspending) {
            val message = overspendingAlert(repository, AppPreferencesStore(database), budgetId, platformTodayIsoDate().take(7))
            policy.overspendingForBudget(message, choices.encrypted)
        } else {
            null
        }
    val minute = platformMinuteOfDay()
    val today = platformTodayIsoDate()
    val settings = repository.readBudgetDisplaySettings(budgetId)
    val format = { minor: Long -> formatCurrency(minor, settings) }
    val store = AppPreferencesStore(database)
    val post = { alert: LocalAlert -> postIosLocalAlert(policy.forBudget(alert, choices.encrypted)) }
    if (policy.allows(AlertKind.UPCOMING_BILLS, minute)) {
        upcomingBillsAlert(repository, store, budgetId, today, policy, format)?.let(post)
    }
    if (policy.allows(AlertKind.CARD_DUE, minute)) {
        val keys = IosDeviceCredentialStore(SecurityIosKeychainClient("planning-keys"))
        CardDueReminders.alert(database, keys, repository, budgetId, today, policy, format)?.let(post)
    }
    if (policy.allows(AlertKind.LARGE_TRANSACTION, minute)) {
        largeTransactionAlert(repository, store, budgetId, today, policy, format)?.let(post)
    }
    return if (policy.quietHours?.contains(minute) == true) {
        IosBackgroundSyncAlerts(taskSucceeded = true)
    } else {
        summary.toAlerts(choices.conflicts, choices.rejected).copy(overspendingMessage = overspending)
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
