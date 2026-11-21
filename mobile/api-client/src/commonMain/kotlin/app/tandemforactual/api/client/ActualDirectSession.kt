package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A signed-in Actual account for direct budget discovery and first-time setup. */
class ActualDirectSession private constructor(
    private val server: ActualServerClient,
) {
    suspend fun listBudgets(): List<BudgetCatalogItem> =
        syncedFiles().mapNotNull { file ->
            file.groupId?.let { BudgetCatalogItem(it, file.name, file.encryptKeyId != null) }
        }

    /**
     * Downloads a budget into new storage and initializes an empty mobile cache. Existing gateway installations use
     * [ActualDirectBudget.stageMobileCache] after comparing snapshots instead.
     */
    suspend fun openNewBudget(
        budgetId: String,
        today: String,
        repository: LocalBudgetRepository,
        now: () -> Long,
        newId: () -> String,
        install: (ByteArray) -> ActualDatabase,
    ): ActualDirectBudget {
        repository.requireFreshDirectCache(budgetId)
        val file = findUnencryptedFile(budgetId)
        val budget = ActualDirectBudget.download(server, file.fileId, now, newId, expectedGroupId = budgetId, install = install)
        return prepareBudget(budget) {
            budget.initializeMobileCache(today, repository)
        }
    }

    /** Reopens the database that owns this cache and its outbox. */
    suspend fun reopenBudget(
        budgetId: String,
        repository: LocalBudgetRepository,
        now: () -> Long,
        newId: () -> String,
        connect: () -> ActualDatabase,
    ): ActualDirectBudget {
        val checkpoint = checkNotNull(repository.readSyncCheckpoint(budgetId)) { "Set up this budget before reopening it." }
        val file = findUnencryptedFile(budgetId)
        val budget = ActualDirectBudget.reopen(connect(), server, file.fileId, budgetId, now, newId)
        return prepareBudget(budget) {
            check(budget.cacheGeneration == checkpoint.generation) { "This local Actual database does not own the mobile cache." }
        }
    }

    private suspend fun prepareBudget(
        budget: ActualDirectBudget,
        prepare: suspend () -> Unit,
    ): ActualDirectBudget =
        try {
            prepare()
            budget
        } catch (error: Throwable) {
            withContext(NonCancellable) { runCatching { budget.close() } }
            throw error
        }

    private suspend fun findUnencryptedFile(budgetId: String): ActualRemoteFile {
        val file = syncedFiles().singleOrNull { it.groupId == budgetId } ?: throw ActualServerException("budget-unavailable")
        if (file.encryptKeyId != null) throw ActualServerException("encrypted-budget")
        return file
    }

    private suspend fun syncedFiles(): List<ActualRemoteFile> = server.listFiles().filter { !it.deleted && !it.groupId.isNullOrBlank() }

    companion object {
        /** The caller owns the HTTP client and configures its platform transport. */
        suspend fun signInWithPassword(
            http: HttpClient,
            serverUrl: String,
            password: String,
            allowInsecurePrivateNetwork: Boolean = false,
        ): ActualDirectSession {
            val url = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)
            if (!url.parameters.isEmpty() || url.trailingQuery || url.fragment.isNotEmpty()) {
                throw TandemApiException("invalid-server-url")
            }
            val server = ActualServerClient(http, url.toString())
            server.signIn(password)
            return ActualDirectSession(server)
        }
    }
}
