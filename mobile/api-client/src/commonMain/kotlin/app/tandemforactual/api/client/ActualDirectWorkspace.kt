package app.tandemforactual.api.client

import app.tandemforactual.data.database.DirectBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns a direct Actual connection and its paired mobile cache until the app closes the workspace. */
class ActualDirectWorkspace private constructor(
    val installationId: String,
    private val budget: ActualDirectBudget,
    val repository: LocalBudgetRepository,
    private val cache: TandemDatabase,
) {
    val budgetId: String = budget.groupId
    private val coordinator = ActualDirectSyncCoordinator(budget, repository)
    private val lifecycle = Mutex()
    private var closed = false

    suspend fun synchronize(
        today: String,
        sendQueuedWrites: Boolean,
    ) = lifecycle.withLock {
        check(!closed) { "The direct budget workspace is closed." }
        coordinator.synchronize(today, sendQueuedWrites)
    }

    suspend fun close() {
        withContext(NonCancellable) {
            lifecycle.withLock {
                if (closed) return@withLock
                closed = true
                try {
                    budget.close()
                } finally {
                    cache.closeDatabase()
                }
            }
        }
    }

    companion object {
        suspend fun create(
            session: ActualDirectSession,
            files: DirectBudgetFiles,
            budgetId: String,
            today: String,
            now: () -> Long,
            newId: () -> String,
        ): ActualDirectWorkspace =
            open(files, now) { repository ->
                session.openNewBudget(budgetId, today, repository, now, newId, files::installActual)
            }

        suspend fun reopen(
            session: ActualDirectSession,
            files: DirectBudgetFiles,
            budgetId: String,
            now: () -> Long,
            newId: () -> String,
        ): ActualDirectWorkspace =
            open(files, now) { repository ->
                session.reopenBudget(budgetId, repository, now, newId, files::openActual)
            }

        private suspend fun open(
            files: DirectBudgetFiles,
            now: () -> Long,
            openBudget: suspend (LocalBudgetRepository) -> ActualDirectBudget,
        ): ActualDirectWorkspace {
            val cache = files.openMobileCache()
            try {
                val repository = LocalBudgetRepository(cache, now)
                val budget = openBudget(repository)
                return ActualDirectWorkspace(files.installationId, budget, repository, cache)
            } catch (error: Throwable) {
                runCatching { cache.closeDatabase() }
                throw error
            }
        }
    }
}
