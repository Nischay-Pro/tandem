package app.tandemforactual.ui

import app.tandemforactual.api.client.GeneratedBudgetSyncRemote
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
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

/** Swift's BGTaskScheduler host calls this and completes its task from the callback. */
fun startIosBackgroundBudgetSync(completion: (Boolean) -> Unit): IosBackgroundSyncHandle {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val job =
        scope.launch {
            val succeeded = runSelectedBudgetBackgroundSync()
            runCatching { completion(succeeded) }
            scope.cancel()
        }
    return IosBackgroundSyncHandle(job)
}

private suspend fun runSelectedBudgetBackgroundSync(): Boolean {
    val database =
        try {
            openTandemDatabase(tandemDatabaseBuilder())
        } catch (_: Exception) {
            return false
        }
    val credentialStore = IosDeviceCredentialStore()
    var apiClient: TandemApiClient? = null
    return try {
        val preferences = AppPreferencesStore(database).read()
        if (preferences["backgroundSyncEnabled"] != "true") return true
        val budgetId = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return true
        val storedSession = credentialStore.read() ?: return true
        val sessionClient = TandemApiClient()
        apiClient = sessionClient
        val pairedDevice = sessionClient.restoreStoredSession(storedSession)
        if (budgetId !in pairedDevice.budgetIds) return true
        val budget = sessionClient.listBudgets().firstOrNull { it.budgetId == budgetId } ?: return true
        if (budget.isEncrypted) return true

        val repository = LocalBudgetRepository(database) { Clock.System.now().toEpochMilliseconds() }
        sessionClient.withExclusiveSession {
            BudgetSyncCoordinator(repository, GeneratedBudgetSyncRemote(sessionClient)).synchronize(budgetId)
        }
        true
    } catch (error: CancellationException) {
        false
    } catch (error: TandemApiException) {
        if (error.errorCode == "invalid_device_credential") {
            apiClient?.let { client -> runCatching { client.forgetSession() } }
            runCatching { credentialStore.clear() }
        }
        false
    } catch (_: Exception) {
        false
    } finally {
        database.close()
    }
}
